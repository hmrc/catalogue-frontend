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

package uk.gov.hmrc.cataloguefrontend.actions

import cats.data.OptionT
import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.{ routes => appRoutes }
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.UmpToken
import uk.gov.hmrc.http.Token
import uk.gov.hmrc.play.HeaderCarrierConverter
import play.api.mvc.Results._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

final case class UmpAuthenticatedRequest[A](request: Request[A], token: Token)
  extends WrappedRequest(request)


/** Creates an Action will only proceed to invoke the action body, if there is a valid [[UmpToken]] in session.
  * If there isn't, it will short circuit with a Redirect to SignIn page.
  *
  * Use [[VerifySignInStatus]] Action if you want to know if there is a valid token, but it should not terminate invocation.
  */
@Singleton
class UmpAuthActionBuilder @Inject()(
  userManagementAuthConnector: UserManagementAuthConnector,
  cc                         : MessagesControllerComponents
)(implicit val ec: ExecutionContext) {

  val whenAuthenticated =
    new ActionBuilder[UmpAuthenticatedRequest, AnyContent] {

      def invokeBlock[A](request: Request[A], block: UmpAuthenticatedRequest[A] => Future[Result]): Future[Result] = {
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)
        OptionT(
            request.session.get(UmpToken.SESSION_KEY_NAME)
              .filterA(token => userManagementAuthConnector.isValid(UmpToken(token)))
          )
          .semiflatMap(token => block(UmpAuthenticatedRequest(request, token = Token(token))))
          .getOrElse(Redirect(appRoutes.AuthController.showSignInPage(targetUrl = Some(request.target.uriString).filter(_ => request.method == "GET"))))
      }

      override def parser: BodyParser[AnyContent] = cc.parsers.defaultBodyParser

      override protected def executionContext: ExecutionContext = cc.executionContext
    }

  def withGroup(group: String) =
    new ActionBuilder[UmpAuthenticatedRequest, AnyContent] {

      def invokeBlock[A](request: Request[A], block: UmpAuthenticatedRequest[A] => Future[Result]): Future[Result] = {
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)
        OptionT(
            request.session.get(UmpToken.SESSION_KEY_NAME)
              .filterA(token => userManagementAuthConnector.hasGroup(UmpToken(token), group))
          )
          .semiflatMap(token => block(UmpAuthenticatedRequest(request, token = Token(token))))
          .getOrElse(Forbidden(""))
      }

      override def parser: BodyParser[AnyContent] = cc.parsers.defaultBodyParser

      override protected def executionContext: ExecutionContext = cc.executionContext
    }
}
