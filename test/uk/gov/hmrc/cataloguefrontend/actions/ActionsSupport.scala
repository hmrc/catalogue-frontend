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

import play.api.mvc.{ActionBuilder, ActionRefiner, AnyContent, BodyParser, MessagesControllerComponents, Request, Result}
import uk.gov.hmrc.cataloguefrontend.connector.model.Username
import uk.gov.hmrc.cataloguefrontend.connector.{UserManagementAuthConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.User
import uk.gov.hmrc.cataloguefrontend.service.CatalogueErrorHandler

import scala.concurrent.{ExecutionContext, Future}

trait ActionsSupport {
  import ExecutionContext.Implicits.global

  class UmpAuthenticatedPassThrough(
      umac                 : UserManagementAuthConnector
    , cc                   : MessagesControllerComponents
    , catalogueErrorHandler: CatalogueErrorHandler
    ) extends UmpAuthActionBuilder(umac, cc, catalogueErrorHandler) {

    override val whenAuthenticated = passThrough

    override def withGroup(group: String) = passThrough

    private def passThrough =
      new ActionBuilder[UmpAuthenticatedRequest, AnyContent]
        with ActionRefiner[Request, UmpAuthenticatedRequest] {

        def refine[A](request: Request[A]): Future[Either[Result, UmpAuthenticatedRequest[A]]] =
          Future(Right(UmpAuthenticatedRequest(
              request
            , token       = UserManagementAuthConnector.UmpToken("asdasdasd")
            , user        = User(username = Username("username"), groups = List.empty)
            , displayName = UserManagementConnector.DisplayName("displayname")
            )))

        override def parser: BodyParser[AnyContent] = cc.parsers.defaultBodyParser

        override protected def executionContext: ExecutionContext = cc.executionContext
      }
  }

  class VerifySignInStatusPassThrough(
      umac: UserManagementAuthConnector
    , cc  : MessagesControllerComponents
    ) extends VerifySignInStatus(umac, cc) {
    override def invokeBlock[A](request: Request[A], block: UmpVerifiedRequest[A] => Future[Result]): Future[Result] =
      block(UmpVerifiedRequest(request, cc.messagesApi, optUser = Some(User(username = Username("username"), groups = List.empty))))
  }
}
