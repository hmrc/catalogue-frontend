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

package uk.gov.hmrc.cataloguefrontend.actions

import javax.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.{UmpToken, User}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendHeaderCarrierProvider

import scala.concurrent.{ExecutionContext, Future}

final case class UmpVerifiedRequest[A](request: Request[A], override val messagesApi: MessagesApi, optUser: Option[User])
    extends MessagesRequest[A](request, messagesApi) {
  def isSignedIn = optUser.isDefined
}

/** Creates an Action to check if there is a UmpToken, and if it is valid.
  * It will continue to invoke the action body, with a [[UmpVerifiedRequest]] representing this status.
  *
  * Use [[UmpAuthActionBuilder]] Action if it should only proceed when there is a valid UmpToken.
  */
@Singleton
class VerifySignInStatus @Inject()(
  userManagementAuthConnector: UserManagementAuthConnector,
  cc                         : MessagesControllerComponents
)(implicit val ec: ExecutionContext)
  extends ActionBuilder[UmpVerifiedRequest, AnyContent]
    with FrontendHeaderCarrierProvider {

  def invokeBlock[A](request: Request[A], block: UmpVerifiedRequest[A] => Future[Result]): Future[Result] = {
    implicit val headerCarrier: HeaderCarrier = hc(request)

    request.session.get(UmpToken.SESSION_KEY_NAME) match {
      case Some(token) =>
        userManagementAuthConnector.getUser(UmpToken(token)).flatMap { optUser =>
          block(UmpVerifiedRequest(request, cc.messagesApi, optUser))
        }
      case None =>
        block(UmpVerifiedRequest(request, cc.messagesApi, optUser = None))
    }
  }

  override def parser: BodyParser[AnyContent] = cc.parsers.anyContent

  override protected def executionContext: ExecutionContext = cc.executionContext
}
