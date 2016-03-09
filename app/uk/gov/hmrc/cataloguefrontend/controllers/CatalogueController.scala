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

package uk.gov.hmrc.cataloguefrontend.controllers

import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.connector.CatalogueConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import views.html.{catalogue_main, team}

object CatalogueController extends CatalogueController {
  override def catalogueConnector: CatalogueConnector = CatalogueConnector
}

trait CatalogueController extends FrontendController {

  def catalogueConnector: CatalogueConnector

  def allTeams() = Action.async { implicit request =>
    catalogueConnector.allTeams.map { s =>
      Ok(catalogue_main(teams = s.map(_.teamName).sortBy(_.toUpperCase)))
    }
  }

  def teamServices(teamName: String) = Action.async { implicit request =>
    catalogueConnector.teamServices(teamName).map { services =>

      Ok(team(teamName = teamName, services = services))

    }
  }
}
