/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.Action
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.DisplayName
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.UmpToken
import uk.gov.hmrc.cataloguefrontend.service.AuthService
import uk.gov.hmrc.cataloguefrontend.service.AuthService.TokenAndDisplayName
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.sign_in

import scala.concurrent.Future

@Singleton
class AuthController @Inject()(val messagesApi: MessagesApi, authService: AuthService, configuration: Configuration)
    extends FrontendController
    with I18nSupport {

  import AuthController.signinForm

  private val selfServiceUrl = {
    val key = "self-service-url"
    configuration.getString(key).getOrElse(throw new Exception(s"Expected to find $key"))
  }

  val showSignInPage = Action { implicit request =>
    Ok(sign_in(signinForm, selfServiceUrl))
  }

  val submit = Action.async { implicit request =>
    signinForm
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(sign_in(formWithErrors, selfServiceUrl))),
        signInData =>
          authService.authenticate(signInData.username, signInData.password).map {
            case Right(TokenAndDisplayName(UmpToken(token), DisplayName(displayName))) =>
              Redirect(routes.CatalogueController.landingPage())
                .withSession("ump.token" -> token, "ump.displayName" -> displayName)
            case Left(_) =>
              BadRequest(sign_in(signinForm.withGlobalError(Messages("sign-in.wrong-credentials")), selfServiceUrl))
        }
      )
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
