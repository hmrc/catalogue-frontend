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

package uk.gov.hmrc.cataloguefrontend.platforminitiatives

import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.platforminitiatives.html.PlatformInitiativesListPage
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PlatformInitiativesController @Inject()
( mcc                           : MessagesControllerComponents
, platformInitiativesConnector  : PlatformInitiativesConnector
, platformInitiativesListPage   : PlatformInitiativesListPage
, teamsAndRepositoriesConnector : TeamsAndRepositoriesConnector
)(implicit val ec: ExecutionContext)
  extends FrontendController(mcc) {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  def platformInitiatives(display: DisplayType, team: Option[TeamName]): Action[AnyContent] = Action.async { implicit request =>
    val boundForm = PlatformInitiativesFilter.form.bindFromRequest()
      boundForm.fold(
        formWithErrors =>
          for {
            allTeams <- teamsAndRepositoriesConnector.allTeams
          } yield BadRequest(platformInitiativesListPage
            (initiatives  = Seq()
            , display     = display
            ,  team       = None
            ,  allTeams   = allTeams
            ,  formWithErrors
            )),
          query =>
              for {
                  allTeams <- teamsAndRepositoriesConnector.allTeams
                  initiatives <- platformInitiativesConnector.getInitiatives(query.team)
                } yield Ok(platformInitiativesListPage(
                  initiatives   = initiatives,
                  display       = display,
                  team          = team,
                  allTeams      = allTeams,
                boundForm
                )))
              }
    }

  case class PlatformInitiativesFilter(initiativeName: Option[String] = None, team: Option[String]) {
    def isEmpty: Boolean = initiativeName.isEmpty && team.isEmpty
  }

  object PlatformInitiativesFilter {
    lazy val form: Form[PlatformInitiativesFilter] = Form(
      mapping(
        "initiativeName"  -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
        "team"            -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity)
        )
      (PlatformInitiativesFilter.apply)(PlatformInitiativesFilter.unapply)
    )
  }
