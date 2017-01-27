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

import play.api.Play.current
import play.api.data.Forms._
import play.api.data.{Form, Mapping}
import play.api.i18n.Messages.Implicits._
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.DisplayableTeamMembers.DisplayableTeamMember
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.{ConnectorError, TeamMember}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import views.html._

case class TeamActivityDates(firstActive: Option[LocalDateTime], lastActive: Option[LocalDateTime], firstServiceCreationDate : Option[LocalDateTime])


object CatalogueController extends CatalogueController {

  override def userManagementConnector: UserManagementConnector = UserManagementConnector

  override def teamsAndServicesConnector: TeamsAndRepositoriesConnector = TeamsAndRepositoriesConnector

  override def indicatorsConnector: IndicatorsConnector = IndicatorsConnector

  override def deploymentsService: DeploymentsService = new DeploymentsService(ServiceDeploymentsConnector, TeamsAndRepositoriesConnector)
}

trait CatalogueController extends FrontendController with UserManagementPortalLink {

  val profileBaseUrlConfigKey = "user-management.profileBaseUrl"

  val repotypeToDetailsUrl = Map(
    RepoType.Deployable -> routes.CatalogueController.service _,
    RepoType.Other -> routes.CatalogueController.repository _,
    RepoType.Library -> routes.CatalogueController.library _
  )

  def userManagementConnector: UserManagementConnector

  def teamsAndServicesConnector: TeamsAndRepositoriesConnector

  def indicatorsConnector: IndicatorsConnector

  def deploymentsService: DeploymentsService

  def landingPage() = Action { request =>
    Ok(landing_page())
  }

  def allTeams() = Action.async { implicit request =>
    teamsAndServicesConnector.allTeams.map { response =>
      Ok(
        teams_list(
          response.formattedTimestamp,
          response.data.sortBy(_.name.toUpperCase)))
    }
  }


  def team(teamName: String) = Action.async { implicit request =>

    val eventualTeamInfo = teamsAndServicesConnector.teamInfo(teamName)
    val eventualErrorOrMembers = userManagementConnector.getTeamMembers(teamName)
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
    for {
      service <- teamsAndServicesConnector.repositoryDetails(name)
      maybeDataPoints <- indicatorsConnector.deploymentIndicatorsForService(name)
    } yield service match {
      case Some(s) if s.data.repoType == RepoType.Deployable => Ok(
        service_info(
          s.formattedTimestamp,
          s.data,
          ServiceChartData.deploymentThroughput(name, maybeDataPoints.map(_.throughput)),
          ServiceChartData.deploymentStability(name, maybeDataPoints.map(_.stability)),
          s.data.createdAt
        ))
      case _ => NotFound
    }
  }

  def library(name: String) = Action.async { implicit request =>
    for {
      library <- teamsAndServicesConnector.repositoryDetails(name)
    } yield library match {
      case Some(s) if s.data.repoType == RepoType.Library =>
        Ok(
          library_info(
            s.formattedTimestamp,
            s.data))
      case _ => NotFound
    }
  }

  def repository(name: String) = Action.async { implicit request =>
    for {
      repository <- teamsAndServicesConnector.repositoryDetails(name)
    } yield repository match {
      case Some(s) =>
        Ok(
          repository_info(
            s.formattedTimestamp,
            s.data))
      case _ => NotFound
    }
  }

  def allServices = Action {
    Redirect("/repositories?name=&type=Service")
  }

  def allLibraries = Action {
    Redirect("/repositories?name=&type=Library")
  }

  def allRepositories() = Action.async{ implicit request =>
    import SearchFiltering._

    teamsAndServicesConnector.allRepositories.map { repositories =>
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

  def deployments() = Action.async { implicit request =>

    import SearchFiltering._

    deploymentsService.getDeployments().map { rs =>

      val form: Form[DeploymentsFilter] = DeploymentsFilter.form.bindFromRequest()
      form.fold(
        errors => Ok(release_list(Nil, errors)),
        query => Ok(release_list(rs.filter(query), form))
      )

    }

  }

  private def convertToDisplayableTeamMembers(teamName: String, errorOrTeamMembers: Either[ConnectorError, Seq[TeamMember]]): Either[ConnectorError, Seq[DisplayableTeamMember]] =

    errorOrTeamMembers match {
      case Left(err) => Left(err)
      case Right(tms) =>
        Right(DisplayableTeamMembers(teamName, getConfString(profileBaseUrlConfigKey, "#"), tms))
    }


}

case class DeploymentsFilter(team: Option[String] = None, serviceName: Option[String] = None, from: Option[LocalDateTime] = None, to: Option[LocalDateTime] = None) {
  def isEmpty: Boolean = team.isEmpty && serviceName.isEmpty && from.isEmpty && to.isEmpty
}

case class RepoListFilter(name : Option[String] = None, repoType : Option[String] = None) {
  def isEmpty = name.isEmpty && repoType.isEmpty
}

object RepoListFilter{
  lazy val form = Form (
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
      .transform[Option[LocalDateTime]](_.flatMap(stringToLocalDateTimeOpt), _.map(_.format(`dd-MM-yyyy`)))
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
