/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.prcommenter

import javax.inject.Inject
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import views.html.prcommenter.PrCommenterRecommendationsPage

import scala.concurrent.ExecutionContext
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName

class PrCommenterController @Inject() (
  override val mcc : MessagesControllerComponents,
  prCommenterConnector         : PrCommenterConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  recommendationsPage          : PrCommenterRecommendationsPage,
  override val auth: FrontendAuthComponents
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  import play.api.data.{Form, Forms}

  case class Filter(team: Option[String], repo: Option[String], commentType: Option[String])

  lazy val form: Form[Filter] = Form(
    Forms.mapping(
      "name"        -> Forms.optional(Forms.text),
      "teamName"    -> Forms.optional(Forms.text),
      "commentType" -> Forms.optional(Forms.text)
    )(Filter.apply)(Filter.unapply)
  )

  def recommendations(name: Option[String], teamName: Option[String], commentType: Option[String]): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        teams        <- teamsAndRepositoriesConnector.allTeams
        repos        <- teamsAndRepositoriesConnector.allRepositories(team = teamName.map(TeamName.apply), name = name)
        reports      <- prCommenterConnector.search(
                          name        = None, // Use listjs filtering
                          teamName    = teamName.filter(_.nonEmpty),
                          commentType = commentType.filter(_.nonEmpty)
                        )
        commentTypes =  reports.flatMap(_.comments.map(_.commentType)).toSet
      } yield Ok(recommendationsPage(form.bindFromRequest(), teams, reports, commentTypes))
    }
}