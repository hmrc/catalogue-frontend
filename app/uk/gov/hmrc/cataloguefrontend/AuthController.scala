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

package uk.gov.hmrc.cataloguefrontend

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Call, MessagesControllerComponents}
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, Retrieval}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl._
import uk.gov.hmrc.play.bootstrap.binders.{OnlyRelative, RedirectUrl}

@Singleton
class AuthController @Inject() (
  auth: FrontendAuthComponents,
  mcc : MessagesControllerComponents
) extends FrontendController(mcc) {
  import AuthController._

  def signIn(targetUrl: Option[RedirectUrl]) =
    auth.authenticatedAction(
      continueUrl = routes.AuthController.postSignIn(sanitize(targetUrl))
    )(Redirect(routes.AuthController.postSignIn(sanitize(targetUrl))))

  // endpoint exists to run retrievals and store the results in the session after logging in
  // (opposed to running retrievals on every page and make results available to standard_layout)
  def postSignIn(targetUrl: Option[RedirectUrl]) =
    auth.authenticatedAction(
      continueUrl = routes.AuthController.signIn(sanitize(targetUrl)),
      retrieval   = Retrieval.username
    ){ implicit request =>
      Redirect(
        targetUrl.flatMap(_.getEither(OnlyRelative).toOption)
          .fold(routes.CatalogueController.index.url)(_.url)
      )
      .addingToSession(
        AuthController.SESSION_USERNAME -> request.retrieval.value
      )
    }

  val signOut =
    Action(
      Redirect(routes.CatalogueController.index).withNewSession
    )
}

object AuthController {
  val SESSION_USERNAME = "username"

    // to avoid cyclical urls
  private[cataloguefrontend] def sanitize(targetUrl: Option[RedirectUrl]): Option[RedirectUrl] = {
    val avoid = List(
      routes.AuthController.signIn(None),
      routes.AuthController.postSignIn(None)
    )
    targetUrl.filter(ru => !avoid.exists(a => ru.unsafeValue.startsWith(a.url)))
  }

  def continueUrl(targetUrl: Call): Call =
    routes.AuthController.postSignIn(sanitize(Some(RedirectUrl(targetUrl.url))))
}
