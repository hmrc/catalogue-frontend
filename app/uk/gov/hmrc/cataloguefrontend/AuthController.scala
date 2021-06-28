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
import play.api.Configuration
import play.api.data.{Form, Forms}
import play.api.i18n.Messages
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.UmpToken
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.DisplayName
import uk.gov.hmrc.cataloguefrontend.service.AuthService
import uk.gov.hmrc.cataloguefrontend.service.AuthService.TokenAndDisplayName
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.sign_in
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthController @Inject() (authService: AuthService, configuration: Configuration, mcc: MessagesControllerComponents)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) {

  import AuthController._

  private[this] val selfServiceUrl = configuration.get[String]("self-service-url")

  def showSignInPage(targetUrl: Option[String]): Action[AnyContent] =
    Action { implicit request =>
      Ok(sign_in(signinForm.fill(SignInData(username = "", password = "", targetUrl = targetUrl)), selfServiceUrl))
    }

  val submit: Action[AnyContent] = Action.async { implicit request =>
    val boundForm = signinForm.bindFromRequest
    boundForm
      .fold(
        formWithErrors => Future.successful(BadRequest(sign_in(formWithErrors, selfServiceUrl))),
        signInData =>
          //Bug fix: User management accepts capitalised names for login. These capitalised names are then returned in the uid field
          //of token lookups. User names returned in other parts of the user management api are always lower case.
          authService
            .authenticate(signInData.username.toLowerCase, signInData.password)
            .map {
              case Right(TokenAndDisplayName(UmpToken(token), DisplayName(displayName))) =>
                val targetUrl = signInData.targetUrl.getOrElse(routes.CatalogueController.index.url)
                Redirect(targetUrl)
                  .withSession(
                    UmpToken.SESSION_KEY_NAME    -> token,
                    DisplayName.SESSION_KEY_NAME -> displayName
                  )
              case Left(_) =>
                BadRequest(sign_in(boundForm.withGlobalError(Messages("sign-in.wrong-credentials")), selfServiceUrl))
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
    password: String,
    targetUrl: Option[String]
  )

  private val signinForm =
    Form(
      Forms
        .mapping(
          "username"  -> Forms.text,
          "password"  -> Forms.text,
          "targetUrl" -> Forms.optional(Forms.text)
        )(SignInData.apply)(SignInData.unapply)
        .verifying(
          "sign-in.wrong-credentials",
          signInData => signInData.username.nonEmpty && signInData.password.nonEmpty
        )
    )
}
