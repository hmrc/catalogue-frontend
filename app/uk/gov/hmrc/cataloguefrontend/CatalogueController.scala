/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.{LocalDateTime, ZoneOffset}

import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.data.Forms._
import play.api.data.{Form, Mapping}
import play.api.libs.json.Json.toJson
import play.api.libs.json.{Format, Json}
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.DisplayableTeamMember._
import uk.gov.hmrc.cataloguefrontend.actions.{UmpAuthActionBuilder, VerifySignInStatus}
import uk.gov.hmrc.cataloguefrontend.connector.RepoType.Library
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.UMPError
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependency, TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.events._
import uk.gov.hmrc.cataloguefrontend.service.{ConfigService, DeploymentsService, LeakDetectionService, RouteRulesService}
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterService, ShutterState, ShutterType, Environment => ShutteringEnvironment}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import views.html._

import scala.concurrent.{ExecutionContext, Future}

case class TeamActivityDates(
  firstActive: Option[LocalDateTime],
  lastActive: Option[LocalDateTime],
  firstServiceCreationDate: Option[LocalDateTime])

case class DigitalServiceDetails(
  digitalServiceName: String,
  teamMembersLookUp: Map[TeamName, Either[UMPError, Seq[DisplayableTeamMember]]],
  repos: Map[String, Seq[String]]
)

@Singleton
class CatalogueController @Inject()(
   userManagementConnector: UserManagementConnector,
   teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
   configService: ConfigService,
   routeRulesService: RouteRulesService,
   serviceDependencyConnector: ServiceDependenciesConnector,
   leakDetectionService: LeakDetectionService,
   deploymentsService: DeploymentsService,
   eventService: EventService,
   readModelService: ReadModelService,
   shutterService: ShutterService,
   verifySignInStatus: VerifySignInStatus,
   umpAuthActionBuilder: UmpAuthActionBuilder,
   userManagementPortalConfig: UserManagementPortalConfig,
   configuration: Configuration,
   mcc: MessagesControllerComponents,
   digitalServiceInfoPage: DigitalServiceInfoPage,
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
  private lazy val jenkinsLinkName = configuration.getOptional[String]("teams-and-repositories.link-name.jenkins").getOrElse("jenkins")

  private implicit val errorResponseFormat: Format[ErrorResponse] = Json.format[ErrorResponse]

  private val repoTypeToDetailsUrl = Map(
    RepoType.Service   -> routes.CatalogueController.service _,
    RepoType.Other     -> routes.CatalogueController.repository _,
    RepoType.Library   -> routes.CatalogueController.library _,
    RepoType.Prototype -> routes.CatalogueController.prototype _
  )

  def index(): Action[AnyContent] = Action { implicit request =>
    Ok(indexPage())
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
            member.username.fold(
              Future.successful(ExpectationFailed(toJson(s"Username was not set (by UMP) for $member!")))) {
              serviceOwnerUsername =>
                eventService
                  .saveServiceOwnerUpdatedEvent(
                    ServiceOwnerUpdatedEventData(serviceOwnerSaveEventData.service, serviceOwnerUsername))
                  .map(_ => Ok(toJson(DisplayableTeamMember(member, userManagementProfileBaseUrl))))
            }
          }
        }
        .getOrElse(Future.successful(BadRequest(
          toJson(s"""Unable to parse json: "${request.body.asText.getOrElse("No text in request body!")}""""))))
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

  def team(teamName: TeamName): Action[AnyContent] = Action.async { implicit request =>
    for {
      teamInfo         <- teamsAndRepositoriesConnector.teamInfo(teamName)
      teamMembers      <- userManagementConnector.getTeamMembersFromUMP(teamName)
      teamDetails      <- userManagementConnector.getTeamDetails(teamName)
      reposWithLeaks   <- leakDetectionService.repositoriesWithLeaks
      teamDependencies <- serviceDependencyConnector.dependenciesForTeam(teamName)
    } yield
      teamInfo match {
        case Some(team) =>
          implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(_.toEpochSecond(ZoneOffset.UTC))

          Ok(
            teamInfoPage(
              teamName            = team.name,
              repos               = team.repos.getOrElse(Map.empty),
              activityDates       = TeamActivityDates(team.firstActiveDate, team.lastActiveDate, team.firstServiceCreationDate),
              errorOrTeamMembers  = convertToDisplayableTeamMembers(team.name, teamMembers),
              errorOrTeamDetails  = teamDetails,
              umpMyTeamsUrl       = umpMyTeamsPageUrl(team.name),
              leaksFoundForTeam   = leakDetectionService.teamHasLeaks(team, reposWithLeaks),
              hasLeaks            = leakDetectionService.hasLeaks(reposWithLeaks),
              teamDependencies    = teamDependencies
            )
          )
        case _ => NotFound(error_404_template())
      }
  }

  def outOfDateTeamDependencies(teamName: TeamName): Action[AnyContent] = Action.async { implicit request =>
    for {
      teamDependencies <- serviceDependencyConnector.dependenciesForTeam(teamName)
    } yield Ok(outOfDateTeamDependenciesPage(teamName, teamDependencies))
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
    def telemetryLinksFrom(links: Seq[Link]): Seq[Link] =
      links.filterNot(_.name == jenkinsLinkName)

    def getDeployedEnvs(
        deployedToEnvs    : Seq[DeploymentVO],
        optRefEnvironments: Option[Seq[TargetEnvironment]]
      ): Option[Seq[TargetEnvironment]] = {
      val deployedEnvNames = deployedToEnvs.map(_.environmentMapping.name)
      optRefEnvironments.map {
        _.map(e => (e.name.toLowerCase, e))
         .map {
            case (lwrCasedRefEnvName, refEnvironment)
                if deployedEnvNames.contains(lwrCasedRefEnvName) || lwrCasedRefEnvName == "dev" =>
              refEnvironment.copy(services = telemetryLinksFrom(refEnvironment.services))
            case (_, refEnvironment) =>
              refEnvironment.copy(services = Nil)
          }
      }
    }

    val futDeployments =
      deploymentsService.getWhatsRunningWhere(serviceName).map(_.deployments)

    val futByEnvironment: Future[Map[String, (Version, Seq[Dependency])]] =
      for {
        deployments      <- futDeployments
        envToDeployments =  deployments.groupBy(_.environmentMapping.name)
        res              <- envToDeployments.toList.traverse { case (env, deployments) =>
                              // a single environment may have multiple versions during a deployment
                              // return the lowest
                              deployments.map(_.version).sorted.headOption match {
                                case Some(version) => serviceDependencyConnector.getCuratedSlugDependencies(serviceName, Some(version)).map {dependencies =>
                                                        (env, Some((version, dependencies)))
                                                      }
                                case None          => Future.successful((env, None))
                              }
                            }
      } yield res.collect { case (k, Some(v)) => (k, v) }.toMap

    val futShutterStateByEnvironment =
      if (CatalogueFrontendSwitches.shuttering.isEnabled)
        ShutteringEnvironment.values
          .traverse { env =>
            shutterService.getShutterState(ShutterType.Frontend, env, serviceName)
          }
          .map(
            _.collect { case Some(s) => s }
             .groupBy(_.environment)
             .mapValues(_.head)
          )
      else Future.successful(Map.empty[ShutteringEnvironment, ShutterState])

    ( teamsAndRepositoriesConnector.repositoryDetails(serviceName)
    , teamsAndRepositoriesConnector.lookupLink(serviceName)
    , futDeployments
    , futByEnvironment
    , serviceDependencyConnector.getDependencies(serviceName)
    , serviceDependencyConnector.getCuratedSlugDependencies(serviceName)
    , leakDetectionService.urlIfLeaksFound(serviceName)
    , routeRulesService.serviceUrl(serviceName)
    , routeRulesService.serviceRoutes(serviceName)
    , futShutterStateByEnvironment
    , serviceDependencyConnector.getSlugInfo(serviceName)
    ).mapN { case ( service
                  , jenkinsLink
                  , deployments
                  , byEnvironment
                  , optMasterDependencies
                  , librariesOfLatestSlug
                  , urlIfLeaksFound
                  , serviceUrl
                  , serviceRoutes
                  , shutterState
                  , latestServiceInfo
                  ) =>
      service match {
        case Some(repositoryDetails) if repositoryDetails.repoType == RepoType.Service =>
          Ok(
            serviceInfoPage(
                service                       = repositoryDetails.copy(
                                                    environments = getDeployedEnvs(deployments, repositoryDetails.environments)
                                                  , jenkinsURL   = jenkinsLink
                                                  )
              , optMasterDependencies         = optMasterDependencies
              , librariesOfLatestSlug         = librariesOfLatestSlug
              , repositoryCreationDate        = repositoryDetails.createdAt
              , dependenciesByEnvironmentName = byEnvironment.mapValues { case (_, d) => d }
              , versionByEnvironmentName      = byEnvironment.mapValues { case (v, _) => v }
              , latestVersion                 = latestServiceInfo.semanticVersion
              , linkToLeakDetection           = urlIfLeaksFound
              , productionEnvironmentRoute    = serviceUrl
              , serviceRoutes                 = serviceRoutes
              , shutterState                  = shutterState
              )
          )

        case _ => NotFound(error_404_template())
      }
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
      repository         <- teamsAndRepositoriesConnector.repositoryDetails(name)
                                .map(_.map(repo => repo.copy(teamNames = {
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

  def deploymentsPage(): Action[AnyContent] = Action.async { implicit request =>
    Future.successful {
      Ok(
        deployments_page(
          form               = DeploymentsFilter.form.bindFromRequest(),
          userProfileBaseUrl = userManagementProfileBaseUrl
        )
      )
    }
  }

  def deploymentsList(teamName: Option[TeamName], serviceName: Option[String]): Action[AnyContent] =
    Action.async { implicit request =>
      import SearchFiltering._
      deploymentsService.getDeployments(
          teamName.filterNot(_.asString.trim.isEmpty)
        , serviceName.filterNot(_.trim.isEmpty)
        ).map { teamReleases =>
          DeploymentsFilter.form
            .bindFromRequest()
            .fold(
              _ => Ok(deployments_list(Nil, userManagementProfileBaseUrl)),
              deploymentsFilter =>
                Ok(
                  deployments_list(
                    deployments        = teamReleases filter deploymentsFilter,
                    userProfileBaseUrl = userManagementProfileBaseUrl
                  )
              )
            )
        }
    }

  private def convertToDisplayableTeamMembers(
    teamName: TeamName,
    errorOrTeamMembers: Either[UMPError, Seq[TeamMember]]): Either[UMPError, Seq[DisplayableTeamMember]] =
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

case class TeamFilter(name: Option[String] = None) {
  def isEmpty: Boolean = name.isEmpty
}

object TeamFilter {
  lazy val form = Form(
    mapping(
      "name" -> optional(text).transform[Option[String]](x => if (x.exists(_.trim.isEmpty)) None else x, identity)
    )(TeamFilter.apply)(TeamFilter.unapply)
  )
}

case class DigitalServiceNameFilter(value: Option[String] = None) {
  def isEmpty: Boolean = value.isEmpty
}

object DigitalServiceNameFilter {
  lazy val form = Form(
    mapping(
      "name" -> optional(text).transform[Option[String]](x => if (x.exists(_.trim.isEmpty)) None else x, identity)
    )(DigitalServiceNameFilter.apply)(DigitalServiceNameFilter.unapply)
  )
}

case class DeploymentsFilter(
  team: Option[String]        = None,
  serviceName: Option[String] = None,
  from: Option[LocalDateTime] = None,
  to: Option[LocalDateTime]   = None) {
  def isEmpty: Boolean = team.isEmpty && serviceName.isEmpty && from.isEmpty && to.isEmpty
}

case class RepoListFilter(name: Option[String] = None, repoType: Option[String] = None) {
  def isEmpty: Boolean = name.isEmpty && repoType.isEmpty
}

object RepoListFilter {
  lazy val form = Form(
    mapping(
      "name" -> optional(text).transform[Option[String]](x => if (x.exists(_.trim.isEmpty)) None else x, identity),
      "type" -> optional(text).transform[Option[String]](x => if (x.exists(_.trim.isEmpty)) None else x, identity)
    )(RepoListFilter.apply)(RepoListFilter.unapply)
  )
}

object DeploymentsFilter {

  import DateHelper._

  lazy val form = Form(
    mapping(
      "team" -> optional(text).transform[Option[String]](x => if (x.exists(_.trim.isEmpty)) None else x, identity),
      "serviceName" -> optional(text)
        .transform[Option[String]](x => if (x.exists(_.trim.isEmpty)) None else x, identity),
      "from" -> optionalLocalDateTimeMapping("from.error.date"),
      "to"   -> optionalLocalDateTimeMapping("to.error.date")
    )(DeploymentsFilter.apply)(DeploymentsFilter.unapply)
  )

  def optionalLocalDateTimeMapping(errorCode: String): Mapping[Option[LocalDateTime]] =
    optional(text.verifying(errorCode, x => stringToLocalDateTimeOpt(x).isDefined))
      .transform[Option[LocalDateTime]](_.flatMap(stringToLocalDateTimeOpt), _.map(_.format(`yyyy-MM-dd`)))
}
