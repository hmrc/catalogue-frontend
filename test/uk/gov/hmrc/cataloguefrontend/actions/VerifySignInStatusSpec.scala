/*
 * Copyright 2020 HM Revenue & Customs
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
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.Results._
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.{UmpToken, User}
import uk.gov.hmrc.cataloguefrontend.connector.model.Username

import scala.concurrent.{ExecutionContext, Future}

class VerifySignInStatusSpec extends WordSpec with MockitoSugar with ScalaFutures with GuiceOneAppPerSuite {
  import ExecutionContext.Implicits.global

  "Action" should {

    "verify with UMP auth service if token in the session is valid" in new Setup {
      val umpToken = UmpToken("token")
      val request  = FakeRequest().withSession("ump.token" -> umpToken.value)

      when(userManagementAuthConnector.getUser(is(umpToken))(any())).thenReturn(Future(Some(User(Username("username"), groups = List.empty))))

      action
        .invokeBlock(request, actionBodyExpecting(isValid = true))
        .futureValue shouldBe expectedStatus
    }

    "verify with UMP auth service if token in the session is invalid" in new Setup {
      val umpToken = UmpToken("token")
      val request  = FakeRequest().withSession("ump.token" -> umpToken.value)

      when(userManagementAuthConnector.getUser(is(umpToken))(any())).thenReturn(Future(None))

      action
        .invokeBlock(request, actionBodyExpecting(isValid = false))
        .futureValue shouldBe expectedStatus
    }

    "indicate that user is not signed-in if token is not present in the session " +
      "(without calling UMP auth service)" in new Setup {

      action
        .invokeBlock(FakeRequest(), actionBodyExpecting(isValid = false))
        .futureValue shouldBe expectedStatus
    }

  }

  private trait Setup {
    val expectedStatus              = Ok
    val userManagementAuthConnector = mock[UserManagementAuthConnector]
    val mcc = app.injector.instanceOf[MessagesControllerComponents]
    val action                      = new VerifySignInStatus(userManagementAuthConnector, mcc)

    def actionBodyExpecting(isValid: Boolean): UmpVerifiedRequest[_] => Future[Result] = authRequest => {
      authRequest.isSignedIn shouldBe isValid
      Future.successful(expectedStatus)
    }
  }
}
