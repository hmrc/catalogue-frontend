/*
 * Copyright 2017 HM Revenue & Customs
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

import cats.data.EitherT
import play.api.Play.current
import play.api.data.Forms._
import play.api.data.{Form, Mapping}
import play.api.i18n.Messages.Implicits._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.cataloguefrontend.DisplayableTeamMembers.DisplayableTeamMember
import uk.gov.hmrc.cataloguefrontend.TeamsAndRepositoriesConnector.TeamsAndRepositoriesError
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.{TeamMember, UMPError}
import uk.gov.hmrc.cataloguefrontend.events._
import uk.gov.hmrc.play.frontend.controller.FrontendController
import views.html._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

case class TeamActivityDates(firstActive: Option[LocalDateTime], lastActive: Option[LocalDateTime], firstServiceCreationDate: Option[LocalDateTime])

case class DigitalServiceDetails(digitalServiceName: String,
                                 teamMembersLookUp: Map[String, Either[UMPError, Seq[DisplayableTeamMember]]],
                                 repos: Map[String, Seq[String]])


object CatalogueController extends CatalogueController with MongoDbConnection {

  override def userManagementConnector: UserManagementConnector = UserManagementConnector

  override def teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector = TeamsAndRepositoriesConnector

  override def indicatorsConnector: IndicatorsConnector = IndicatorsConnector

  lazy override val deploymentsService: DeploymentsService = new DeploymentsService(ServiceDeploymentsConnector, TeamsAndRepositoriesConnector)

  lazy override val eventService = new DefaultEventService(new MongoEventRepository(db))

  lazy override val readModelService = new DefaultReadModelService(eventService, UserManagementConnector)
}

trait CatalogueController extends FrontendController with UserManagementPortalLink {

  val profileBaseUrlConfigKey = "user-management.profileBaseUrl"

  val repotypeToDetailsUrl = Map(
    RepoType.Service -> routes.CatalogueController.service _,
    RepoType.Other -> routes.CatalogueController.repository _,
    RepoType.Library -> routes.CatalogueController.library _,
    RepoType.Prototype -> routes.CatalogueController.prototype _
  )

  def userManagementConnector: UserManagementConnector

  def teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector

  def indicatorsConnector: IndicatorsConnector

  def deploymentsService: DeploymentsService

  def eventService: EventService

  def readModelService: ReadModelService

  def landingPage() = Action { request =>
    Ok(landing_page())
  }

  def serviceOwner(digitalService: String) = Action {
    readModelService.getDigitalServiceOwner(digitalService).fold(NotFound(Json.toJson(s"owner for $digitalService not found")))(ds => Ok(Json.toJson(ds)))
  }


  def saveServiceOwner() = Action.async { implicit request =>
    request.body.asJson.map{ payload =>
      val serviceOwnerUpdatedEventData = payload.as[ServiceOwnerUpdatedEventData]
      val userValid = readModelService.getAllUsers.map(_.displayName.getOrElse("")).contains(serviceOwnerUpdatedEventData.name)
      if (userValid) {
        eventService.saveServiceOwnerUpdatedEvent(serviceOwnerUpdatedEventData).map { _ =>
          Ok(Json.toJson(s"${serviceOwnerUpdatedEventData.name}"))
        }
      } else {
        Future(NotAcceptable(Json.toJson(s"Invalid user: ${serviceOwnerUpdatedEventData.name}")))
      }
    }.getOrElse(Future.successful(BadRequest(Json.toJson( s"""Unable to parse json: "${request.body.asText.getOrElse("No Text!")}""""))))

  }


  def allTeams() = Action.async { implicit request =>
    import SearchFiltering._

    teamsAndRepositoriesConnector.allTeams.map { response =>
      val form: Form[TeamFilter] = TeamFilter.form.bindFromRequest()

      form.fold(
        error =>
          Ok(
            teams_list(
              response.formattedTimestamp,
              teams = Seq.empty,
              form)),
        query => {
          Ok(
            teams_list(
              response.formattedTimestamp,
              response
                .data
                .filter(query)
                .sortBy(_.name.toUpperCase),
              form))
        }
      )
    }
  }


  def digitalService(digitalServiceName: String) = Action.async { implicit request =>
    import cats.instances.future._

    type TeamsAndRepoType[A] = Future[Either[TeamsAndRepositoriesError, A]]

    val eventualDigitalServiceInfoF: TeamsAndRepoType[Timestamped[DigitalService]] =
      teamsAndRepositoriesConnector.digitalServiceInfo(digitalServiceName)


    val errorOrTeamNames: TeamsAndRepoType[Seq[String]] =
      eventualDigitalServiceInfoF.map(_.right.map(_.data.repositories.flatMap(_.teamNames)).right.map(_.distinct))

    val errorOrTeamMembersLookupF: Future[Either[TeamsAndRepositoriesError, Map[String, Either[UMPError, Seq[DisplayableTeamMember]]]]] = errorOrTeamNames.flatMap {
      case Right(teamNames) =>
        userManagementConnector
          .getTeamMembersForTeams(teamNames)
          .map(convertToDisplayableTeamMembers)
          .map(Right(_))
      case Left(connectorError) =>
        Future.successful(Left(connectorError))
    }


    val digitalServiceDetails: EitherT[Future, TeamsAndRepositoriesError, Timestamped[DigitalServiceDetails]] = for {
      timestampedDigitalService <- EitherT(eventualDigitalServiceInfoF)
      teamMembers <- EitherT(errorOrTeamMembersLookupF)
    } yield timestampedDigitalService.map(digitalService => DigitalServiceDetails(digitalServiceName, teamMembers, getRepos(digitalService)))

    digitalServiceDetails.value.map(d => {
      Ok(digital_service_info(digitalServiceName, d, readModelService.getDigitalServiceOwner(digitalServiceName)))
    })
  }

  def allUsers = Action { implicit request =>

    val filterTerm = request.getQueryString("term").getOrElse("")
    println(s"getting users: $filterTerm")
    val filteredUsers: Seq[Option[String]] = readModelService.getAllUsers.map(_.displayName).filter(displayName => displayName.getOrElse("").toLowerCase.contains(filterTerm.toLowerCase))

    Ok(Json.toJson(filteredUsers))
  }

  def getRepos(data: DigitalService) = {
    val emptyMapOfRepoTypes = RepoType.values.map(v => v.toString -> List.empty[String]).toMap
    val mapOfRepoTypes = data.repositories.groupBy(_.repoType).map { case (k, v) => k.toString -> v.map(_.name) }

    emptyMapOfRepoTypes ++ mapOfRepoTypes
  }


  def allDigitalServices = Action.async { implicit request =>
    import SearchFiltering._

    teamsAndRepositoriesConnector.allDigitalServices.map { response =>

      val form: Form[DigitalServiceNameFilter] = DigitalServiceNameFilter.form.bindFromRequest()

      form.fold(
        error =>
          Ok(
            digital_service_list(
              response.formattedTimestamp,
              digitalServices = Seq.empty,
              form)),
        query => {
          Ok(
            digital_service_list(
              response.formattedTimestamp,
              response
                .data
                .filter(query)
                .sortBy(_.toUpperCase),
              form))
        }
      )
    }
  }


  def team(teamName: String) = Action.async { implicit request =>

    val eventualTeamInfo = teamsAndRepositoriesConnector.teamInfo(teamName)
    val eventualErrorOrMembers = userManagementConnector.getTeamMembersFromUMP(teamName)
    val eventualErrorOrTeamDetails = userManagementConnector.getTeamDetails(teamName)

    val eventualMaybeDeploymentIndicators = indicatorsConnector.deploymentIndicatorsForTeam(teamName)
    for {
      typeRepos: Option[Timestamped[Team]] <- eventualTeamInfo
      teamMembers <- eventualErrorOrMembers
      teamDetails <- eventualErrorOrTeamDetails
      teamIndicators <- eventualMaybeDeploymentIndicators
    } yield (typeRepos, teamMembers, teamDetails, teamIndicators) match {
      case (Some(s), _, _, _) =>
        implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(_.toEpochSecond(ZoneOffset.UTC))

        Ok(team_info(
          s.formattedTimestamp,
          teamName,
          repos = s.data.repos.getOrElse(Map()),
          activityDates = TeamActivityDates(s.data.firstActiveDate, s.data.lastActiveDate, s.data.firstServiceCreationDate),
          errorOrTeamMembers = convertToDisplayableTeamMembers(teamName, teamMembers),
          teamDetails,
          TeamChartData.deploymentThroughput(teamName, teamIndicators.map(_.throughput)),
          TeamChartData.deploymentStability(teamName, teamIndicators.map(_.stability)),
          umpMyTeamsPageUrl(teamName)
        )
        )
      case _ => NotFound
    }
  }


  def service(name: String) = Action.async { implicit request =>

    def getDeployedEnvs(deployedToEnvs: Seq[DeploymentVO], maybeRefEnvironments: Option[Seq[Environment]]): Option[Seq[Environment]] = {

      val deployedEnvNames = deployedToEnvs.map(_.environmentMapping.name)

      maybeRefEnvironments.map { environments =>
        environments
          .map(e => (e.name.toLowerCase, e))
          .map {
            case (lwrCasedRefEnvName, refEnvironment) if deployedEnvNames.contains(lwrCasedRefEnvName) || lwrCasedRefEnvName == "dev" =>
              refEnvironment
            case (_, refEnvironment) =>
              refEnvironment.copy(services = Nil)
          }
      }

    }

    val repositoryDetailsF = teamsAndRepositoriesConnector.repositoryDetails(name)
    val deploymentIndicatorsForServiceF: Future[Option[DeploymentIndicators]] = indicatorsConnector.deploymentIndicatorsForService(name)
    val serviceDeploymentInformationF: Future[Either[Throwable, ServiceDeploymentInformation]] = deploymentsService.getWhatsRunningWhere(name)

    for {
      service <- repositoryDetailsF
      maybeDataPoints <- deploymentIndicatorsForServiceF
      serviceDeploymentInformation: Either[Throwable, ServiceDeploymentInformation] <- serviceDeploymentInformationF
    } yield (service, serviceDeploymentInformation) match {
      case (_, Left(t)) =>
        t.printStackTrace()
        ServiceUnavailable(t.getMessage)
      case (Some(repositoryDetails), Right(sdi: ServiceDeploymentInformation)) if repositoryDetails.data.repoType == RepoType.Service =>
        val maybeDeployedEnvironments =
          getDeployedEnvs(sdi.deployments, repositoryDetails.data.environments)

        val deploymentsByEnvironmentName: Map[String, Seq[DeploymentVO]] =
          sdi.deployments.map(deployment =>
            deployment.environmentMapping.name -> sdi.deployments
              .filter(_.environmentMapping.name == deployment.environmentMapping.name))
            .toMap

      Ok(
        service_info(
          repositoryDetails.formattedTimestamp,
          repositoryDetails.data.copy(environments = maybeDeployedEnvironments),
          ServiceChartData.deploymentThroughput(name, maybeDataPoints.map(_.throughput)),
          ServiceChartData.deploymentStability(name, maybeDataPoints.map(_.stability)),
          repositoryDetails.data.createdAt,
          deploymentsByEnvironmentName
        ))
      case _ => NotFound
    }
  }

  def library(name: String) = Action.async { implicit request =>
    for {
      library <- teamsAndRepositoriesConnector.repositoryDetails(name)
    } yield library match {
      case Some(s) if s.data.repoType == RepoType.Library =>
        Ok(
          library_info(
            s.formattedTimestamp,
            s.data))
      case _ => NotFound
    }
  }

  def prototype(name: String) = Action.async { implicit request =>
    teamsAndRepositoriesConnector
      .repositoryDetails(name)
      .map {
        case Some(s) if s.data.repoType == RepoType.Prototype =>
          val result = Ok(prototype_info(
            s.formattedTimestamp,
            s.data.copy(environments = None),
            s.data.createdAt))
          result
        case _ => NotFound
      }
  }

  def repository(name: String) = Action.async { implicit request =>
    for {
      repository <- teamsAndRepositoriesConnector.repositoryDetails(name)
      indicators <- indicatorsConnector.buildIndicatorsForRepository(name)
    } yield (repository, indicators) match {
      case (Some(s), maybeDataPoints) =>
        Ok(
          repository_info(
            s.formattedTimestamp,
            s.data,
            ServiceChartData.jobExecutionTime(name, maybeDataPoints)))
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
        error =>
          Ok(
            repositories_list(
              repositories.formattedTimestamp,
              repositories = Seq.empty,
              repotypeToDetailsUrl,
              error)),
        query =>
          Ok(
            repositories_list(
              repositories.formattedTimestamp,
              repositories = repositories.data.filter(query),
              repotypeToDetailsUrl,
              form))
      )
    }
  }

  def deploymentsPage() = Action.async { implicit request =>
    val umpProfileUrl = getConfString(profileBaseUrlConfigKey, "#")
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

  private def convertToDisplayableTeamMembers(teamName: String, errorOrTeamMembers: Either[UMPError, Seq[TeamMember]]): Either[UMPError, Seq[DisplayableTeamMember]] =
    errorOrTeamMembers match {
      case Left(err) => Left(err)
      case Right(tms) =>
        Right(DisplayableTeamMembers(teamName, getConfString(profileBaseUrlConfigKey, "#"), tms))
    }


  private def convertToDisplayableTeamMembers(teamsAndMembers: Map[String, Either[UMPError, Seq[TeamMember]]]): Map[String, Either[UMPError, Seq[DisplayableTeamMember]]] =
    teamsAndMembers.map { case (teamName, errorOrMembers) => (teamName, convertToDisplayableTeamMembers(teamName, errorOrMembers)) }


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

case class DeploymentsFilter(team: Option[String] = None, serviceName: Option[String] = None, from: Option[LocalDateTime] = None, to: Option[LocalDateTime] = None) {
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
      "serviceName" -> optional(text).transform[Option[String]](x => if (x.exists(_.trim.isEmpty)) None else x, identity),
      "from" -> optionalLocalDateTimeMapping("from.error.date"),
      "to" -> optionalLocalDateTimeMapping("to.error.date")
    )(DeploymentsFilter.apply)(DeploymentsFilter.unapply)
  )

  def optionalLocalDateTimeMapping(errorCode: String): Mapping[Option[LocalDateTime]] = {
    optional(text.verifying(errorCode, x => stringToLocalDateTimeOpt(x).isDefined))
      .transform[Option[LocalDateTime]](_.flatMap(stringToLocalDateTimeOpt), _.map(_.format(`yyyy-MM-dd`)))
  }
}

object DisplayableTeamMembers {


  def apply(teamName: String, umpProfileBaseUrl: String, teamMembers: Seq[TeamMember]): Seq[DisplayableTeamMember] = {

    val displayableTeamMembers = teamMembers.map(tm =>
      DisplayableTeamMember(
        displayName = tm.displayName.getOrElse("DISPLAY NAME NOT PROVIDED"),
        isServiceOwner = tm.serviceOwnerFor.map(_.map(_.toLowerCase)).exists(_.contains(teamName.toLowerCase)),
        umpLink = tm.username.map(x => s"${umpProfileBaseUrl.appendSlash}$x").getOrElse("USERNAME NOT PROVIDED")
      )
    )

    val (serviceOwners, others) = displayableTeamMembers.partition(_.isServiceOwner)
    serviceOwners.sortBy(_.displayName) ++ others.sortBy(_.displayName)
  }

  case class DisplayableTeamMember(displayName: String,
                                   isServiceOwner: Boolean = false,
                                   umpLink: String)

}
