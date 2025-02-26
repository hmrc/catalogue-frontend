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

import cats.data.EitherT
import play.api.data.{Form, Forms}
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, MessagesRequest, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, TeamName}
import uk.gov.hmrc.cataloguefrontend.platforminitiatives.view.html.PlatformInitiativesListPage
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlatformInitiativesController @Inject()
( override val mcc              : MessagesControllerComponents
, platformInitiativesConnector  : PlatformInitiativesConnector
, teamsAndRepositoriesConnector : TeamsAndRepositoriesConnector
, platformInitiativesListPage   : PlatformInitiativesListPage
, override val auth             : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  /**
    * @param team for reverse routing
    * @param digitalService for reverse routing
    */
  def platformInitiatives(display: DisplayType, team: Option[TeamName], digitalService: Option[DigitalService]): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      ( for
         teams           <- EitherT.right[Result](teamsAndRepositoriesConnector.allTeams())
         digitalServices <- EitherT.right[Result](teamsAndRepositoriesConnector.allDigitalServices())
         form            =  PlatformInitiativesFilter.form.bindFromRequest()
         filter          <- EitherT.fromEither[Future](form.fold(
                              formErrors => Left(BadRequest(platformInitiativesListPage(formErrors, display, teams, digitalServices, results = None)))
                            , formObject => Right(formObject)
                            ))
         results         <- EitherT.right[Result](platformInitiativesConnector.getInitiatives(filter.team, filter.digitalService))
        yield
          Ok(platformInitiativesListPage(form.fill(filter), display, teams, digitalServices, results = Some(results)))
      ).merge

end PlatformInitiativesController

case class PlatformInitiativesFilter(
  initiativeName : Option[String] = None,
  team           : Option[TeamName],
  digitalService : Option[DigitalService]
):
  def isEmpty: Boolean =
    initiativeName.isEmpty && team.isEmpty

object PlatformInitiativesFilter {
  lazy val form: Form[PlatformInitiativesFilter] =
    Form(
      mapping(
        "initiativeName" -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
        "team"           -> optional(Forms.of[TeamName]),
        "digitalService" -> optional(Forms.of[DigitalService])
        )
      (PlatformInitiativesFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
    )
}
