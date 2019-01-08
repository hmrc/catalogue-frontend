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

import javax.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.UmpToken
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendHeaderCarrierProvider
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.{ExecutionContext, Future}

final case class UmpVerifiedRequest[A](request: Request[A], override val messagesApi: MessagesApi, isSignedIn: Boolean)
    extends MessagesRequest[A](request, messagesApi)

@Singleton
class VerifySignInStatus @Inject()(
  userManagementAuthConnector: UserManagementAuthConnector,
  cc: MessagesControllerComponents
) extends ActionBuilder[UmpVerifiedRequest, AnyContent]
    with FrontendHeaderCarrierProvider {

  def invokeBlock[A](request: Request[A], block: UmpVerifiedRequest[A] => Future[Result]): Future[Result] = {
    implicit val headerCarrier: HeaderCarrier = hc(request)

    request.session.get(UmpToken.SESSION_KEY_NAME) match {
      case Some(token) =>
        userManagementAuthConnector.isValid(UmpToken(token)).flatMap { isValid =>
          block(UmpVerifiedRequest(request, cc.messagesApi, isValid))
        }
      case None =>
        block(UmpVerifiedRequest(request, cc.messagesApi, isSignedIn = false))
    }
  }

  override def parser: BodyParser[AnyContent] = cc.parsers.anyContent

  override protected def executionContext: ExecutionContext = cc.executionContext
}
