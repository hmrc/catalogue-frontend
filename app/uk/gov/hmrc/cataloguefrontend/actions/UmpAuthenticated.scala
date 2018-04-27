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
import play.api.mvc.{Action, ActionFunction, AnyContent, Request, Result}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.UmpToken
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import play.api.mvc.Results._

import scala.concurrent.Future

@Singleton
class UmpAuthenticated @Inject()(userManagementAuthConnector: UserManagementAuthConnector)
    extends ActionFunction[Request, Request] {

  def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
    implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    request.session.get(UmpToken.SESSION_KEY_NAME) match {
      case Some(token) =>
        userManagementAuthConnector.isValid(UmpToken(token)).flatMap {
          case true  => block(request)
          case false => Future.successful(NotFound)
        }

      case None =>
        Future.successful(NotFound)
    }
  }

  def async(block: Request[AnyContent] => Future[Result]): Action[AnyContent] =
    Action.andThen(this).async(block)
}
