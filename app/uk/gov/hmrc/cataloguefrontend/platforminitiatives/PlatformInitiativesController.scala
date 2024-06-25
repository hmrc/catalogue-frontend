/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.data.{Form, Forms}
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.TeamName
import uk.gov.hmrc.cataloguefrontend.platforminitiatives.html.PlatformInitiativesListPage
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PlatformInitiativesController @Inject()
( override val mcc              : MessagesControllerComponents
, platformInitiativesConnector  : PlatformInitiativesConnector
, platformInitiativesListPage   : PlatformInitiativesListPage
, teamsAndRepositoriesConnector : TeamsAndRepositoriesConnector
, override val auth             : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  def platformInitiatives(display: DisplayType, team: Option[TeamName]): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      val boundForm = PlatformInitiativesFilter.form.bindFromRequest()
      boundForm.fold(
        formWithErrors =>
          for
            allTeams <- teamsAndRepositoriesConnector.allTeams()
          yield BadRequest(platformInitiativesListPage(
            initiatives = Seq.empty,
            display     = display,
            team        = None,
            allTeams    = allTeams,
            formWithErrors
          )),
        query =>
          for
            allTeams <- teamsAndRepositoriesConnector.allTeams()
            initiatives <- platformInitiativesConnector.getInitiatives(query.team)
          yield Ok(platformInitiativesListPage(
            initiatives = initiatives,
            display     = display,
            team        = team,
            allTeams    = allTeams,
            boundForm
          ))
      )
  }

end PlatformInitiativesController

case class PlatformInitiativesFilter(
  initiativeName: Option[String] = None,
  team          : Option[TeamName]
):
  def isEmpty: Boolean =
    initiativeName.isEmpty && team.isEmpty

object PlatformInitiativesFilter {
  lazy val form: Form[PlatformInitiativesFilter] =
    Form(
      mapping(
        "initiativeName" -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
        "team"           -> optional(Forms.of[TeamName](TeamName.formFormat))
        )
      (PlatformInitiativesFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
    )
}
