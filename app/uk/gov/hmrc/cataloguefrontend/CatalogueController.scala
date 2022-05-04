/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cataloguefrontend

import akka.actor.ActorSystem
import cats.data.OptionT
import cats.implicits._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc._
import play.api.{Configuration, Logger}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.connector.model.{RepositoryModules, Version}
import uk.gov.hmrc.cataloguefrontend.leakdetection.LeakDetectionService
import uk.gov.hmrc.cataloguefrontend.model.{Environment, SlugInfoFlag}
import uk.gov.hmrc.cataloguefrontend.service.ConfigService.ArtifactNameResult.{ArtifactNameError, ArtifactNameFound, ArtifactNameNotFound}
import uk.gov.hmrc.cataloguefrontend.service.{ConfigService, CostEstimateConfig, CostEstimationService, DefaultBranchesService, RouteRulesService}
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterService, ShutterState, ShutterType}
import uk.gov.hmrc.cataloguefrontend.util.{MarkdownLoader, TelemetryLinks}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereService
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html._

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

case class EnvData(
  version          : Version,
  repoModules      : Option[RepositoryModules],
  optShutterState  : Option[ShutterState],
  optTelemetryLinks: Option[Seq[Link]]
)

@Singleton
class CatalogueController @Inject() (
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  configService                : ConfigService,
  costEstimationService        : CostEstimationService,
  serviceCostEstimateConfig    : CostEstimateConfig,
  routeRulesService            : RouteRulesService,
  serviceDependenciesConnector : ServiceDependenciesConnector,
  leakDetectionService         : LeakDetectionService,
  shutterService               : ShutterService,
  defaultBranchesService       : DefaultBranchesService,
  userManagementPortalConfig   : UserManagementPortalConfig,
  configuration                : Configuration,
  override val mcc             : MessagesControllerComponents,
  whatsRunningWhereService     : WhatsRunningWhereService,
  indexPage                    : IndexPage,
  serviceInfoPage              : ServiceInfoPage,
  serviceConfigPage            : ServiceConfigPage,
  serviceConfigRawPage         : ServiceConfigRawPage,
  libraryInfoPage              : LibraryInfoPage,
  prototypeInfoPage            : PrototypeInfoPage,
  repositoryInfoPage           : RepositoryInfoPage,
  repositoriesListPage         : RepositoriesListPage,
  defaultBranchListPage        : DefaultBranchListPage,
  outOfDateTeamDependenciesPage: OutOfDateTeamDependenciesPage,
  costEstimationPage           : CostEstimationPage,
  override val auth            : FrontendAuthComponents,
  actorSystem                  : ActorSystem
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  private lazy val whatsNewDisplayLines  = configuration.get[Int]("whats-new.display.lines")
  private lazy val blogPostsDisplayLines = configuration.get[Int]("blog-posts.display.lines")

  private lazy val telemetryLogsLinkTemplate = configuration.get[String]("telemetry.templates.logs")
  private lazy val telemetryMetricsLinkTemplate = configuration.get[String]("telemetry.templates.metrics")

  private val logger = Logger(getClass)

  private def notFound(implicit request: Request[_], messages: Messages) = NotFound(error_404_template())

  def index(): Action[AnyContent] =
    BasicAuthAction { implicit request =>
      val whatsNew  = MarkdownLoader.markdownFromFile("VERSION_HISTORY.md", whatsNewDisplayLines).merge
      val blogPosts = MarkdownLoader.markdownFromFile("BLOG_POSTS.md", blogPostsDisplayLines).merge
      Ok(indexPage(whatsNew, blogPosts))
    }

  def serviceConfig(serviceName: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        configByKey <- configService.configByKey(serviceName)
      } yield Ok(serviceConfigPage(serviceName, configByKey))
    }

  def serviceConfigRaw(serviceName: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        configByEnvironment <- configService.configByEnvironment(serviceName)
      } yield Ok(serviceConfigRawPage(serviceName, configByEnvironment))
    }

  /** Renders the service page by either the repository name, or the artefact name (if configured).
    * This is where it differs from accessing through the generic `/repositories/name` endpoint, which only
    * considers the name of the repository.
    */
  def service(serviceName: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      def buildServicePageFromItsArtifactName(serviceName: String): Future[Result] =
        configService.findArtifactName(serviceName).flatMap {
          case ArtifactNameFound(artifactName) => buildServicePageFromRepoName(artifactName).getOrElse(notFound)
          case ArtifactNameNotFound            => Future.successful(notFound)
          case ArtifactNameError(error)        => logger.error(error)
                                                  Future.successful(InternalServerError)
        }

      def buildServicePageFromRepoName(repoName: String): OptionT[Future, Result] =
        OptionT(teamsAndRepositoriesConnector.repositoryDetails(repoName))
          .semiflatMap {
            case repositoryDetails if repositoryDetails.repoType == RepoType.Service => renderServicePage(serviceName, repositoryDetails)
            case _ => Future.successful(notFound)
          }

          enableBranchProtection *>
            buildServicePageFromRepoName(serviceName).getOrElseF(buildServicePageFromItsArtifactName(serviceName))
    }

  private def renderServicePage(
    serviceName      : String,
    repositoryDetails: GitRepository
  )(implicit
    messages: Messages,
    request : Request[_]
  ): Future[Result] = {
    val repositoryName = repositoryDetails.name
    val futEnvDatas: Future[Map[SlugInfoFlag, EnvData]] =
      for {
        deployments <- whatsRunningWhereService.releasesForService(serviceName).map(_.versions)
        res <- Environment.values.traverse { env =>
                 val slugInfoFlag     = SlugInfoFlag.ForEnvironment(env)
                 val deployedVersions = deployments.filter(_.environment == env).map(_.versionNumber.asVersion)
                 // a single environment may have multiple versions during a deployment
                 // return the lowest
                 deployedVersions.sorted.headOption match {
                   case Some(version) =>
                     (
                       serviceDependenciesConnector.getRepositoryModules(repositoryName, version),
                       shutterService.getShutterState(ShutterType.Frontend, env, serviceName)
                     ).mapN { (repoModules, optShutterState) =>
                       Some(
                         slugInfoFlag ->
                           EnvData(
                             version           = version,
                             repoModules       = repoModules,
                             optShutterState   = optShutterState,
                             optTelemetryLinks = Some(Seq(
                               TelemetryLinks.create("Grafana", telemetryMetricsLinkTemplate, env, serviceName),
                               TelemetryLinks.create("Kibana", telemetryLogsLinkTemplate, env, serviceName),
                             ))
                           )
                       )
                     }
                   case None => Future.successful(None)
                 }
               }
      } yield res.collect { case Some(v) => v }.toMap

    val costEstimationEnvironments = Environment.values
    (
      teamsAndRepositoriesConnector.lookupLink(repositoryName),
      futEnvDatas,
      serviceDependenciesConnector.getRepositoryModules(repositoryName),
      leakDetectionService.urlIfLeaksFound(repositoryName),
      routeRulesService.serviceUrl(serviceName),
      routeRulesService.serviceRoutes(serviceName),
      serviceDependenciesConnector.getSlugInfo(repositoryName),
      costEstimationService.estimateServiceCost(repositoryName, costEstimationEnvironments, serviceCostEstimateConfig)
    ).mapN { (jenkinsLink,
              envDatas,
              latestRepoModules,
              urlIfLeaksFound,
              serviceUrl,
              serviceRoutes,
              optLatestServiceInfo,
              costEstimate
             ) =>
      val optLatestData: Option[(SlugInfoFlag, EnvData)] =
        optLatestServiceInfo.map { latestServiceInfo =>
          SlugInfoFlag.Latest ->
            EnvData(
              version           = latestServiceInfo.version,
              repoModules       = latestRepoModules,
              optShutterState   = None,
              optTelemetryLinks = None
            )
        }

      Ok(
        serviceInfoPage(
          serviceName                 = serviceName,
          repositoryDetails           = repositoryDetails.copy(jenkinsURL = jenkinsLink.map(_.url)),
          costEstimate                = costEstimate,
          costEstimateConfig          = serviceCostEstimateConfig,
          repositoryCreationDate      = repositoryDetails.createdDate,
          envDatas                    = optLatestData.fold(envDatas)(envDatas + _),
          linkToLeakDetection         = urlIfLeaksFound,
          productionEnvironmentRoute  = serviceUrl,
          serviceRoutes               = serviceRoutes
        )
      )
    }
  }

  def library(name: String): Action[AnyContent] =
    Action(Redirect(routes.CatalogueController.repository(name)))

  def prototype(name: String): Action[AnyContent] =
    Action(Redirect(routes.CatalogueController.repository(name)))

  def repository(name: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>

      enableBranchProtection *>
        OptionT(teamsAndRepositoriesConnector.repositoryDetails(name))
          .foldF(Future.successful(notFound))(repoDetails =>
            repoDetails.repoType match {
              case RepoType.Service   => renderServicePage(
                                           serviceName       = repoDetails.name,
                                           repositoryDetails = repoDetails
                                         )
              case RepoType.Library   => renderLibrary(repoDetails)
              case RepoType.Prototype => renderPrototype(repoDetails)
              case RepoType.Other     => renderOther(repoDetails)
            }
          )
    }

  private def enableBranchProtection(implicit request: Request[_]): Future[Unit] =
    SetBranchProtection
      .form
      .bindFromRequest()
      .value
      .traverse_(sbp => teamsAndRepositoriesConnector.setBranchProtection(sbp.repoName)) *>
        akka.pattern.after(2.seconds, actorSystem.scheduler)(Future.unit)

  def renderLibrary(repoDetails: GitRepository)(implicit messages: Messages, request: Request[_]): Future[Result] =
    ( teamsAndRepositoriesConnector.lookupLink(repoDetails.name),
      serviceDependenciesConnector.getRepositoryModules(repoDetails.name),
      leakDetectionService.urlIfLeaksFound(repoDetails.name)
    ).mapN { ( jenkinsLink,
               repoModules,
               urlIfLeaksFound
             ) =>
      Ok(
        libraryInfoPage(
          repoDetails.copy(jenkinsURL = jenkinsLink.map(_.url)),
          repoModules,
          urlIfLeaksFound
        )
      )
    }

  private def renderPrototype(repoDetails: GitRepository)(implicit messages: Messages, request: Request[_]): Future[Result] =
    for {
      urlIfLeaksFound <- leakDetectionService.urlIfLeaksFound(repoDetails.name)
    } yield
      Ok(prototypeInfoPage(
        repoDetails,
        urlIfLeaksFound
      ))

  private def renderOther(repoDetails: GitRepository)(implicit messages: Messages, request: Request[_]): Future[Result] =
    ( teamsAndRepositoriesConnector.lookupLink(repoDetails.name),
      serviceDependenciesConnector.getRepositoryModules(repoDetails.name),
      leakDetectionService.urlIfLeaksFound(repoDetails.name)
    ).mapN { ( jenkinsLink,
               repoModules,
               urlIfLeaksFound
             ) =>
      Ok(
        repositoryInfoPage(
          repoDetails.copy(
            teamNames  = { val (owners, writers) =  repoDetails.teamNames.partition(repoDetails.owningTeams.contains) // TODO should this apply to renderLibrary too?
                           owners.sorted ++ writers.sorted
                         },
              jenkinsURL = jenkinsLink.map(_.url)
          ),
          repoModules,
          urlIfLeaksFound
        )
      )
    }

  def allServices: Action[AnyContent] =
    Action {
      Redirect(routes.CatalogueController.allRepositories(repoType = Some(RepoType.Service.asString)))
    }

  def allLibraries: Action[AnyContent] =
    Action {
      Redirect(routes.CatalogueController.allRepositories(repoType = Some(RepoType.Library.asString)))
    }

  def allPrototypes: Action[AnyContent] =
    Action {
      Redirect(routes.CatalogueController.allRepositories(repoType = Some(RepoType.Prototype.asString)))
    }

  def allRepositories(repoType: Option[String]): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      import SearchFiltering._

      val allTeams =
        teamsAndRepositoriesConnector
          .allTeams
          .map(_.sortBy(_.name.asString))

      val allRepositories =
        teamsAndRepositoriesConnector
          .allRepositories
          .map(_.sortBy(_.name.toLowerCase))

      val form =
        RepoListFilter.form.bindFromRequest()

      for {
        teams        <- allTeams
        repositories <- allRepositories
      } yield form.fold(
        formWithErrors => Ok(repositoriesListPage(repositories = Seq.empty, teams = teams, formWithErrors)),
        query => Ok(repositoriesListPage(repositories = repositories.filter(query), teams = teams, form))
      )
    }

  def dependencyRepository(group: String, artefact: String, version: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      serviceDependenciesConnector.getRepositoryName(group, artefact, Version(version))
        .map { repoName =>
          Redirect(routes.CatalogueController.repository(repoName.getOrElse(artefact)).copy(fragment = artefact))
        }
    }

  def allDefaultBranches(singleOwnership: Boolean, includeArchived: Boolean): Action[AnyContent] = {
    BasicAuthAction.async { implicit request =>
      teamsAndRepositoriesConnector.allRepositories.map { repositories =>
        DefaultBranchesFilter.form
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(defaultBranchListPage(
              repositories      = Seq(),
              teams             = Seq(""),
              singleOwnership   = false,
              includeArchived   = false,
              formWithErrors)),
            query =>
              Ok(
                defaultBranchListPage(
                  repositories = defaultBranchesService.filterRepositories(repositories, query.name, query.defaultBranch, query.teamNames, singleOwnership, includeArchived),
                  teams = defaultBranchesService.allTeams(repositories),
                  singleOwnership = singleOwnership,
                  includeArchived = includeArchived,
                  DefaultBranchesFilter.form.bindFromRequest()
                )
              )
          )
      }
    }
  }

  def costEstimation(serviceName: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      (for {
        repositoryDetails <- OptionT(teamsAndRepositoriesConnector.repositoryDetails(serviceName))
        if repositoryDetails.repoType == RepoType.Service
        costEstimationEnvironments = Environment.values
        costEstimation <- OptionT.liftF(costEstimationService.estimateServiceCost(serviceName, costEstimationEnvironments, serviceCostEstimateConfig))
        estimatedCostCharts <- OptionT.liftF(costEstimationService.historicResourceUsageChartsForService(serviceName, serviceCostEstimateConfig))
      } yield
        Ok(costEstimationPage(
          serviceName,
          repositoryDetails,
          costEstimation,
          serviceCostEstimateConfig,
          estimatedCostCharts
        ))
        ).getOrElse(notFound)
    }
}

case class TeamFilter(
  name: Option[String] = None
) {
  def isEmpty: Boolean = name.isEmpty
}

object TeamFilter {
  lazy val form = Form(
    mapping(
      "name" -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity)
    )(TeamFilter.apply)(TeamFilter.unapply)
  )
}

case class DigitalServiceNameFilter(value: Option[String] = None) {
  def isEmpty: Boolean = value.isEmpty
}

object DigitalServiceNameFilter {
  lazy val form = Form(
    mapping(
      "name" -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity)
    )(DigitalServiceNameFilter.apply)(DigitalServiceNameFilter.unapply)
  )
}

case class RepoListFilter(
  name    : Option[String] = None,
  team    : Option[String] = None,
  repoType: Option[String] = None
) {
  def isEmpty: Boolean =
    name.isEmpty && team.isEmpty && repoType.isEmpty
}

object RepoListFilter {
  lazy val form = Form(
    mapping(
      "name"     -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
      "team"     -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
      "repoType" -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity)
    )(RepoListFilter.apply)(RepoListFilter.unapply)
  )
}

case class SetBranchProtection(repoName: String)

object SetBranchProtection {
  lazy val form: Form[SetBranchProtection] =
    Form(mapping("repoName" -> text)(SetBranchProtection.apply)(SetBranchProtection.unapply))
}

case class DefaultBranchesFilter(
   name           : Option[String] = None,
   teamNames      : Option[String] = None,
   defaultBranch  : Option[String] = None
 ) {
  def isEmpty: Boolean = name.isEmpty && teamNames.isEmpty && defaultBranch.isEmpty
}

object DefaultBranchesFilter {
  lazy val form: Form[DefaultBranchesFilter] = Form(
    mapping(
      "name"          -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
      "teamNames"     -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
      "defaultBranch" -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity)
    )(DefaultBranchesFilter.apply)(DefaultBranchesFilter.unapply)
  )
}
