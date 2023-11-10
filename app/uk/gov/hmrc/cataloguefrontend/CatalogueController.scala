/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.data.{EitherT, OptionT}
import cats.implicits._
import play.api.data.{Form, Forms}
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.api.Logger
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.auth.{AuthController, CatalogueAuthBuilders}
import uk.gov.hmrc.cataloguefrontend.config.CatalogueConfig
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.PrototypeStatus
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.connector.model.{RepositoryModules, Version}
import uk.gov.hmrc.cataloguefrontend.leakdetection.LeakDetectionService
import uk.gov.hmrc.cataloguefrontend.model.{Environment, SlugInfoFlag}
import uk.gov.hmrc.cataloguefrontend.prcommenter.PrCommenterConnector
import uk.gov.hmrc.cataloguefrontend.service.{CostEstimateConfig, CostEstimationService, DefaultBranchesService, RouteRulesService}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsService
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterService, ShutterState, ShutterType}
import uk.gov.hmrc.cataloguefrontend.util.TelemetryLinks
import uk.gov.hmrc.cataloguefrontend.vulnerabilities.VulnerabilitiesConnector
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, IAAction, Predicate, Resource, Retrieval}
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class EnvData(
  version                : Version,
  repoModules            : Option[RepositoryModules],
  optShutterState        : Option[ShutterState],
  telemetryLinks         : Seq[Link],
  nonPerformantQueryLinks: Seq[Link],
)

@Singleton
class CatalogueController @Inject() (
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  serviceConfigsService        : ServiceConfigsService,
  costEstimationService        : CostEstimationService,
  serviceCostEstimateConfig    : CostEstimateConfig,
  routeRulesService            : RouteRulesService,
  serviceDependenciesConnector : ServiceDependenciesConnector,
  leakDetectionService         : LeakDetectionService,
  shutterService               : ShutterService,
  defaultBranchesService       : DefaultBranchesService,
  override val mcc             : MessagesControllerComponents,
  whatsRunningWhereService     : WhatsRunningWhereService,
  prCommenterConnector         : PrCommenterConnector,
  vulnerabilitiesConnector     : VulnerabilitiesConnector,
  confluenceConnector          : ConfluenceConnector,
  buildDeployApiConnector      : BuildDeployApiConnector,
  telemetryLinks               : TelemetryLinks,
  indexPage                    : IndexPage,
  serviceInfoPage              : ServiceInfoPage,
  libraryInfoPage              : LibraryInfoPage,
  prototypeInfoPage            : PrototypeInfoPage,
  repositoryInfoPage           : RepositoryInfoPage,
  defaultBranchListPage        : DefaultBranchListPage,
  costEstimationPage           : CostEstimationPage,
  serviceMetricsConnector      : ServiceMetricsConnector,
  override val auth            : FrontendAuthComponents,
  config                       : CatalogueConfig,
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders
     with I18nSupport {

  private val logger = Logger(getClass)

  private def notFound(implicit request: Request[_]) = NotFound(error_404_template())

  def index(): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      confluenceConnector
        .getBlogs()
        .map(blogs => Ok(indexPage(blogs)))
    }

  /** Renders the service page by either the repository name, or the artefact name (if configured).
    * This is where it differs from accessing through the generic `/repositories/name` endpoint, which only
    * considers the name of the repository.
    */
  def service(serviceName: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      def buildServicePageFromItsArtifactName(serviceName: String, hasBranchProtectionAuth: EnableBranchProtection.HasAuthorisation): Future[Result] =
        serviceConfigsService.findArtifactName(serviceName).flatMap {
          case ServiceConfigsService.ArtifactNameResult.ArtifactNameFound(artifactName) => buildServicePageFromRepoName(artifactName, hasBranchProtectionAuth).getOrElse(notFound)
          case ServiceConfigsService.ArtifactNameResult.ArtifactNameNotFound            => Future.successful(notFound)
          case ServiceConfigsService.ArtifactNameResult.ArtifactNameError(error)        => logger.error(error)
                                                                                   Future.successful(InternalServerError)
        }

      def buildServicePageFromRepoName(repoName: String, hasBranchProtectionAuth: EnableBranchProtection.HasAuthorisation): OptionT[Future, Result] =
        OptionT(teamsAndRepositoriesConnector.repositoryDetails(repoName))
          .semiflatMap {
            case repositoryDetails if repositoryDetails.repoType == RepoType.Service => renderServicePage(serviceName, repositoryDetails, hasBranchProtectionAuth)
            case _ => Future.successful(notFound)
          }

      for {
        hasBranchProtectionAuth <- hasEnableBranchProtectionAuthorisation(serviceName)
        result                  <- buildServicePageFromRepoName(serviceName, hasBranchProtectionAuth)
                                    .getOrElseF(buildServicePageFromItsArtifactName(serviceName, hasBranchProtectionAuth))
      } yield result

    }

  private def retrieveZone(serviceName: String)(implicit request: Request[_]): Future[Option[CostEstimationService.Zone]] =
    serviceConfigsService.deploymentConfig(serviceName = Some(serviceName))
      .map{deploymentConfigs =>
        val zones = deploymentConfigs.map(_.zone).distinct
        if (zones.size > 1)
          logger.warn(s"Service $serviceName is hosted on different zones: \n${deploymentConfigs.map(dc => s"${dc.environment}: ${dc.zone}").mkString("\n")}")
        zones.headOption
      }

  private def renderServicePage(
    serviceName            : String,
    repositoryDetails      : GitRepository,
    hasBranchProtectionAuth: EnableBranchProtection.HasAuthorisation,
  )(implicit
    request : Request[_]
  ): Future[Result] = {
    for {
      deployments          <- whatsRunningWhereService.releasesForService(serviceName).map(_.versions)
      repositoryName       =  repositoryDetails.name
      jenkinsJobs          <- teamsAndRepositoriesConnector.lookupLatestBuildJobs(repositoryName)
      hasMongo             <- serviceMetricsConnector.getCollections(serviceName).map(_.size > 0)
      nonPerformantQueries <- if (hasMongo) 
        serviceMetricsConnector.nonPerformantQueriesForService(serviceName)
        else
          Future.successful(Seq.empty)
      envDatas             <- Environment.values.traverse { env =>
                                val slugInfoFlag: SlugInfoFlag = SlugInfoFlag.ForEnvironment(env)
                                val deployedVersions = deployments.filter(_.environment == env).map(_.versionNumber.asVersion)
                                // a single environment may have multiple versions during a deployment
                                // return the lowest
                                deployedVersions.sorted.headOption match {
                                  case Some(version) =>
                                    for {
                                      repoModules     <- serviceDependenciesConnector.getRepositoryModules(repositoryName, version)
                                      optShutterState <- shutterService.getShutterState(ShutterType.Frontend, env, serviceName)
                                      data            =  EnvData(
                                                           version                 = version,
                                                           repoModules             = repoModules.headOption,
                                                           optShutterState         = optShutterState,
                                                           telemetryLinks          = Seq(
                                                              telemetryLinks.grafanaDashboard(env, serviceName),
                                                              telemetryLinks.kibanaDashboard(env, serviceName)
                                                           ),
                                                           nonPerformantQueryLinks = if (hasMongo)
                                                            telemetryLinks.kibanaNonPerformantQueries(env, serviceName, nonPerformantQueries)
                                                            else
                                                              Seq.empty
                                                         )
                                    } yield Some(slugInfoFlag -> data)
                                  case None => Future.successful(None)
                                }
                              }.map(_.collect { case Some(v) => v }.toMap)
      latestRepoModules    <- serviceDependenciesConnector.getRepositoryModulesLatestVersion(repositoryName)
      urlIfLeaksFound      <- leakDetectionService.urlIfLeaksFound(repositoryName)
      serviceRoutes        <- routeRulesService.serviceRoutes(serviceName)
      optLatestServiceInfo <- serviceDependenciesConnector.getSlugInfo(repositoryName)
      costEstimate         <- costEstimationService.estimateServiceCost(repositoryName)
      commenterReport      <- prCommenterConnector.report(repositoryName)
      vulnerabilitiesCount <- vulnerabilitiesConnector.distinctVulnerabilities(serviceName)
      serviceRelationships <- serviceConfigsService.serviceRelationships(serviceName)
      zone                 <- retrieveZone(serviceName)
      optLatestData        =  optLatestServiceInfo.map { latestServiceInfo =>
                                SlugInfoFlag.Latest ->
                                  EnvData(
                                    version                 = latestServiceInfo.version,
                                    repoModules             = latestRepoModules,
                                    optShutterState         = None,
                                    telemetryLinks          = Seq.empty,
                                    nonPerformantQueryLinks = Seq.empty,
                                  )
                              }
    } yield Ok(serviceInfoPage(
      serviceName                  = serviceName,
      repositoryDetails            = repositoryDetails.copy(jenkinsJobs = jenkinsJobs, zone = zone),
      costEstimate                 = costEstimate,
      costEstimateConfig           = serviceCostEstimateConfig,
      repositoryCreationDate       = repositoryDetails.createdDate,
      envDatas                     = optLatestData.fold(envDatas)(envDatas + _),
      linkToLeakDetection          = urlIfLeaksFound,
      serviceRoutes                = serviceRoutes,
      hasBranchProtectionAuth      = hasBranchProtectionAuth,
      commenterReport              = commenterReport,
      distinctVulnerabilitiesCount = vulnerabilitiesCount,
      serviceRelationships         = serviceRelationships,
      config                       = config,
    ))
  }

  def library(name: String): Action[AnyContent] =
    Action(Redirect(routes.CatalogueController.repository(name)))

  def prototype(name: String): Action[AnyContent] =
    Action(Redirect(routes.CatalogueController.repository(name)))

  def repository(name: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        hasBranchProtectionAuth <- hasEnableBranchProtectionAuthorisation(name)
        result <- OptionT(teamsAndRepositoriesConnector.repositoryDetails(name))
                    .foldF(Future.successful(notFound))(repoDetails =>
                      repoDetails.repoType match {
                        case RepoType.Service   => renderServicePage(repoDetails.name, repoDetails, hasBranchProtectionAuth)
                        case RepoType.Library   => renderLibrary(repoDetails, hasBranchProtectionAuth)
                        case RepoType.Prototype => renderPrototype(repoDetails, hasBranchProtectionAuth).map(Ok(_))
                        case RepoType.Test      => renderOther(repoDetails, hasBranchProtectionAuth)
                        case RepoType.Other     => renderOther(repoDetails, hasBranchProtectionAuth)
                      }
                    )
      } yield result
    }

  def enableBranchProtection(repoName: String) =
    auth
      .authorizedAction(
        continueUrl = routes.CatalogueController.repository(repoName),
        predicate = EnableBranchProtection.permission(repoName))
      .async { implicit request =>
        teamsAndRepositoriesConnector
          .enableBranchProtection(repoName)
          .map(_ => Redirect(routes.CatalogueController.repository(repoName)))
      }

  private def hasEnableBranchProtectionAuthorisation(repoName: String)(
    implicit hc: HeaderCarrier
  ): Future[EnableBranchProtection.HasAuthorisation] =
    auth
      .verify(Retrieval.hasPredicate(EnableBranchProtection.permission(repoName)))
      .map(r => EnableBranchProtection.HasAuthorisation(r.getOrElse(false)))

  def changePrototypePassword(repoName: String) =
    auth
      .authorizedAction(
        continueUrl = routes.CatalogueController.repository(repoName),
        predicate = ChangePrototypePassword.permission(repoName)
      ).async { implicit request =>

      (for {
        hasBranchProtectionAuth <- EitherT.liftF[Future, Result, EnableBranchProtection.HasAuthorisation](hasEnableBranchProtectionAuthorisation(repoName))
        repoDetails     <- EitherT.fromOptionF[Future, Result, GitRepository](teamsAndRepositoriesConnector.repositoryDetails(repoName), notFound)
        newPassword     <- ChangePrototypePassword
                             .form()
                             .bindFromRequest()
                             .fold[EitherT[Future, Result, ChangePrototypePassword.PrototypePassword]](
                               formWithErrors => EitherT.left(renderPrototype(repoDetails, hasBranchProtectionAuth, formWithErrors).map(BadRequest(_))),
                               password => EitherT.rightT(password)
                             )
        user            =  request.session.get(AuthController.SESSION_USERNAME).getOrElse("Unknown")
        _               =  logger.info(s"User $user has triggered a password reset for prototype: $repoName")
        response        <- EitherT(buildDeployApiConnector.changePrototypePassword(repoName, newPassword))
                             .leftSemiflatMap(errorMsg => renderPrototype(repoDetails, hasBranchProtectionAuth, ChangePrototypePassword.form().withGlobalError(errorMsg) , None).map(BadRequest(_)))
        result          <- EitherT.liftF[Future, Result, Html](renderPrototype(repoDetails, hasBranchProtectionAuth, ChangePrototypePassword.form() , Some(response)))
       } yield Ok(result)
      ).merge
    }

  def setPrototypeStatus(repoName: String, status: PrototypeStatus): Action[AnyContent] =
    auth
      .authorizedAction(
        continueUrl = routes.CatalogueController.repository(repoName),
        predicate = ChangePrototypePassword.permission(repoName)
      ).async { implicit request =>

      (for {
        repoDetails   <- EitherT.fromOptionF[Future, Result, GitRepository](teamsAndRepositoriesConnector.repositoryDetails(repoName), notFound)
        user          =  request.session.get(AuthController.SESSION_USERNAME).getOrElse("Unknown")
        _             =  logger.info(s"Setting prototype $repoName to status ${status.displayString}, triggered by User: $user")
        prototypeName = repoDetails.prototypeName.getOrElse(repoName)
        _             <- EitherT.liftF[Future, Result, Unit](
                           buildDeployApiConnector.setPrototypeStatus(prototypeName, status)
                             .map(_.leftMap(err =>
                               logger.warn(s"Failed to set $prototypeName to ${status.displayString}: $err")
                             ).merge)
                         )
      } yield Redirect(routes.CatalogueController.repository(repoName))).merge
    }

  private def hasChangePrototypePasswordAuthorisation(repoName: String)(
    implicit hc: HeaderCarrier
  ): Future[ChangePrototypePassword.HasAuthorisation] =
    auth
      .verify(Retrieval.hasPredicate(ChangePrototypePassword.permission(repoName)))
      .map(r => ChangePrototypePassword.HasAuthorisation(r.getOrElse(false)))

  def renderLibrary(
    repoDetails: GitRepository,
    hasBranchProtectionAuth: EnableBranchProtection.HasAuthorisation
  )(implicit request: Request[_]): Future[Result] =
    ( teamsAndRepositoriesConnector.lookupLatestBuildJobs(repoDetails.name),
      serviceDependenciesConnector.getRepositoryModulesAllVersions(repoDetails.name),
      leakDetectionService.urlIfLeaksFound(repoDetails.name),
      prCommenterConnector.report(repoDetails.name)
    ).mapN { ( jenkinsJobs,
               repoModulesAllVersions,
               urlIfLeaksFound,
               commenterReport
             ) =>
      Ok(
        libraryInfoPage(
          repoDetails.copy(jenkinsJobs = jenkinsJobs),
          repoModulesAllVersions.sorted(Ordering.by((_: RepositoryModules).version).reverse),
          urlIfLeaksFound,
          hasBranchProtectionAuth,
          commenterReport
        )
      )
    }

  private def renderPrototype(
    repoDetails            : GitRepository,
    hasBranchProtectionAuth: EnableBranchProtection.HasAuthorisation,
    form                   : Form[_]        = ChangePrototypePassword.form(),
    successMessage         : Option[String] = None
  )(implicit request: Request[_]): Future[Html] =
    for {
      urlIfLeaksFound       <- leakDetectionService.urlIfLeaksFound(repoDetails.name)
      commenterReport       <- prCommenterConnector.report(repoDetails.name)
      hasPasswordChangeAuth <- hasChangePrototypePasswordAuthorisation(repoDetails.name)
      prototypeName         =  repoDetails.prototypeName.getOrElse(repoDetails.name)
      prototypeDetails      <- buildDeployApiConnector.getPrototypeDetails(prototypeName)
    } yield
      prototypeInfoPage(
        repoDetails,
        urlIfLeaksFound,
        hasBranchProtectionAuth,
        hasPasswordChangeAuth,
        form,
        successMessage,
        commenterReport,
        prototypeDetails
      )

  private def renderOther(
    repoDetails: GitRepository,
    hasBranchProtectionAuth: EnableBranchProtection.HasAuthorisation
  )(implicit request: Request[_]): Future[Result] =
    ( teamsAndRepositoriesConnector.lookupLatestBuildJobs(repoDetails.name),
      serviceDependenciesConnector.getRepositoryModulesLatestVersion(repoDetails.name),
      leakDetectionService.urlIfLeaksFound(repoDetails.name),
      prCommenterConnector.report(repoDetails.name)
    ).mapN { ( jenkinsJobs,
               repoModules,
               urlIfLeaksFound,
               commenterReport
             ) =>
      Ok(
        repositoryInfoPage(
          repoDetails.copy(
            teamNames  = { val (owners, writers) =  repoDetails.teamNames.partition(repoDetails.owningTeams.contains) // TODO should this apply to renderLibrary too?
                           owners.sorted ++ writers.sorted
                         },
            jenkinsJobs = jenkinsJobs
          ),
          repoModules,
          urlIfLeaksFound,
          hasBranchProtectionAuth,
          commenterReport
        )
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
      teamsAndRepositoriesConnector.allRepositories().map { repositories =>
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
        repositoryDetails           <- OptionT(teamsAndRepositoriesConnector.repositoryDetails(serviceName))
        if repositoryDetails.repoType == RepoType.Service
        costEstimation              <- OptionT.liftF(costEstimationService.estimateServiceCost(serviceName))
        historicEstimatedCostCharts <- OptionT.liftF(costEstimationService.historicEstimatedCostChartsForService(serviceName))
      } yield
        Ok(costEstimationPage(
          serviceName,
          repositoryDetails,
          costEstimation,
          serviceCostEstimateConfig,
          historicEstimatedCostCharts
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

object EnableBranchProtection {

  final case class HasAuthorisation(value: Boolean) extends AnyVal

  def permission(repoName: String): Permission =
    Predicate.Permission(
      Resource.from("catalogue-repository", repoName),
      IAAction("WRITE_BRANCH_PROTECTION")
    )
}

object ChangePrototypePassword {

  final case class HasAuthorisation(value: Boolean) extends AnyVal

  final case class PrototypePassword(value: String) extends AnyVal {
    override def toString: String = "PrototypePassword(...)"
  }

  def permission(repoName: String): Permission =
    Predicate.Permission(
      Resource.from("catalogue-repository", repoName),
      IAAction("CHANGE_PROTOTYPE_PASSWORD")
    )

  def form(): Form[PrototypePassword] =
    Form(
      Forms.mapping(
        "password" -> Forms.nonEmptyText
      )(PrototypePassword.apply)(PrototypePassword.unapply)
    )
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
