/*
 * Copyright 2021 HM Revenue & Customs
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

import cats.data.OptionT
import cats.implicits._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.Json.toJson
import play.api.mvc._
import play.api.{Configuration, Logger}
import uk.gov.hmrc.cataloguefrontend.DisplayableTeamMember._
import uk.gov.hmrc.cataloguefrontend.actions.{UmpAuthActionBuilder, VerifySignInStatus}
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.UMPError
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependency, DependencyScope, TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.events._
import uk.gov.hmrc.cataloguefrontend.model.{Environment, SlugInfoFlag}
import uk.gov.hmrc.cataloguefrontend.service.ConfigService.ArtifactNameResult.{ArtifactNameError, ArtifactNameFound, ArtifactNameNotFound}
import uk.gov.hmrc.cataloguefrontend.service.{ConfigService, DefaultBranchesService, LeakDetectionService, PlatformInitiativesService, RouteRulesService}
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterService, ShutterState, ShutterType}
import uk.gov.hmrc.cataloguefrontend.util.MarkdownLoader
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html._

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class TeamActivityDates(
  created   : Option[LocalDateTime],
  lastActive: Option[LocalDateTime]
)

case class EnvData(
  version          : Version,
  dependencies     : Seq[Dependency],
  optShutterState  : Option[ShutterState],
  optTelemetryLinks: Option[Seq[Link]]
)

@Singleton
class CatalogueController @Inject() (
  userManagementConnector      : UserManagementConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  configService                : ConfigService,
  routeRulesService            : RouteRulesService,
  serviceDependencyConnector   : ServiceDependenciesConnector,
  platformInitiativesConnector : PlatformInitiativesConnector,
  leakDetectionService         : LeakDetectionService,
  eventService                 : EventService,
  readModelService             : ReadModelService,
  shutterService               : ShutterService,
  defaultBranchesService       : DefaultBranchesService,
  verifySignInStatus           : VerifySignInStatus,
  umpAuthActionBuilder         : UmpAuthActionBuilder,
  userManagementPortalConfig   : UserManagementPortalConfig,
  configuration                : Configuration,
  mcc                          : MessagesControllerComponents,
  digitalServiceInfoPage       : DigitalServiceInfoPage,
  whatsRunningWhereService     : WhatsRunningWhereService,
  indexPage                    : IndexPage,
  teamInfoPage                 : TeamInfoPage,
  serviceInfoPage              : ServiceInfoPage,
  serviceConfigPage            : ServiceConfigPage,
  serviceConfigRawPage         : ServiceConfigRawPage,
  libraryInfoPage              : LibraryInfoPage,
  prototypeInfoPage            : PrototypeInfoPage,
  repositoryInfoPage           : RepositoryInfoPage,
  repositoriesListPage         : RepositoriesListPage,
  defaultBranchListPage        : DefaultBranchListPage,
  platformInitiativesListPage  : PlatformInitiativesListPage,
  platformInitiativesService   : PlatformInitiativesService,
  outOfDateTeamDependenciesPage: OutOfDateTeamDependenciesPage
)(implicit val ec: ExecutionContext)
    extends FrontendController(mcc) {

  import UserManagementConnector._
  import userManagementPortalConfig._

  // this value must match the configured name property for jenkins services defined under url-templates.environments at https://github.com/hmrc/app-config-base/blob/master/teams-and-repositories.conf
  private lazy val jenkinsLinkName =
    configuration.getOptional[String]("teams-and-repositories.link-name.jenkins").getOrElse("jenkins")

  private lazy val hideArchivedRepositoriesFromTeam: Boolean =
    configuration.get[Boolean]("team.hideArchivedRepositories")

  private lazy val whatsNewDisplayLines  = configuration.get[Int]("whats-new.display.lines")
  private lazy val blogPostsDisplayLines = configuration.get[Int]("blog-posts.display.lines")

  private val logger = Logger(getClass)

  private def notFound(implicit request: Request[_], messages: Messages) = NotFound(error_404_template())

  def index(): Action[AnyContent] =
    Action { implicit request =>
      val whatsNew  = MarkdownLoader.loadAndRenderMarkdownFile("VERSION_HISTORY.md", whatsNewDisplayLines)
      val blogPosts = MarkdownLoader.loadAndRenderMarkdownFile("BLOG_POSTS.md", blogPostsDisplayLines)

      Ok(indexPage(whatsNew, blogPosts))
    }

  def serviceOwner(digitalService: String): Action[AnyContent] =
    Action {
      readModelService
        .getDigitalServiceOwner(digitalService)
        .fold(NotFound(toJson(s"owner for $digitalService not found")))(ds => Ok(toJson(ds)))
    }

  def saveServiceOwner(): Action[AnyContent] =
    umpAuthActionBuilder.whenAuthenticated.async { implicit request =>
      request.body.asJson
        .map { payload =>
          val serviceOwnerSaveEventData: ServiceOwnerSaveEventData = payload.as[ServiceOwnerSaveEventData]
          val serviceOwnerDisplayName: String                      = serviceOwnerSaveEventData.displayName
          val optTeamMember: Option[TeamMember] =
            readModelService.getAllUsers.find(_.displayName.getOrElse("") == serviceOwnerDisplayName)

          optTeamMember.fold {
            Future.successful(NotAcceptable(toJson(s"Invalid user: $serviceOwnerDisplayName")))
          } { member =>
            member.username.fold(Future.successful(ExpectationFailed(toJson(s"Username was not set (by UMP) for $member!")))) { serviceOwnerUsername =>
              eventService
                .saveServiceOwnerUpdatedEvent(ServiceOwnerUpdatedEventData(serviceOwnerSaveEventData.service, serviceOwnerUsername))
                .map(_ => Ok(toJson(DisplayableTeamMember(member, userManagementProfileBaseUrl))))
            }
          }
        }
        .getOrElse(Future.successful(BadRequest(toJson(s"""Unable to parse json: "${request.body.asText
          .getOrElse("No text in request body!")}""""))))
    }

  def allTeams(): Action[AnyContent] =
    Action.async { implicit request =>
      import SearchFiltering._

      teamsAndRepositoriesConnector.allTeams.map { response =>
        val form: Form[TeamFilter] = TeamFilter.form.bindFromRequest()

        form.fold(
          _ => Ok(teams_list(teams = Seq.empty, form)),
          query =>
            Ok(
              teams_list(
                teams = response
                  .filter(query)
                  .sortBy(_.name.asString.toUpperCase),
                form = form
              )
            )
        )
      }
    }

  def digitalService(digitalServiceName: String): Action[AnyContent] =
    verifySignInStatus.async { implicit request =>
      teamsAndRepositoriesConnector.digitalServiceInfo(digitalServiceName).flatMap {
        case Some(digitalService) =>
          val teamNames: Set[TeamName] = digitalService.repositories.flatMap(_.teamNames).toSet
          val repos                    = digitalService.repositories.groupBy(_.repoType).mapValues(_.map(_.name))
          userManagementConnector
            .getTeamMembersForTeams(teamNames.toSeq)
            .map(convertToDisplayableTeamMembers)
            .map(teamMembers =>
              Ok(
                digitalServiceInfoPage(
                  digitalServiceName  = digitalService.name,
                  teamMembersLookUp   = teamMembers,
                  repos               = repos,
                  digitalServiceOwner = readModelService
                                          .getDigitalServiceOwner(digitalServiceName)
                                          .map(DisplayableTeamMember(_, userManagementProfileBaseUrl))
                )
              )
            )
        case None => Future.successful(notFound)
      }
    }

  def allUsers: Action[AnyContent] =
    Action { implicit request =>
      val filterTerm = request.getQueryString("term").getOrElse("")
      val filteredUsers: Seq[Option[String]] = readModelService.getAllUsers
        .map(_.displayName)
        .filter(displayName => displayName.getOrElse("").toLowerCase.contains(filterTerm.toLowerCase))

      Ok(toJson(filteredUsers))
    }

  def allDigitalServices: Action[AnyContent] =
    Action.async { implicit request =>
      import SearchFiltering._

      teamsAndRepositoriesConnector.allDigitalServices.map { response =>
        val form: Form[DigitalServiceNameFilter] = DigitalServiceNameFilter.form.bindFromRequest()

        form.fold(
          _ => Ok(digital_service_list(digitalServices = Seq.empty, form)),
          query =>
            Ok(
              digital_service_list(
                digitalServices = response
                  .filter(query)
                  .sortBy(_.toUpperCase),
                form = form
              )
            )
        )
      }
    }

  def team(teamName: TeamName): Action[AnyContent] =
    Action.async { implicit request =>
      teamsAndRepositoriesConnector.teamInfo(teamName).flatMap {
        case Some(teamInfo) =>
          (
            userManagementConnector.getTeamMembersFromUMP(teamName),
            userManagementConnector.getTeamDetails(teamName),
            leakDetectionService.repositoriesWithLeaks,
            serviceDependencyConnector.dependenciesForTeam(teamName),
            serviceDependencyConnector.getCuratedSlugDependenciesForTeam(teamName, SlugInfoFlag.ForEnvironment(Environment.Production)),
            if (hideArchivedRepositoriesFromTeam)
              teamsAndRepositoriesConnector.archivedRepositories.map(_.map(_.name))
            else
              Future.successful(Nil)
          ).mapN { (teamMembers, teamDetails, reposWithLeaks, masterTeamDependencies, prodDependencies, reposToHide) =>
            Ok(
              teamInfoPage(
                teamName               = teamInfo.name,
                repos                  = teamInfo.repos.getOrElse(Map.empty).map {
                                           case (repoType, repos) =>
                                             repoType -> repos.filterNot(reposToHide.contains)
                                         },
                activityDates          = TeamActivityDates(
                                           created    = teamInfo.createdDate,
                                           lastActive = teamInfo.lastActiveDate
                                         ),
                errorOrTeamMembers     = convertToDisplayableTeamMembers(teamInfo.name, teamMembers),
                errorOrTeamDetails     = teamDetails,
                umpMyTeamsUrl          = umpMyTeamsPageUrl(teamInfo.name),
                leaksFoundForTeam      = leakDetectionService.teamHasLeaks(teamInfo, reposWithLeaks),
                hasLeaks               = leakDetectionService.hasLeaks(reposWithLeaks),
                masterTeamDependencies = masterTeamDependencies.filterNot(repo => reposToHide.contains(repo.repositoryName)),
                prodDependencies       = prodDependencies
              )
            )
          }
        case _ => Future.successful(notFound)
      }
    }

  def outOfDateTeamDependencies(teamName: TeamName): Action[AnyContent] =
    Action.async { implicit request =>
      (
        serviceDependencyConnector.dependenciesForTeam(teamName),
        serviceDependencyConnector.getCuratedSlugDependenciesForTeam(teamName, SlugInfoFlag.ForEnvironment(Environment.Production))
      ).mapN { (masterTeamDependencies, prodDependencies) =>
        Ok(outOfDateTeamDependenciesPage(teamName, masterTeamDependencies, prodDependencies))
      }
    }

  def serviceConfig(serviceName: String): Action[AnyContent] =
    Action.async { implicit request =>
      for {
        configByKey <- configService.configByKey(serviceName)
      } yield Ok(serviceConfigPage(serviceName, configByKey))
    }

  def serviceConfigRaw(serviceName: String): Action[AnyContent] =
    Action.async { implicit request =>
      for {
        configByEnvironment <- configService.configByEnvironment(serviceName)
      } yield Ok(serviceConfigRawPage(serviceName, configByEnvironment))
    }

  /** Renders the service page by either the repository name, or the artefact name (if configured).
    * This is where it differs from accessing through the generic `/repositories/name` endpoint, which only
    * considers the name of the repository.
    */
  def service(serviceName: String): Action[AnyContent] =
    Action.async { implicit request =>
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

      buildServicePageFromRepoName(serviceName).getOrElseF(buildServicePageFromItsArtifactName(serviceName))
    }

  private def renderServicePage(
    serviceName      : String,
    repositoryDetails: RepositoryDetails
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
                     val telemetryLinks =
                       (for {
                         targetEnvironments <- repositoryDetails.environments.toSeq
                         targetEnvironment  <- targetEnvironments
                         if targetEnvironment.environment == env
                       } yield targetEnvironment.services.filterNot(_.name == jenkinsLinkName)).flatten

                     (
                       serviceDependencyConnector.getCuratedSlugDependencies(repositoryName, slugInfoFlag),
                       shutterService.getShutterState(ShutterType.Frontend, env, serviceName)
                     ).mapN { (dependencies, optShutterState) =>
                       Some(
                         slugInfoFlag ->
                           EnvData(
                             version = version,
                             dependencies = dependencies,
                             optShutterState = optShutterState,
                             optTelemetryLinks = Some(telemetryLinks)
                           )
                       )
                     }
                   case None => Future.successful(None)
                 }
               }
      } yield res.collect { case Some(v) => v }.toMap

    (
      teamsAndRepositoriesConnector.lookupLink(repositoryName),
      futEnvDatas,
      serviceDependencyConnector.getDependencies(repositoryName),
      serviceDependencyConnector.getCuratedSlugDependencies(repositoryName, SlugInfoFlag.Latest),
      leakDetectionService.urlIfLeaksFound(repositoryName),
      routeRulesService.serviceUrl(serviceName),
      routeRulesService.serviceRoutes(serviceName),
      serviceDependencyConnector.getSlugInfo(repositoryName)
    ).mapN { (jenkinsLink,
              envDatas,
              optDependenciesFromGithub,
              librariesOfLatestSlug,
              urlIfLeaksFound,
              serviceUrl,
              serviceRoutes,
              optLatestServiceInfo
             ) =>
      val optLatestData: Option[(SlugInfoFlag, EnvData)] =
        optLatestServiceInfo.map { latestServiceInfo =>
          SlugInfoFlag.Latest ->
            EnvData(
              version           = latestServiceInfo.version,
              dependencies      = librariesOfLatestSlug,
              optShutterState   = None,
              optTelemetryLinks = None
            )
        }

      // slugs build before the inclusion of the dependency graph data won't have build deps, so we fallback to getting them from github
      // this will be deprecated once dependency graphs data covers the majority of slugs
      val optLegacyLatestDependencies =
        if (librariesOfLatestSlug.exists(_.scope.contains(DependencyScope.Build))) None
        else optDependenciesFromGithub

      Ok(
        serviceInfoPage(
          serviceName                 = serviceName,
          repositoryDetails           = repositoryDetails.copy(jenkinsURL = jenkinsLink),
          optLegacyLatestDependencies = optLegacyLatestDependencies,
          repositoryCreationDate      = repositoryDetails.createdAt,
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
    Action.async { implicit request =>
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

  def renderLibrary(repoDetails: RepositoryDetails)(implicit request: Request[_]): Future[Result] =
    for {
      jenkinsLink     <- teamsAndRepositoriesConnector.lookupLink(repoDetails.name)
      optDependencies <- serviceDependencyConnector.getDependencies(repoDetails.name)
      urlIfLeaksFound <- leakDetectionService.urlIfLeaksFound(repoDetails.name)
    } yield
      Ok(libraryInfoPage(
        repoDetails.copy(jenkinsURL = jenkinsLink),
        optDependencies,
        urlIfLeaksFound
      ))

  private def renderPrototype(repoDetails: RepositoryDetails)(implicit messages: Messages, request: Request[_]): Future[Result] =
    for {
      urlIfLeaksFound <- leakDetectionService.urlIfLeaksFound(repoDetails.name)
    } yield
      Ok(prototypeInfoPage(
        repoDetails.copy(environments = None),
        urlIfLeaksFound
      ))

  private def renderOther(repoDetails: RepositoryDetails)(implicit messages: Messages, request: Request[_]): Future[Result] =
    for {
      jenkinsLinks      <- teamsAndRepositoriesConnector.lookupLink(repoDetails.name)
      optDependencies   <- serviceDependencyConnector.getDependencies(repoDetails.name)
      urlIfLeaksFound   <- leakDetectionService.urlIfLeaksFound(repoDetails.name)
      (owners, writers) =  repoDetails.teamNames.partition(repoDetails.owningTeams.contains)
    } yield
      Ok(repositoryInfoPage(
        repoDetails.copy(
          jenkinsURL = jenkinsLinks,
          teamNames  = owners.sorted ++ writers.sorted
        ),
        optDependencies,
        urlIfLeaksFound
      ))

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
    Action.async { implicit request =>
      import SearchFiltering._

      teamsAndRepositoriesConnector.allRepositories.map { repositories =>
        RepoListFilter.form
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(repositoriesListPage(repositories = Seq.empty, formWithErrors)),
            query =>
              Ok(
                repositoriesListPage(
                  repositories = repositories.filter(query),
                  RepoListFilter.form.bindFromRequest()
                )
              )
          )
      }
    }

  def allDefaultBranches(singleOwnership: Boolean, includeArchived: Boolean): Action[AnyContent] = {
    Action.async { implicit request =>
      teamsAndRepositoriesConnector.allDefaultBranches.map { repositories =>
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

  def platformInitiatives(displayChart: Boolean, displayProgress: Boolean): Action[AnyContent] = {
    Action.async { implicit request =>
      platformInitiativesConnector.allInitiatives.map { initiative =>
        PlatformInitiativesFilter.form
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(platformInitiativesListPage(
                initiatives       = Seq(),
                displayChart      = false,
                displayProgress   = false,
                formWithErrors
              )),
          _ =>
          Ok(platformInitiativesListPage(
            initiatives           = initiative,
            displayChart          = displayChart,
            displayProgress       = displayProgress,
            PlatformInitiativesFilter.form.bindFromRequest()
        ))
        )
      }
    }
  }

  private def convertToDisplayableTeamMembers(
    teamName: TeamName,
    errorOrTeamMembers: Either[UMPError, Seq[TeamMember]]
  ): Either[UMPError, Seq[DisplayableTeamMember]] =
    errorOrTeamMembers match {
      case Left(err) => Left(err)
      case Right(tms) =>
        Right(DisplayableTeamMembers(teamName, userManagementProfileBaseUrl, tms))
    }

  private def convertToDisplayableTeamMembers(
    teamsAndMembers: Map[TeamName, Either[UMPError, Seq[TeamMember]]]
  ): Map[TeamName, Either[UMPError, Seq[DisplayableTeamMember]]] =
    teamsAndMembers.map {
      case (teamName, errorOrMembers) => (teamName, convertToDisplayableTeamMembers(teamName, errorOrMembers))
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
  repoType: Option[String] = None
) {
  def isEmpty: Boolean = name.isEmpty && repoType.isEmpty
}

object RepoListFilter {
  lazy val form = Form(
    mapping(
      "name"     -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
      "repoType" -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity)
    )(RepoListFilter.apply)(RepoListFilter.unapply)
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

case class PlatformInitiativesFilter(
  initiativeName           : Option[String] = None,
) {
  def isEmpty: Boolean = initiativeName.isEmpty
}

object PlatformInitiativesFilter {
  lazy val form: Form[PlatformInitiativesFilter] = Form(
    mapping(
      "initiativeName" -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
    )(PlatformInitiativesFilter.apply)(PlatformInitiativesFilter.unapply)
  )
}