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

package uk.gov.hmrc.cataloguefrontend.actions

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.UmpToken
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

final case class UmpVerifiedRequest[A](
  request: Request[A],
  isSignedIn: Boolean
) extends WrappedRequest[A](request)

@Singleton
class VerifySignInStatus @Inject()(userManagementAuthConnector: UserManagementAuthConnector)
    extends ActionBuilder[UmpVerifiedRequest] {

  def invokeBlock[A](request: Request[A], block: UmpVerifiedRequest[A] => Future[Result]): Future[Result] = {
    implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    request.session.get(UmpToken.SESSION_KEY_NAME) match {
      case Some(token) =>
        userManagementAuthConnector.isValid(UmpToken(token)).flatMap { isValid =>
          block(UmpVerifiedRequest(request, isValid))
        }
      case None =>
        block(UmpVerifiedRequest(request, isSignedIn = false))
    }
  }

}
