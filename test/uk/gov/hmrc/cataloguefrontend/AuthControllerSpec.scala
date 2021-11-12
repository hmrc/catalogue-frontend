/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, MessagesApi}
import play.api.mvc._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.internalauth.client.Retrieval
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthControllerSpec
  extends AnyWordSpec
     with Matchers
     with GuiceOneAppPerSuite
     with MockitoSugar
     with ArgumentMatchersSugar
     with OptionValues
     with ScalaFutures {

  implicit lazy val defaultLang: Lang = Lang(java.util.Locale.getDefault)

  import Helpers._

  "Signing in" should {
    "redirect to internal-auth" in new Setup {
      val request = FakeRequest()

      val result = controller.signIn(targetUrl = None)(request)

      redirectLocation(result) shouldBe Some("/internal-auth-frontend/sign-in?continue_url=%2Fpost-sign-in")
    }

    "forward target Url" in new Setup {
      val request = FakeRequest()

      val result = controller.signIn(targetUrl = Some("/my-url"))(request)

      redirectLocation(result) shouldBe Some("/internal-auth-frontend/sign-in?continue_url=%2Fpost-sign-in%3FtargetUrl%3D%252Fmy-url")
    }
  }

  "Returning from signing-in" should {
    "put username into session" in new Setup {
      val request = FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      when(
        authStubBehaviour.stubAuth(
          None,
          Retrieval.username
        )
      ).thenReturn(Future.successful(Retrieval.Username("user.name")))

      val result = controller.postSignIn(targetUrl = None)(request)

      redirectLocation(result)                         shouldBe Some(routes.CatalogueController.index.url)
      Helpers.session(result).apply("ump.displayName") shouldBe "user.name"
    }

    "redirect to requested page" in new Setup {
      val request = FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      when(
        authStubBehaviour.stubAuth(
          None,
          Retrieval.username
        )
      ).thenReturn(Future.successful(Retrieval.Username("user.name")))

      val result = controller.postSignIn(targetUrl = Some("/my-url"))(request)

      redirectLocation(result)                         shouldBe Some("/my-url")
      Helpers.session(result).apply("ump.displayName") shouldBe "user.name"
    }
  }

  "Signing out" should {
    "redirect to landing page and clear session" in new Setup {
      val request = FakeRequest()

      val result = controller.signOut(request)

      redirectLocation(result)      shouldBe Some(routes.CatalogueController.index.url)
      result.futureValue.newSession shouldBe Some(Session())
    }
  }

  private[this] trait Setup {
    val messagesApi       = app.injector.instanceOf[MessagesApi]
    val mcc               = app.injector.instanceOf[MessagesControllerComponents]
    val authStubBehaviour = mock[StubBehaviour]
    val authComponent     = { implicit val cc: ControllerComponents = Helpers.stubControllerComponents()
                              FrontendAuthComponentsStub(authStubBehaviour)
                            }
    val controller        = new AuthController(authComponent, mcc)
  }
}
