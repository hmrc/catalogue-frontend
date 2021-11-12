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

package uk.gov.hmrc.cataloguefrontend

import javax.inject.{Inject, Singleton}
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.DisplayName
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, Retrieval}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

@Singleton
class AuthController @Inject() (
  auth: FrontendAuthComponents,
  mcc : MessagesControllerComponents
) extends FrontendController(mcc) {

  def signIn(targetUrl: Option[String]) =
    // TODO provide this from internal-auth-client?
    Action(
      Redirect(
        "/internal-auth-frontend/sign-in",
        Map("continue_url" -> Seq(routes.AuthController.postSignIn(targetUrl).url))
      )
    )

  // TODO consider updating internal-auth to add username to the session
  def postSignIn(targetUrl: Option[String]) =
    auth.authenticatedAction(
      continueUrl = routes.AuthController.signIn(targetUrl),
      retrieval   = Retrieval.username /// TODO previously this was "Display Name" rather than "user.name"
    ){ request =>
      Redirect(targetUrl.getOrElse(routes.CatalogueController.index.url))
      .withSession(
        DisplayName.SESSION_KEY_NAME -> request.retrieval.value
      )
    }

  val signOut =
    Action(
      Redirect(routes.CatalogueController.index).withNewSession
    )
}
