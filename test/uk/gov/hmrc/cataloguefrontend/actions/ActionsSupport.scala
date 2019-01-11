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

import play.api.mvc.{MessagesControllerComponents, Request, Result}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector

import scala.concurrent.Future

trait ActionsSupport {

  class UmpAuthenticatedPassThrough(
    umac: UserManagementAuthConnector,
    cc: MessagesControllerComponents
  ) extends UmpAuthenticated(umac, cc) {
    override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] =
      block(request)
  }

  class VerifySignInStatusPassThrough(
    umac: UserManagementAuthConnector,
    cc: MessagesControllerComponents
  ) extends VerifySignInStatus(umac, cc) {
    override def invokeBlock[A](request: Request[A], block: UmpVerifiedRequest[A] => Future[Result]): Future[Result] =
      block(UmpVerifiedRequest(request, cc.messagesApi, isSignedIn = true))
  }

}
