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
import play.api
import play.api.Configuration
import play.api.data.Forms._
import play.api.data.{Form, Mapping}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{Format, Json}
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.cataloguefrontend.DisplayableTeamMember._
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.UMPError
import uk.gov.hmrc.cataloguefrontend.connector.{DeploymentIndicators, IndicatorsConnector, ServiceDependenciesConnector}
import uk.gov.hmrc.cataloguefrontend.events._
import uk.gov.hmrc.cataloguefrontend.service.{DeploymentsService, LeakDetectionService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import views.html._

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
  serviceDependencyConnector: ServiceDependenciesConnector,
  indicatorsConnector: IndicatorsConnector,
  leakDetectionService: LeakDetectionService,
  deploymentsService: DeploymentsService,
  eventService: EventService,
  readModelService: ReadModelService,
  environment: api.Environment,
  override val runModeConfiguration: Configuration,
  val messagesApi: MessagesApi)
    extends FrontendController
    with UserManagementPortalLink
    with I18nSupport {

  import UserManagementConnector._

  val profileBaseUrlConfigKey = "user-management.profileBaseUrl"

  implicit val errorResponseFormat: Format[ErrorResponse] = Json.format[ErrorResponse]

  override protected def mode = environment.mode

  val repotypeToDetailsUrl = Map(
    RepoType.Service   -> routes.CatalogueController.service _,
    RepoType.Other     -> routes.CatalogueController.repository _,
    RepoType.Library   -> routes.CatalogueController.library _,
    RepoType.Prototype -> routes.CatalogueController.prototype _
  )

  def landingPage() = Action { request =>
    Ok(landing_page())
  }

  def serviceOwner(digitalService: String) = Action {
    readModelService
      .getDigitalServiceOwner(digitalService)
      .fold(NotFound(Json.toJson(s"owner for $digitalService not found")))(ds => Ok(Json.toJson(ds)))
  }

  def saveServiceOwner() = Action.async { implicit request =>
    request.body.asJson
      .map { payload =>
        val serviceOwnerSaveEventData: ServiceOwnerSaveEventData = payload.as[ServiceOwnerSaveEventData]
        val serviceOwnerDisplayName: String                      = serviceOwnerSaveEventData.displayName
        val maybeTeamMember: Option[TeamMember] =
          readModelService.getAllUsers.find(_.displayName.getOrElse("") == serviceOwnerDisplayName)

        maybeTeamMember.fold {
          Future(NotAcceptable(Json.toJson(s"Invalid user: $serviceOwnerDisplayName")))
        } { member =>
          member.username.fold(
            Future.successful(ExpectationFailed(Json.toJson(s"Username was not set (by UMP) for $member!")))) {
            serviceOwnerUsername =>
              eventService
                .saveServiceOwnerUpdatedEvent(
                  ServiceOwnerUpdatedEventData(serviceOwnerSaveEventData.service, serviceOwnerUsername))
                .map(_ => {
                  val string = getConfString(profileBaseUrlConfigKey, "#")
                  Ok(Json.toJson(DisplayableTeamMember(member, string)))
                })
          }
        }
      }
      .getOrElse(Future.successful(BadRequest(Json.toJson(s"""Unable to parse json: "${request.body.asText
        .getOrElse("No text in request body!")}""""))))

  }

  def allTeams() = Action.async { implicit request =>
    import SearchFiltering._

    teamsAndRepositoriesConnector.allTeams.map { response =>
      val form: Form[TeamFilter] = TeamFilter.form.bindFromRequest()

      form.fold(
        error => Ok(teams_list(teams = Seq.empty, form)),
        query => {
          Ok(
            teams_list(
              response
                .filter(query)
                .sortBy(_.name.toUpperCase),
              form))
        }
      )
    }
  }

  def digitalService(digitalServiceName: String) = Action.async { implicit request =>
    teamsAndRepositoriesConnector.digitalServiceInfo(digitalServiceName) flatMap {
      case Some(digitalService) =>
        val teamNames = digitalService.repositories.flatMap(_.teamNames).map(_.distinct)
        userManagementConnector
          .getTeamMembersForTeams(teamNames)
          .map(convertToDisplayableTeamMembers)
          .map(teamMembers =>
            Ok(digital_service_info(
              DigitalServiceDetails(digitalService.name, teamMembers, getRepos(digitalService)),
              readModelService
                .getDigitalServiceOwner(digitalServiceName)
                .map(DisplayableTeamMember(_, getConfString(profileBaseUrlConfigKey, "#")))
            )))
      case None => Future.successful(NotFound)
    }
  }

  def allUsers = Action { implicit request =>
    val filterTerm = request.getQueryString("term").getOrElse("")
    println(s"getting users: $filterTerm")
    val filteredUsers: Seq[Option[String]] = readModelService.getAllUsers
      .map(_.displayName)
      .filter(displayName => displayName.getOrElse("").toLowerCase.contains(filterTerm.toLowerCase))

    Ok(Json.toJson(filteredUsers))
  }

  def getRepos(data: DigitalService) = {
    val emptyMapOfRepoTypes = RepoType.values.map(v => v.toString -> List.empty[String]).toMap
    val mapOfRepoTypes      = data.repositories.groupBy(_.repoType).map { case (k, v) => k.toString -> v.map(_.name) }

    emptyMapOfRepoTypes ++ mapOfRepoTypes
  }

  def allDigitalServices = Action.async { implicit request =>
    import SearchFiltering._

    teamsAndRepositoriesConnector.allDigitalServices.map { response =>
      val form: Form[DigitalServiceNameFilter] = DigitalServiceNameFilter.form.bindFromRequest()

      form.fold(
        error => Ok(digital_service_list(digitalServices = Seq.empty, form)),
        query => {
          Ok(
            digital_service_list(
              response
                .filter(query)
                .sortBy(_.toUpperCase),
              form))
        }
      )
    }
  }

  def team(teamName: String) = Action.async { implicit request =>
    val eventualErrorOrMembers     = userManagementConnector.getTeamMembersFromUMP(teamName)
    val eventualErrorOrTeamDetails = userManagementConnector.getTeamDetails(teamName)
    val eventualReposWithLeaks     = leakDetectionService.repositoriesWithLeaks
    val eventualInfoAboutAllTeams  = teamsAndRepositoriesConnector.teamsWithRepositories

    val eventualMaybeDeploymentIndicators = indicatorsConnector.deploymentIndicatorsForTeam(teamName)
    for {
      allTeamsInfo <- eventualInfoAboutAllTeams
      teamInfo = allTeamsInfo.find(_.name == teamName)
      teamMembers    <- eventualErrorOrMembers
      teamDetails    <- eventualErrorOrTeamDetails
      teamIndicators <- eventualMaybeDeploymentIndicators
      reposWithLeaks <- eventualReposWithLeaks
    } yield
      (teamInfo, teamMembers, teamDetails, teamIndicators) match {
        case (Some(team), _, _, _) =>
          implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(_.toEpochSecond(ZoneOffset.UTC))

          Ok(
            team_info(
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
            ))
        case _ => NotFound
      }
  }

  def service(name: String) = Action.async { implicit request =>
    def getDeployedEnvs(
      deployedToEnvs: Seq[DeploymentVO],
      maybeRefEnvironments: Option[Seq[Environment]]): Option[Seq[Environment]] = {

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
            service_info(
              repositoryDetails.copy(environments = maybeDeployedEnvironments),
              mayBeDependencies,
              ServiceChartData.deploymentThroughput(repositoryDetails.name, maybeDataPoints.map(_.throughput)),
              ServiceChartData.deploymentStability(repositoryDetails.name, maybeDataPoints.map(_.stability)),
              repositoryDetails.createdAt,
              deploymentsByEnvironmentName,
              urlIfLeaksFound
            ))
        case _ => NotFound
      }
  }

  def library(name: String) = Action.async { implicit request =>
    for {
      library           <- teamsAndRepositoriesConnector.repositoryDetails(name)
      mayBeDependencies <- serviceDependencyConnector.getDependencies(name)
      urlIfLeaksFound   <- leakDetectionService.urlIfLeaksFound(name)
    } yield
      library match {
        case Some(s) if s.repoType == RepoType.Library =>
          Ok(library_info(s, mayBeDependencies, urlIfLeaksFound))
        case _ => NotFound(Json.toJson(ErrorResponse(NOT_FOUND, s"Library: $name does not exist")))
      }
  }

  def prototype(name: String) = Action.async { implicit request =>
    for {
      repository      <- teamsAndRepositoriesConnector.repositoryDetails(name)
      urlIfLeaksFound <- leakDetectionService.urlIfLeaksFound(name)
    } yield {
      repository match {
        case Some(s) if s.repoType == RepoType.Prototype =>
          Ok(prototype_info(s.copy(environments = None), s.createdAt, urlIfLeaksFound))
        case None => NotFound(Json.toJson(ErrorResponse(NOT_FOUND, s"Prototype: $name does not exist")))
      }
    }
  }

  def repository(name: String) = Action.async { implicit request =>
    for {
      repository           <- teamsAndRepositoriesConnector.repositoryDetails(name)
      indicators           <- indicatorsConnector.buildIndicatorsForRepository(name)
      mayBeDependencies    <- serviceDependencyConnector.getDependencies(name)
      maybeUrlIfLeaksFound <- leakDetectionService.urlIfLeaksFound(name)
    } yield
      (repository, indicators) match {
        case (Some(s), maybeDataPoints) =>
          Ok(
            repository_info(
              s,
              mayBeDependencies,
              ServiceChartData.jobExecutionTime(s.name, maybeDataPoints),
              maybeUrlIfLeaksFound
            )
          )
        case _ => NotFound
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

  def allRepositories() = Action.async { implicit request =>
    import SearchFiltering._

    teamsAndRepositoriesConnector.allRepositories.map { repositories =>
      val form: Form[RepoListFilter] = RepoListFilter.form.bindFromRequest()
      form.fold(
        error => Ok(repositories_list(repositories = Seq.empty, repotypeToDetailsUrl, error)),
        query => Ok(repositories_list(repositories = repositories.filter(query), repotypeToDetailsUrl, form))
      )
    }
  }

  def deploymentsPage() = Action.async { implicit request =>
    val umpProfileUrl                 = getConfString(profileBaseUrlConfigKey, "#")
    val form: Form[DeploymentsFilter] = DeploymentsFilter.form.bindFromRequest()
    Future.successful(Ok(deployments_page(form, umpProfileUrl)))
  }

  def deploymentsList() = Action.async { implicit request =>
    import SearchFiltering._
    val umpProfileUrl = getConfString(profileBaseUrlConfigKey, "#")

    deploymentsService.getDeployments().map { rs =>
      val form: Form[DeploymentsFilter] = DeploymentsFilter.form.bindFromRequest()
      form.fold(
        errors => {
          Ok(deployments_list(Nil, umpProfileUrl))
        },
        query => Ok(deployments_list(rs.filter(query), umpProfileUrl))
      )

    }

  }

  private def convertToDisplayableTeamMembers(
    teamName: String,
    errorOrTeamMembers: Either[UMPError, Seq[TeamMember]]): Either[UMPError, Seq[DisplayableTeamMember]] =
    errorOrTeamMembers match {
      case Left(err) => Left(err)
      case Right(tms) =>
        Right(DisplayableTeamMembers(teamName, getConfString(profileBaseUrlConfigKey, "#"), tms))
    }

  private def convertToDisplayableTeamMembers(teamsAndMembers: Map[String, Either[UMPError, Seq[TeamMember]]])
    : Map[String, Either[UMPError, Seq[DisplayableTeamMember]]] =
    teamsAndMembers.map {
      case (teamName, errorOrMembers) => (teamName, convertToDisplayableTeamMembers(teamName, errorOrMembers))
    }

}

case class TeamFilter(name: Option[String] = None) {
  def isEmpty = name.isEmpty
}

object TeamFilter {
  lazy val form = Form(
    mapping(
      "name" -> optional(text).transform[Option[String]](x => if (x.exists(_.trim.isEmpty)) None else x, identity)
    )(TeamFilter.apply)(TeamFilter.unapply)
  )
}

case class DigitalServiceNameFilter(value: Option[String] = None) {
  def isEmpty = value.isEmpty
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
  def isEmpty = name.isEmpty && repoType.isEmpty
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
