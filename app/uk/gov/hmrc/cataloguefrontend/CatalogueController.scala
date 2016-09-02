/*
 * Copyright 2016 HM Revenue & Customs
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


import java.time.{LocalDate, LocalDateTime}
import java.util.Date

import play.api.data.Forms._
import play.api.data.Form
import play.api.{Play, Configuration}
import play.api.mvc._
import uk.gov.hmrc.play.frontend.controller.FrontendController
import views.html._

import scala.concurrent.Future

object CatalogueController extends CatalogueController {
  override def teamsAndServicesConnector: TeamsAndServicesConnector = TeamsAndServicesConnector

  override def indicatorsConnector: IndicatorsConnector = IndicatorsConnector
}

trait CatalogueController extends FrontendController {

  def teamsAndServicesConnector: TeamsAndServicesConnector

  def indicatorsConnector: IndicatorsConnector


  def landingPage() = Action { request =>
    Ok(landing_page())
  }

  def allTeams() = Action.async { implicit request =>
    teamsAndServicesConnector.allTeams.map { response =>
      Ok(teams_list(response.time, response.data.sortBy(_.toUpperCase)))
    }
  }

  def team(teamName: String) = Action.async { implicit request =>
    for {
      typeRepos <- teamsAndServicesConnector.teamInfo(teamName)
    } yield typeRepos match {
      case Some(s) => Ok(team_info(s.time, teamName, repos = s.data, teamMembersLink = UserManagementPortalLink(teamName, Play.current.configuration)))
      case None => NotFound
    }
  }

  def service(name: String) = Action.async { implicit request =>
    for {
      service <- teamsAndServicesConnector.repositoryDetails(name)
      indicators <- indicatorsConnector.fprForService(name)
    } yield service match {
      case Some(s) if s.data.repoType == RepoType.Deployable => Ok(service_info(s.time, s.data, indicators.map(_.map(_.toJSString))))
      case _ => NotFound
    }
  }

  def library(name: String) = Action.async { implicit request =>
    for {
      library <- teamsAndServicesConnector.repositoryDetails(name)
    } yield library match {
      case Some(s) if s.data.repoType == RepoType.Library => Ok(library_info(s.time, s.data))
      case _ => NotFound
    }
  }

  def allServiceNames() = Action.async { implicit request =>
    teamsAndServicesConnector.allServiceNames.map { services =>
      Ok(services_list(services.time, repositories = services.data))
    }
  }

  def allLibraryNames() = Action.async { implicit request =>
    teamsAndServicesConnector.allLibraryNames.map { libraries =>
      Ok(library_list(libraries.time, repositories = libraries.data))
    }
  }

  def releases() = Action.async { implicit request =>

    Future.successful(Ok(ReleasesFilter.form.bindFromRequest().value.toString))
  }
}

case class ReleasesFilter(serviceName: Option[String], from: Option[Date], to: Option[Date])

object ReleasesFilter {

  val form = Form(
    mapping(
      "serviceName" -> optional(text).transform[Option[String]](x => if (x.exists(_.trim.isEmpty)) None else x, identity),
      "from" -> optional(date("dd-MM-yyyy")),
      "to" -> optional(date("dd-MM-yyyy"))
    )(ReleasesFilter.apply)(ReleasesFilter.unapply)
  )

}

object UserManagementPortalLink {

  def apply(teamName: String, config: Configuration): String = {

    s"${config.getString("usermanagement.portal.url").fold("#")(x => s"$x/$teamName")}"

  }

}
