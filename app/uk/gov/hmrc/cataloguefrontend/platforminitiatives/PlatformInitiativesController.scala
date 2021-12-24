/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.platforminitiatives.html.PlatformInitiativesListPage
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlatformInitiativesController @Inject()
( mcc                           : MessagesControllerComponents
, platformInitiativesConnector  : PlatformInitiativesConnector
, platformInitiativesListPage   : PlatformInitiativesListPage
, teamsAndRepositoriesConnector : TeamsAndRepositoriesConnector
)(implicit val ec: ExecutionContext)
  extends FrontendController(mcc) {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  def platformInitiatives(team: String = "", display: DisplayType): Action[AnyContent] = Action.async { implicit request =>
    PlatformInitiativesFilter.form
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(Ok(platformInitiativesListPage
        (initiatives  = Seq()
        , display     = display
        ,  team       = team
        ,  teams      = Seq()
        ,  formWithErrors
        ))),
        _ =>
        request.uri match {
            case uri if uri.contains("/platform-initiatives/") => {
              for {
                initiatives <- platformInitiativesConnector.teamInitiatives(team)
                teams <- teamsAndRepositoriesConnector.allTeams
              } yield Ok(platformInitiativesListPage(
                initiatives   = initiatives,
                display       = display,
                team          = team,
                teams         = teams.map(_.name.asString).distinct.sorted,
                PlatformInitiativesFilter.form.bindFromRequest()
              ))
            }
            case uri if uri.contains("?team=") => {
              val team = request.uri.replace("/platform-initiatives?team=", "")
              for {
                initiatives <- platformInitiativesConnector.teamInitiatives(team)
                teams <- teamsAndRepositoriesConnector.allTeams
              } yield Ok(platformInitiativesListPage(
                initiatives   = initiatives,
                display       = display,
                team          = team,
                teams         = teams.map(_.name.asString).distinct.sorted,
                PlatformInitiativesFilter.form.bindFromRequest()
              ))
            }
            case _ => {
              for {
                initiatives <- platformInitiativesConnector.allInitiatives
                teams <- teamsAndRepositoriesConnector.allTeams
              } yield Ok(platformInitiativesListPage(
                initiatives   = initiatives,
                display       = display,
                team          = team,
                teams         = teams.map(_.name.asString).distinct.sorted,
                PlatformInitiativesFilter.form.bindFromRequest()
              ))
            }
          }
      )
  }

  case class PlatformInitiativesFilter(initiativeName: Option[String] = None) {
    def isEmpty: Boolean = initiativeName.isEmpty
  }

  object PlatformInitiativesFilter {
    lazy val form: Form[PlatformInitiativesFilter] = Form(
      mapping("initiativeName" -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity))
      (PlatformInitiativesFilter.apply)(PlatformInitiativesFilter.unapply)
    )
  }
}
