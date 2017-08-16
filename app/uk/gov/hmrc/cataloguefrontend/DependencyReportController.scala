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
import uk.gov.hmrc.cataloguefrontend.DisplayableTeamMember._
import uk.gov.hmrc.cataloguefrontend.RepoType.RepoType
import uk.gov.hmrc.cataloguefrontend.TeamsAndRepositoriesConnector.TeamsAndRepositoriesError
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.{TeamMember, UMPError}
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependencies, Version}
import uk.gov.hmrc.cataloguefrontend.connector.{DeploymentIndicators, IndicatorsConnector, ServiceDependenciesConnector}
import uk.gov.hmrc.cataloguefrontend.events._
import uk.gov.hmrc.cataloguefrontend.service.DeploymentsService
import uk.gov.hmrc.play.frontend.controller.FrontendController
import views.html._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.Random


object DependencyReportController extends DependencyReportController with MongoDbConnection {

  override def userManagementConnector: UserManagementConnector = UserManagementConnector

  override def teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector = TeamsAndRepositoriesConnector

  override def indicatorsConnector: IndicatorsConnector = IndicatorsConnector

  lazy override val deploymentsService: DeploymentsService = new DeploymentsService(ServiceDeploymentsConnector, TeamsAndRepositoriesConnector)

  lazy override val eventService = new DefaultEventService(new MongoEventRepository(db))

  lazy override val readModelService = new DefaultReadModelService(eventService, UserManagementConnector)

  lazy override val serviceDependencyConnector: ServiceDependenciesConnector = ServiceDependenciesConnector
}

trait DependencyReportController extends FrontendController with UserManagementPortalLink {

  val profileBaseUrlConfigKey = "user-management.profileBaseUrl"

  val repotypeToDetailsUrl = Map(
    RepoType.Service -> routes.CatalogueController.service _,
    RepoType.Other -> routes.CatalogueController.repository _,
    RepoType.Library -> routes.CatalogueController.library _,
    RepoType.Prototype -> routes.CatalogueController.prototype _
  )

  def userManagementConnector: UserManagementConnector

  def teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector

  def serviceDependencyConnector: ServiceDependenciesConnector

  def indicatorsConnector: IndicatorsConnector

  def deploymentsService: DeploymentsService

  def eventService: EventService

  def readModelService: ReadModelService


  case class DependencyReport(repository: String,               // teamsAndRepositoriesConnector.allRepositories
                              repoType: String,                 // teamsAndRepositoriesConnector.allRepositories
                              team: String,                     // findTeamNames(_,_)
                              digitalService: String,           // findDigitalServiceName
                              dependencyName: String,           //
                              dependencyType: String,           //
                              currentVersion: String,          //
                              latestVersion: String)                   //

  implicit val drFormat = Json.format[DependencyReport]

  def dependencyReport() = Action.async { implicit request =>


    type RepoName = String

    val allTeamsF = teamsAndRepositoriesConnector.allTeams.map(_.data).flatMap(teams => Future.sequence(teams.map(team => teamsAndRepositoriesConnector.teamInfo(team.name).map(_.map(_.data))))).map(_.flatten)
    val digitalServicesF = teamsAndRepositoriesConnector.allDigitalServices.map(_.data).flatMap { digitalServices =>
      Future.sequence {
        digitalServices.map(teamsAndRepositoriesConnector.digitalServiceInfo)
      }.map(errorsOrDigitalServices => errorsOrDigitalServices.map(errorOrDigitalService => errorOrDigitalService.right.map(_.data)))
    }


    val x: Future[Seq[DependencyReport]] = for {
      allRepos: Seq[RepositoryDisplayDetails] <- teamsAndRepositoriesConnector.allRepositories.map(_.data)
      dependencies <- Future.sequence(allRepos.map(repo => serviceDependencyConnector.getDependencies(repo.name))).map(_.flatten)
      allTeams <- allTeamsF
      digitalServices <- digitalServicesF
    } yield {
      dependencies.flatMap { (dependencies: Dependencies) =>
        val repoName = dependencies.repositoryName
        
        dependencies.libraryDependenciesState.map(d =>
          DependencyReport(repoName,
            getRepositoryType(repoName, allRepos),
            findTeamNames(repoName, allTeams).mkString(";"),
            findDigitalServiceName(repoName, digitalServices),
            d.libraryName,
            "library",
            d.currentVersion.toString,
            d.latestVersion.getOrElse("Unknown").toString
          ))

      }

    }




//    val x: Future[Seq[Map[String, Seq[String]]]] = for {
//      teamsInfo: Seq[Team] <- teamsInfoF
//      digitalServices: Seq[Either[TeamsAndRepositoriesError, DigitalService]] <- digitalServicesF
//    } yield {
//      val teamsWithRepos = teamsInfo.filter(_.repos.isDefined)
//      val seq1 = teamsWithRepos.map(team => team.repos.getOrElse(Map.empty))
//      println(seq1)
//
//      seq1
//
//    }

    x.map(r => Ok(Json.toJson(r)))
  }

  def getRepositoryType(repositoryName:String, allRepos: Seq[RepositoryDisplayDetails]): String =
    allRepos.find(r => r.name == repositoryName).map(_.repoType).getOrElse("Unknown").toString

  def findTeamNames(repositoryName: String, teams: Seq[Team]): Seq[String] =
    teams.filter(_.repos.isDefined).filter(team => team.repos.get.values.flatten.toSeq.contains(repositoryName)).map(_.name)

  def findDigitalServiceName(repositoryName: String, errorsOrDigitalServices: Seq[Either[TeamsAndRepositoriesError, DigitalService]]): String =
    errorsOrDigitalServices
      .filter(errorOrDigitalService => errorOrDigitalService.isRight)
      .find(_.right.get.repositories.exists(_.name == repositoryName))
      .map(_.right.get.name).getOrElse("Unknown")










  ///////////////////////

  def serviceOwner(digitalService: String) = Action {
    readModelService.getDigitalServiceOwner(digitalService).fold(NotFound(Json.toJson(s"owner for $digitalService not found")))(ds => Ok(Json.toJson(ds)))
  }

  def saveServiceOwner() = Action.async { implicit request =>

    request.body.asJson.map { payload =>
      val serviceOwnerSaveEventData: ServiceOwnerSaveEventData = payload.as[ServiceOwnerSaveEventData]
      val serviceOwnerDisplayName: String = serviceOwnerSaveEventData.displayName
      val maybeTeamMember: Option[TeamMember] = readModelService.getAllUsers.find(_.displayName.getOrElse("") == serviceOwnerDisplayName)

      maybeTeamMember.fold {
        Future(NotAcceptable(Json.toJson(s"Invalid user: $serviceOwnerDisplayName")))
      } { member =>
        member.username.fold(Future.successful(ExpectationFailed(Json.toJson(s"Username was not set (by UMP) for $member!")))) {
          serviceOwnerUsername =>
            eventService.saveServiceOwnerUpdatedEvent(ServiceOwnerUpdatedEventData(serviceOwnerSaveEventData.service, serviceOwnerUsername))
              .map(_ => {
                val string = getConfString(profileBaseUrlConfigKey, "#")
                Ok(Json.toJson(DisplayableTeamMember(member, string)))
              })
        }
      }
    }.getOrElse(Future.successful(BadRequest(Json.toJson( s"""Unable to parse json: "${request.body.asText.getOrElse("No text in request body!")}""""))))

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

    digitalServiceDetails.value.map(d =>
      Ok(digital_service_info(digitalServiceName, d, readModelService.getDigitalServiceOwner(digitalServiceName).map(DisplayableTeamMember(_, getConfString(profileBaseUrlConfigKey, "#")))))
    )
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
    val dependenciesF = serviceDependencyConnector.getDependencies(name)

    for {
      service <- repositoryDetailsF
      maybeDataPoints <- deploymentIndicatorsForServiceF
      serviceDeploymentInformation: Either[Throwable, ServiceDeploymentInformation] <- serviceDeploymentInformationF
      mayBeDependencies <- dependenciesF
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
            mayBeDependencies,
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
      mayBeDependencies <- serviceDependencyConnector.getDependencies(name)
    } yield library match {
      case Some(s) if s.data.repoType == RepoType.Library =>
        Ok(
          library_info(
            s.formattedTimestamp,
            s.data,
            mayBeDependencies))
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
      mayBeDependencies <- serviceDependencyConnector.getDependencies(name)
    } yield (repository, indicators) match {
      case (Some(s), maybeDataPoints) =>
        Ok(
          repository_info(
            s.formattedTimestamp,
            s.data,
            mayBeDependencies,
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









