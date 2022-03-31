/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.auth

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.cataloguefrontend.CatalogueFrontendSwitches
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, Call, MessagesControllerComponents, MessagesRequest, Result, Request}

trait CatalogueAuthBuilders {

  def auth: FrontendAuthComponents
  def ec: ExecutionContext
  def mcc: MessagesControllerComponents

  def BasicAuthAction =
    new ActionBuilder[MessagesRequest[*], AnyContent] {
      override def executionContext: ExecutionContext = ec
      override def parser: BodyParser[AnyContent] = mcc.parsers.anyContent
      override def invokeBlock[A](request: Request[A], block: MessagesRequest[A] => Future[Result]): Future[Result] =
        if (CatalogueFrontendSwitches.requiresLogin.isEnabled)
          auth
            .authenticatedAction(continueUrl = AuthController.continueUrl(Call("GET", request.uri))) // Other HTTP methods are not suported in targetUrl
            .invokeBlock[A](request, ar => block(new MessagesRequest[A](ar.request, mcc.messagesApi)))
        else
          block(new MessagesRequest[A](request, mcc.messagesApi))
    }
}
