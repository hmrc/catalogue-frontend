/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.data.Forms._
import play.api.data.{Form, Mapping}
import play.api.libs.json.Json.toJson
import play.api.libs.json.{Format, Json}
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.DisplayableTeamMember._
import uk.gov.hmrc.cataloguefrontend.actions.{UmpAuthenticated, VerifySignInStatus}
import uk.gov.hmrc.cataloguefrontend.connector.RepoType.Library
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.UMPError
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.events._
import uk.gov.hmrc.cataloguefrontend.service.{DeploymentsService, LeakDetectionService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import views.html._
import uk.gov.hmrc.cataloguefrontend.service.ConfigService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class TeamActivityDates(
  firstActive: Option[LocalDateTime],
  lastActive: Option[LocalDateTime],
  firstServiceCreationDate: Option[LocalDateTime])

case class DigitalServiceDetails(
  digitalServiceName: String,
  teamMembersLookUp: Map[String, Either[UMPError, Seq[DisplayableTeamMember]]],
  repos: Map[String, Seq[String]]
)

@Singleton
class CatalogueController @Inject()(
  userManagementConnector: UserManagementConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  configService: ConfigService,
  serviceDependencyConnector: ServiceDependenciesConnector,
  indicatorsConnector: IndicatorsConnector,
  leakDetectionService: LeakDetectionService,
  deploymentsService: DeploymentsService,
  eventService: EventService,
  readModelService: ReadModelService,
  verifySignInStatus: VerifySignInStatus,
  umpAuthenticated: UmpAuthenticated,
  userManagementPortalConfig: UserManagementPortalConfig,
  mcc: MessagesControllerComponents,
  digitalServiceInfoPage: DigitalServiceInfoPage,
  indexPage: IndexPage,
  teamInfoPage: TeamInfoPage,
  serviceInfoPage: ServiceInfoPage,
  serviceConfigPage: ServiceConfigPage,
  libraryInfoPage: LibraryInfoPage,
  prototypeInfoPage: PrototypeInfoPage,
  repositoryInfoPage: RepositoryInfoPage,
  repositoriesListPage: RepositoriesListPage
) extends FrontendController(mcc) {

  import UserManagementConnector._
  import userManagementPortalConfig._

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

  def saveServiceOwner(): Action[AnyContent] = umpAuthenticated.async { implicit request =>
    request.body.asJson
      .map { payload =>
        val serviceOwnerSaveEventData: ServiceOwnerSaveEventData = payload.as[ServiceOwnerSaveEventData]
        val serviceOwnerDisplayName: String                      = serviceOwnerSaveEventData.displayName
        val maybeTeamMember: Option[TeamMember] =
          readModelService.getAllUsers.find(_.displayName.getOrElse("") == serviceOwnerDisplayName)

        maybeTeamMember.fold {
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
                .sortBy(_.name.toUpperCase),
              form = form)
          )
        }
      )
    }
  }

  def digitalService(digitalServiceName: String): Action[AnyContent] = verifySignInStatus.async { implicit request =>
    teamsAndRepositoriesConnector.digitalServiceInfo(digitalServiceName) flatMap {
      case Some(digitalService) =>
        val teamNames = digitalService.repositories.flatMap(_.teamNames).distinct
        userManagementConnector
          .getTeamMembersForTeams(teamNames)
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

  def team(teamName: String): Action[AnyContent] = Action.async { implicit request =>
    val eventualTeamInfo           = teamsAndRepositoriesConnector.teamInfo(teamName)
    val eventualErrorOrMembers     = userManagementConnector.getTeamMembersFromUMP(teamName)
    val eventualErrorOrTeamDetails = userManagementConnector.getTeamDetails(teamName)
    val eventualReposWithLeaks     = leakDetectionService.repositoriesWithLeaks

    val eventualMaybeDeploymentIndicators = indicatorsConnector.deploymentIndicatorsForTeam(teamName)
    for {
      teamInfo       <- eventualTeamInfo
      teamMembers    <- eventualErrorOrMembers
      teamDetails    <- eventualErrorOrTeamDetails
      teamIndicators <- eventualMaybeDeploymentIndicators
      reposWithLeaks <- eventualReposWithLeaks
    } yield
      (teamInfo, teamMembers, teamDetails, teamIndicators) match {
        case (Some(team), _, _, _) =>
          implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(_.toEpochSecond(ZoneOffset.UTC))

          Ok(
            teamInfoPage(
              team.name,
              repos = team.repos.getOrElse(Map()),
              activityDates =
                TeamActivityDates(team.firstActiveDate, team.lastActiveDate, team.firstServiceCreationDate),
              errorOrTeamMembers = convertToDisplayableTeamMembers(team.name, teamMembers),
              teamDetails,
              TeamChartData.deploymentThroughput(team.name, teamIndicators.map(_.throughput)),
              TeamChartData.deploymentStability(team.name, teamIndicators.map(_.stability)),
              umpMyTeamsPageUrl(team.name),
              leakDetectionService.teamHasLeaks(team, reposWithLeaks),
              leakDetectionService.hasLeaks(reposWithLeaks)
            )
          )
        case _ => NotFound(error_404_template())
      }
  }

  def serviceConfig(serviceName: String): Action[AnyContent] = Action.async { implicit request => {

    for {

      // app-config-common
      baseConfig <- configService.serviceConfigConf("base", serviceName)

      developmentConfig <- configService.serviceConfigYaml("development", serviceName)
      qaConfig <- configService.serviceConfigYaml("qa", serviceName)
      stagingConfig <- configService.serviceConfigYaml("staging", serviceName)
      // app-config-integration
      // app-config-externaltest
      // app-config-production

    } yield () match {
      case _ => {
        Ok(serviceConfigPage(serviceName, configService.buildConfigMap(baseConfig, developmentConfig, qaConfig, stagingConfig)))
      }
    }
  }
  }




  def service(name: String): Action[AnyContent] = Action.async { implicit request =>
    def getDeployedEnvs(
      deployedToEnvs: Seq[DeploymentVO],
      maybeRefEnvironments: Option[Seq[TargetEnvironment]]): Option[Seq[TargetEnvironment]] = {

      val deployedEnvNames = deployedToEnvs.map(_.environmentMapping.name)

      maybeRefEnvironments.map { environments =>
        environments
          .map(e => (e.name.toLowerCase, e))
          .map {
            case (lwrCasedRefEnvName, refEnvironment)
                if deployedEnvNames.contains(lwrCasedRefEnvName) || lwrCasedRefEnvName == "dev" =>
              refEnvironment
            case (_, refEnvironment) =>
              refEnvironment.copy(services = Nil)
          }
      }

    }

    val repositoryDetailsF = teamsAndRepositoriesConnector.repositoryDetails(name)
    val deploymentIndicatorsForServiceF: Future[Option[DeploymentIndicators]] =
      indicatorsConnector.deploymentIndicatorsForService(name)
    val serviceDeploymentInformationF: Future[Either[Throwable, ServiceDeploymentInformation]] =
      deploymentsService.getWhatsRunningWhere(name)
    val dependenciesF = serviceDependencyConnector.getDependencies(name)

    for {
      service                      <- repositoryDetailsF
      maybeDataPoints              <- deploymentIndicatorsForServiceF
      serviceDeploymentInformation <- serviceDeploymentInformationF
      mayBeDependencies            <- dependenciesF
      urlIfLeaksFound              <- leakDetectionService.urlIfLeaksFound(name)
    } yield
      (service, serviceDeploymentInformation) match {
        case (_, Left(t)) =>
          t.printStackTrace()
          ServiceUnavailable(t.getMessage)
        case (Some(repositoryDetails), Right(sdi: ServiceDeploymentInformation))
            if repositoryDetails.repoType == RepoType.Service =>
          val maybeDeployedEnvironments =
            getDeployedEnvs(sdi.deployments, repositoryDetails.environments)

          val deploymentsByEnvironmentName: Map[String, Seq[DeploymentVO]] =
            sdi.deployments
              .map(
                deployment =>
                  deployment.environmentMapping.name -> sdi.deployments
                    .filter(_.environmentMapping.name == deployment.environmentMapping.name))
              .toMap

          Ok(
            serviceInfoPage(
              repositoryDetails.copy(environments = maybeDeployedEnvironments),
              mayBeDependencies,
              ServiceChartData.deploymentThroughput(repositoryDetails.name, maybeDataPoints.map(_.throughput)),
              ServiceChartData.deploymentStability(repositoryDetails.name, maybeDataPoints.map(_.stability)),
              repositoryDetails.createdAt,
              deploymentsByEnvironmentName,
              urlIfLeaksFound
            )
          )
        case _ => NotFound(error_404_template())
      }
  }

  def library(name: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      library           <- teamsAndRepositoriesConnector.repositoryDetails(name)
      mayBeDependencies <- serviceDependencyConnector.getDependencies(name)
      urlIfLeaksFound   <- leakDetectionService.urlIfLeaksFound(name)
    } yield
      library match {
        case Some(s) if s.repoType == Library =>
          Ok(libraryInfoPage(s, mayBeDependencies, urlIfLeaksFound))
        case _ =>
          NotFound(error_404_template())
      }
  }

  def prototype(name: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      repository      <- teamsAndRepositoriesConnector.repositoryDetails(name)
      urlIfLeaksFound <- leakDetectionService.urlIfLeaksFound(name)
    } yield {
      repository match {
        case Some(s) if s.repoType == RepoType.Prototype =>
          Ok(prototypeInfoPage(s.copy(environments = None), s.createdAt, urlIfLeaksFound))
        case None => NotFound(error_404_template())
      }
    }
  }

  def repository(name: String): Action[AnyContent] = Action.async { implicit request =>
    for {
      repository           <- teamsAndRepositoriesConnector.repositoryDetails(name)
      indicators           <- indicatorsConnector.buildIndicatorsForRepository(name)
      mayBeDependencies    <- serviceDependencyConnector.getDependencies(name)
      maybeUrlIfLeaksFound <- leakDetectionService.urlIfLeaksFound(name)
    } yield
      (repository, indicators) match {
        case (Some(repositoryDetails), maybeDataPoints) =>
          Ok(
            repositoryInfoPage(
              repositoryDetails,
              mayBeDependencies,
              ServiceChartData.jobExecutionTime(repositoryDetails.name, maybeDataPoints),
              maybeUrlIfLeaksFound
            )
          )
        case _ => NotFound(error_404_template())
      }
  }

  def allServices = Action {
    Redirect("/repositories?name=&type=Service")
  }

  def allLibraries = Action {
    Redirect("/repositories?name=&type=Library")
  }

  def allPrototypes = Action {
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

  def deploymentsList(teamName: Option[String], serviceName: Option[String]): Action[AnyContent] = Action.async {
    implicit request =>
      import SearchFiltering._
      deploymentsService.getDeployments(teamName.blankToNone, serviceName.blankToNone) map { teamReleases =>
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

  private implicit class OptionOps(maybeString: Option[String]) {
    lazy val blankToNone: Option[String] =
      maybeString.map(_.trim).filterNot(_.isEmpty)
  }

  private def convertToDisplayableTeamMembers(
    teamName: String,
    errorOrTeamMembers: Either[UMPError, Seq[TeamMember]]): Either[UMPError, Seq[DisplayableTeamMember]] =
    errorOrTeamMembers match {
      case Left(err) => Left(err)
      case Right(tms) =>
        Right(DisplayableTeamMembers(teamName, userManagementProfileBaseUrl, tms))
    }

  private def convertToDisplayableTeamMembers(teamsAndMembers: Map[String, Either[UMPError, Seq[TeamMember]]])
    : Map[String, Either[UMPError, Seq[DisplayableTeamMember]]] =
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


