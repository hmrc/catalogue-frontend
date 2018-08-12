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

import org.mockito.Matchers.{eq => is, _}
import org.mockito.Mockito._
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, ControllerComponents, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.UmpToken

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UmpAuthenticatedSpec extends WordSpec with MockitoSugar with ScalaFutures with GuiceOneAppPerSuite {

  "Action" should {

    "allow the request to proceed if user is signed-in (checking UMP)" in new Setup {
      val expectedStatus = Ok
      val umpToken       = UmpToken("token")
      val request        = FakeRequest().withSession("ump.token" -> umpToken.value)

      when(userManagementAuthConnector.isValid(is(umpToken))(any())).thenReturn(Future(true))

      action
        .invokeBlock(request, (_: Request[AnyContent]) => Future(expectedStatus))
        .futureValue shouldBe expectedStatus
    }

    "return 404 NOT_FOUND if user is not signed-in (token exists but not valid)" in new Setup {
      val umpToken = UmpToken("token")
      val request  = FakeRequest().withSession("ump.token" -> umpToken.value)

      when(userManagementAuthConnector.isValid(is(umpToken))(any())).thenReturn(Future(false))

      val result = action.invokeBlock(request, (_: Request[AnyContent]) => Future(Ok))

      status(result) shouldBe 404
    }

    "return 404 NOT_FOUND if user is not signed-in (no token in the session)" in new Setup {
      val result = action.invokeBlock(FakeRequest(), (_: Request[AnyContent]) => Future(Ok))

      status(result) shouldBe 404
    }
  }

  private trait Setup {
    val userManagementAuthConnector = mock[UserManagementAuthConnector]
    val cc = mock[ControllerComponents]
    val action                      = new UmpAuthenticated(userManagementAuthConnector, cc)
  }
}
