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


import play.api.{Play, Configuration}
import play.api.mvc._
import uk.gov.hmrc.play.frontend.controller.FrontendController
import views.html._

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

  def teamServiceNames(teamName: String) = Action.async { implicit request =>
    teamsAndServicesConnector.teamServiceNames(teamName).map { services =>
      Ok(team(services.time, teamName, services = services.data, teamMembersLink = UserManagementPortalLink(teamName, Play.current.configuration)))
    }
  }

  def service(name: String) = Action.async { implicit request =>
    val showIndicators = request.getQueryString("indicators").isDefined

    for {
      service <- teamsAndServicesConnector.service(name)
      indicators <- indicatorsConnector.fprForService(name)
    } yield service match {
      case Some(s) => Ok(service_info(s.time, s.data, indicators.map(_.map(_.toJSString)), showIndicators))
      case None => NotFound
    }
  }

  def allServiceNames() = Action.async { implicit request =>
    teamsAndServicesConnector.allServiceNames.map { services =>
      Ok(services_list(services.time, services = services.data))
    }
  }
}

object UserManagementPortalLink {

  def apply(teamName: String, config: Configuration): String = {

    s"${config.getString("usermanagement.portal.url").fold("#")(x => s"$x/$teamName")}"

  }

}
