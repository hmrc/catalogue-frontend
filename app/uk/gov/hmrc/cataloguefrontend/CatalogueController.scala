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

import java.time.LocalDateTime

import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json.toJson
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.DisplayableTeamMember._
import uk.gov.hmrc.cataloguefrontend.actions.{UmpAuthActionBuilder, VerifySignInStatus}
import uk.gov.hmrc.cataloguefrontend.connector.RepoType.Library
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.UMPError
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependency, TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.events._
import uk.gov.hmrc.cataloguefrontend.model.{Environment, SlugInfoFlag}
import uk.gov.hmrc.cataloguefrontend.service.{ConfigService, LeakDetectionService, RouteRulesService}
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterService, ShutterState, ShutterType}
import uk.gov.hmrc.cataloguefrontend.util.MarkdownLoader
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{Platform, WhatsRunningWhereService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html._

import scala.concurrent.{ExecutionContext, Future}

case class TeamActivityDates(
  firstActive: Option[LocalDateTime],
  lastActive: Option[LocalDateTime],
  firstServiceCreationDate: Option[LocalDateTime]
)

case class DigitalServiceDetails(
  digitalServiceName: String,
  teamMembersLookUp: Map[TeamName, Either[UMPError, Seq[DisplayableTeamMember]]],
  repos: Map[String, Seq[String]]
)

case class EnvData(
  version          : Version,
  optPlatform      : Option[Platform],
  dependencies     : Seq[Dependency],
  optShutterState  : Option[ShutterState],
  optTelemetryLinks: Option[Seq[Link]]
)

@Singleton
class CatalogueController @Inject()(
  userManagementConnector: UserManagementConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  configService: ConfigService,
  routeRulesService: RouteRulesService,
  serviceDependencyConnector: ServiceDependenciesConnector,
  leakDetectionService: LeakDetectionService,
  eventService: EventService,
  readModelService: ReadModelService,
  shutterService: ShutterService,
  verifySignInStatus: VerifySignInStatus,
  umpAuthActionBuilder: UmpAuthActionBuilder,
  userManagementPortalConfig: UserManagementPortalConfig,
  configuration: Configuration,
  mcc: MessagesControllerComponents,
  digitalServiceInfoPage: DigitalServiceInfoPage,
  whatsRunningWhereService: WhatsRunningWhereService,
  indexPage: IndexPage,
  teamInfoPage: TeamInfoPage,
  serviceInfoPage: ServiceInfoPage,
  serviceConfigPage: ServiceConfigPage,
  serviceConfigRawPage: ServiceConfigRawPage,
  libraryInfoPage: LibraryInfoPage,
  prototypeInfoPage: PrototypeInfoPage,
  repositoryInfoPage: RepositoryInfoPage,
  repositoriesListPage: RepositoriesListPage,
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
  private lazy val blogPostsDisplayLines  = configuration.get[Int]("blog-posts.display.lines")

  private val repoTypeToDetailsUrl = Map(
    RepoType.Service   -> routes.CatalogueController.service _,
    RepoType.Other     -> routes.CatalogueController.repository _,
    RepoType.Library   -> routes.CatalogueController.library _,
    RepoType.Prototype -> routes.CatalogueController.prototype _
  )

  def index(): Action[AnyContent] = Action { implicit request =>
    val whatsNew = MarkdownLoader.loadAndRenderMarkdownFile("VERSION_HISTORY.md", whatsNewDisplayLines)
    val blogPosts = MarkdownLoader.loadAndRenderMarkdownFile("BLOG_POSTS.md", blogPostsDisplayLines)

    Ok(indexPage(whatsNew, blogPosts))
  }

  def serviceOwner(digitalService: String): Action[AnyContent] = Action {
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

  def allTeams(): Action[AnyContent] = Action.async { implicit request =>
    import SearchFiltering._

    teamsAndRepositoriesConnector.allTeams.map { response =>
      val form: Form[TeamFilter] = TeamFilter.form.bindFromRequest()

      form.fold(
        _ => Ok(teams_list(teams = Seq.empty, form)),
        query => {
          Ok(
            teams_list(
              teams = response
                .filter(query)
                .sortBy(_.name.asString.toUpperCase),
              form = form)
          )
        }
      )
    }
  }

  def digitalService(digitalServiceName: String): Action[AnyContent] =
    verifySignInStatus.async { implicit request =>
      teamsAndRepositoriesConnector.digitalServiceInfo(digitalServiceName).flatMap {
        case Some(digitalService) =>
          val teamNames: Set[TeamName] = digitalService.repositories.flatMap(_.teamNames).toSet
          userManagementConnector
            .getTeamMembersForTeams(teamNames.toSeq)
            .map(convertToDisplayableTeamMembers)
            .map(teamMembers =>
              Ok(digitalServiceInfoPage(
                DigitalServiceDetails(digitalService.name, teamMembers, getRepos(digitalService)),
                readModelService
                  .getDigitalServiceOwner(digitalServiceName)
                  .map(DisplayableTeamMember(_, userManagementProfileBaseUrl))
              )))
        case None => Future.successful(NotFound(error_404_template()))
      }
    }

  def allUsers: Action[AnyContent] = Action { implicit request =>
    val filterTerm = request.getQueryString("term").getOrElse("")
    val filteredUsers: Seq[Option[String]] = readModelService.getAllUsers
      .map(_.displayName)
      .filter(displayName => displayName.getOrElse("").toLowerCase.contains(filterTerm.toLowerCase))

    Ok(toJson(filteredUsers))
  }

  private def getRepos(data: DigitalService): Map[String, Seq[String]] = {
    val emptyMapOfRepoTypes = RepoType.values.map(v => v.toString -> List.empty[String]).toMap
    val mapOfRepoTypes      = data.repositories.groupBy(_.repoType).map { case (k, v) => k.toString -> v.map(_.name) }

    emptyMapOfRepoTypes ++ mapOfRepoTypes
  }

  def allDigitalServices: Action[AnyContent] = Action.async { implicit request =>
    import SearchFiltering._

    teamsAndRepositoriesConnector.allDigitalServices.map { response =>
      val form: Form[DigitalServiceNameFilter] = DigitalServiceNameFilter.form.bindFromRequest()

      form.fold(
        _ => Ok(digital_service_list(digitalServices = Seq.empty, form)),
        query => {
          Ok(
            digital_service_list(
              digitalServices = response
                .filter(query)
                .sortBy(_.toUpperCase),
              form = form
            )
          )
        }
      )
    }
  }

  def team(teamName: TeamName): Action[AnyContent] =
    Action.async { implicit request =>
      teamsAndRepositoriesConnector.teamInfo(teamName).flatMap {
        case Some(teamInfo) =>
          ( userManagementConnector.getTeamMembersFromUMP(teamName)
          , userManagementConnector.getTeamDetails(teamName)
          , leakDetectionService.repositoriesWithLeaks
          , serviceDependencyConnector.dependenciesForTeam(teamName)
          , serviceDependencyConnector.getCuratedSlugDependenciesForTeam(teamName, SlugInfoFlag.ForEnvironment(Environment.Production))
          , if (hideArchivedRepositoriesFromTeam) teamsAndRepositoriesConnector.archivedRepositories.map(_.map(_.name)) else Future.successful(Nil)
          ).mapN {
            (teamMembers, teamDetails, reposWithLeaks, masterTeamDependencies, prodDependencies, reposToHide) =>
              Ok(
                teamInfoPage(
                  teamName               = teamInfo.name,
                  repos                  = teamInfo.repos.getOrElse(Map.empty).map { case (repoType, repos) =>
                                            repoType -> repos.filterNot(reposToHide.contains(_))
                                           },
                  activityDates          = TeamActivityDates(teamInfo.firstActiveDate, teamInfo.lastActiveDate, teamInfo.firstServiceCreationDate),
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
        case _ => Future.successful(NotFound(error_404_template()))
      }
    }

  def outOfDateTeamDependencies(teamName: TeamName): Action[AnyContent] =
    Action.async { implicit request =>
      ( serviceDependencyConnector.dependenciesForTeam(teamName)
      , serviceDependencyConnector.getCuratedSlugDependenciesForTeam(teamName, SlugInfoFlag.ForEnvironment(Environment.Production))
      ).mapN { (masterTeamDependencies, prodDependencies) =>
        Ok(outOfDateTeamDependenciesPage(teamName, masterTeamDependencies, prodDependencies))
      }
    }

  def serviceConfig(serviceName: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      configByKey <- configService.configByKey(serviceName)
    } yield Ok(serviceConfigPage(serviceName, configByKey))
  }

  def serviceConfigRaw(serviceName: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      configByEnvironment <- configService.configByEnvironment(serviceName)
    } yield Ok(serviceConfigRawPage(serviceName, configByEnvironment))
  }

  def service(serviceName: String): Action[AnyContent] = Action.async { implicit request =>
    teamsAndRepositoriesConnector.repositoryDetails(serviceName).flatMap {
      case Some(repositoryDetails) if repositoryDetails.repoType == RepoType.Service =>
        val futEnvDatas: Future[Map[SlugInfoFlag, EnvData]] =
          for {
            deployments <- whatsRunningWhereService.releasesForService(serviceName).map(_.versions)
            res <- Environment.values.traverse { env =>
                    val slugInfoFlag     = SlugInfoFlag.ForEnvironment(env)
                    val deployedVersions = deployments.filter(_.environment == env).map(x => (x.versionNumber.asVersion, x.platform))
                    // a single environment may have multiple versions during a deployment
                    // return the lowest
                    deployedVersions.sortBy(_._1).headOption match {
                      case Some((version, platform)) =>
                        val telemetryLinks =
                          (for {
                             targetEnvironments <- repositoryDetails.environments.toSeq
                             targetEnvironment  <- targetEnvironments
                             if targetEnvironment.environment == env
                           } yield targetEnvironment.services.filterNot(_.name == jenkinsLinkName)
                          ).flatten

                        ( serviceDependencyConnector.getCuratedSlugDependencies(serviceName, slugInfoFlag)
                        , shutterService.getShutterState(ShutterType.Frontend, env, serviceName)
                        ).mapN { (dependencies, optShutterState) =>
                          Some(slugInfoFlag ->
                            EnvData(
                              version           = version,
                              optPlatform       = Some(platform),
                              dependencies      = dependencies,
                              optShutterState   = optShutterState,
                              optTelemetryLinks = Some(telemetryLinks)
                            )
                          )
                        }
                      case None => Future.successful(None)
                    }
                  }
          } yield res.collect { case Some(v) => v }.toMap

        ( teamsAndRepositoriesConnector.lookupLink(serviceName)
        , futEnvDatas
        , serviceDependencyConnector.getDependencies(serviceName)
        , serviceDependencyConnector.getCuratedSlugDependencies(serviceName, SlugInfoFlag.Latest)
        , leakDetectionService.urlIfLeaksFound(serviceName)
        , routeRulesService.serviceUrl(serviceName)
        , routeRulesService.serviceRoutes(serviceName)
        , serviceDependencyConnector.getSlugInfo(serviceName)
        ).mapN {
          (jenkinsLink, envDatas, optMasterDependencies, librariesOfLatestSlug, urlIfLeaksFound, serviceUrl, serviceRoutes, optLatestServiceInfo) =>
            val optLatestData: Option[(SlugInfoFlag, EnvData)] =
              optLatestServiceInfo.map { latestServiceInfo =>
                SlugInfoFlag.Latest ->
                  EnvData(
                    version           = latestServiceInfo.semanticVersion.get,
                    optPlatform       = None,
                    dependencies      = librariesOfLatestSlug,
                    optShutterState   = None,
                    optTelemetryLinks = None
                  )
              }

            Ok(
              serviceInfoPage(
                repositoryDetails          = repositoryDetails.copy(jenkinsURL = jenkinsLink),
                optMasterDependencies      = optMasterDependencies,
                repositoryCreationDate     = repositoryDetails.createdAt,
                envDatas                   = optLatestData.fold(envDatas)(envDatas + _),
                linkToLeakDetection        = urlIfLeaksFound,
                productionEnvironmentRoute = serviceUrl,
                serviceRoutes              = serviceRoutes
              )
            )
        }

      case _ => Future.successful(NotFound(error_404_template()))
    }
  }

  def library(name: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      library         <- teamsAndRepositoriesConnector.repositoryDetails(name)
      jenkinsLink     <- teamsAndRepositoriesConnector.lookupLink(name)
      optDependencies <- serviceDependencyConnector.getDependencies(name)
      urlIfLeaksFound <- leakDetectionService.urlIfLeaksFound(name)
    } yield
      library match {
        case Some(s) if s.repoType == Library =>
          Ok(libraryInfoPage(s.copy(jenkinsURL = jenkinsLink), optDependencies, urlIfLeaksFound))
        case _ =>
          NotFound(error_404_template())
      }
  }

  def prototype(name: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      repository      <- teamsAndRepositoriesConnector.repositoryDetails(name)
      urlIfLeaksFound <- leakDetectionService.urlIfLeaksFound(name)
    } yield
      repository match {
        case Some(s) if s.repoType == RepoType.Prototype =>
          Ok(prototypeInfoPage(s.copy(environments = None), s.createdAt, urlIfLeaksFound))
        case None => NotFound(error_404_template())
      }
  }

  def repository(name: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      repository <- teamsAndRepositoriesConnector
                     .repositoryDetails(name)
                     .map(_.map(repo =>
                       repo.copy(teamNames = {
                         val (owners, other) = repo.teamNames.partition(s => repo.owningTeams.contains(s))
                         owners.sorted ++ other.sorted
                       })))
      jenkinsLinks       <- teamsAndRepositoriesConnector.lookupLink(name)
      optDependencies    <- serviceDependencyConnector.getDependencies(name)
      optUrlIfLeaksFound <- leakDetectionService.urlIfLeaksFound(name)
    } yield
      repository match {
        case Some(repositoryDetails) =>
          Ok(
            repositoryInfoPage(
              repositoryDetails.copy(jenkinsURL = jenkinsLinks),
              optDependencies,
              optUrlIfLeaksFound
            )
          )
        case _ => NotFound(error_404_template())
      }
  }

  def allServices: Action[AnyContent] = Action {
    Redirect("/repositories?name=&type=Service")
  }

  def allLibraries: Action[AnyContent] = Action {
    Redirect("/repositories?name=&type=Library")
  }

  def allPrototypes: Action[AnyContent] = Action {
    Redirect("/repositories?name=&type=Prototype")
  }

  def allRepositories(): Action[AnyContent] = Action.async { implicit request =>
    import SearchFiltering._

    teamsAndRepositoriesConnector.allRepositories.map { repositories =>
      RepoListFilter.form
        .bindFromRequest()
        .fold(
          formWithErrors => Ok(repositoriesListPage(repositories = Seq.empty, repoTypeToDetailsUrl, formWithErrors)),
          query =>
            Ok(
              repositoriesListPage(
                repositories = repositories.filter(query),
                repoTypeToDetailsUrl,
                RepoListFilter.form.bindFromRequest()
              )
          )
        )
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
  name: Option[String]     = None,
  repoType: Option[String] = None
) {
  def isEmpty: Boolean = name.isEmpty && repoType.isEmpty
}

object RepoListFilter {
  lazy val form = Form(
    mapping(
      "name" -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
      "type" -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity)
    )(RepoListFilter.apply)(RepoListFilter.unapply)
  )
}