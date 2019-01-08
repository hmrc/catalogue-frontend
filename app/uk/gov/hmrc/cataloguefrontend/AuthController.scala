/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.DisplayName
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.UmpToken
import uk.gov.hmrc.cataloguefrontend.service.AuthService
import uk.gov.hmrc.cataloguefrontend.service.AuthService.TokenAndDisplayName
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.sign_in

import scala.concurrent.Future

@Singleton
class AuthController @Inject()(
  authService: AuthService,
  configuration: Configuration,
  mcc: MessagesControllerComponents
) extends FrontendController(mcc) {

  import AuthController.signinForm

  private[this] val selfServiceUrl = configuration.get[String]("self-service-url")

  val showSignInPage: Action[AnyContent] = Action { implicit request =>
    Ok(sign_in(signinForm, selfServiceUrl))
  }

  val submit: Action[AnyContent] = Action.async { implicit request =>
    signinForm
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(sign_in(formWithErrors, selfServiceUrl))),
        signInData =>
          authService.authenticate(signInData.username, signInData.password).map {
            case Right(TokenAndDisplayName(UmpToken(token), DisplayName(displayName))) =>
              Redirect(routes.CatalogueController.index())
                .withSession(
                  UmpToken.SESSION_KEY_NAME    -> token,
                  DisplayName.SESSION_KEY_NAME -> displayName
                )
            case Left(_) =>
              BadRequest(sign_in(signinForm.withGlobalError(Messages("sign-in.wrong-credentials")), selfServiceUrl))
        }
      )
  }

  val signOut = Action {
    Redirect(
      routes.AuthController.showSignInPage()
    ).withNewSession
  }
}

object AuthController {

  final case class SignInData(
    username: String,
    password: String
  )

  private val signinForm =
    Form(
      mapping(
        "username" -> text,
        "password" -> text
      )(SignInData.apply)(SignInData.unapply)
        .verifying(
          "sign-in.wrong-credentials",
          signInData => signInData.username.nonEmpty && signInData.password.nonEmpty
        )
    )

}
