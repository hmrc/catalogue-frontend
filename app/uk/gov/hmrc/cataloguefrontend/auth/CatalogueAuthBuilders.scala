/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, Call, MessagesControllerComponents, MessagesRequest, Result, Request}
import uk.gov.hmrc.internalauth.client.{IAAction, FrontendAuthComponents, Predicate, ResourceType, ResourceLocation, Resource}
import uk.gov.hmrc.cataloguefrontend.CatalogueFrontendSwitches

import scala.concurrent.{ExecutionContext, Future}

trait CatalogueAuthBuilders:

  def auth: FrontendAuthComponents
  def ec: ExecutionContext
  def mcc: MessagesControllerComponents

  private val readPerm =
    Predicate.Permission(Resource(ResourceType("catalogue-frontend"), ResourceLocation("*")), IAAction("READ"))

  def BasicAuthAction: ActionBuilder[MessagesRequest, AnyContent] =
    if CatalogueFrontendSwitches.requiresLogin.isEnabled
    then
      mcc.actionBuilder.andThen(
        new ActionBuilder[MessagesRequest, AnyContent]:
          override def executionContext: ExecutionContext =
            ec

          override def parser: BodyParser[AnyContent] =
            mcc.parsers.anyContent

          override def invokeBlock[A](request: Request[A], block: MessagesRequest[A] => Future[Result]): Future[Result] =
            auth
              .authorizedAction(AuthController.continueUrl(Call("GET", request.uri)), readPerm) // Other HTTP methods are not suported in targetUrl
              .invokeBlock[A](request, ar => block(MessagesRequest[A](ar.request, mcc.messagesApi)))
      )
    else
      mcc.messagesActionBuilder
