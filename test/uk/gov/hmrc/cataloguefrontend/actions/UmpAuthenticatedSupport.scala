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

import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import play.api.mvc.{Action, ActionBuilder, AnyContent, Request, Result}

import scala.concurrent.Future

trait UmpAuthenticatedSupport {

  /**
    * Mocks UmpAuthenticated action to execute action body
    * without checking UMP auth service
    */
  def mockPassThrough(mockedAction: UmpAuthenticated): Unit =
    when(mockedAction.async(any())).thenAnswer(new Answer[Action[AnyContent]] {
      def answer(invocation: InvocationOnMock): Action[AnyContent] = {

        val block = invocation.getArgumentAt(0, classOf[Request[AnyContent] => Future[Result]])

        val actionBuilder = new ActionBuilder[Request] {
          def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] =
            block(request)
        }

        actionBuilder.async(block)
      }
    })

}
