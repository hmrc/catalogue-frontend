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

import org.jsoup.Jsoup
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.i18n.{Lang, MessagesApi}
import play.api.mvc._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.{UmpToken, UmpUnauthorized}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.DisplayName
import uk.gov.hmrc.cataloguefrontend.service.AuthService
import uk.gov.hmrc.cataloguefrontend.service.AuthService.TokenAndDisplayName

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

  "Authenticating" should {

    "redirect to landing page with ump token in session on successful login" in new Setup {
      val username            = "john.smith"
      val password            = "password"
      val request             = FakeRequest().withFormUrlEncodedBody("username" -> username, "password" -> password)
      val expectedToken       = UmpToken("ump-token")
      val expectedDisplayName = DisplayName("John Smith")

      when(authService.authenticate(eqTo(username), eqTo(password))(any))
        .thenReturn(Future(Right(TokenAndDisplayName(expectedToken, expectedDisplayName))))

      val result: Future[Result] = controller.submit(request)

      redirectLocation(result).get                     shouldBe routes.CatalogueController.index.url
      Helpers.session(result).apply("ump.token")       shouldBe expectedToken.value
      Helpers.session(result).apply("ump.displayName") shouldBe expectedDisplayName.value
    }

    "lower case username sent to ump and redirect to landing page on successful login" in new Setup {
      val password            = "password"
      val request             = FakeRequest().withFormUrlEncodedBody("username" -> "John.Smith", "password" -> password)
      val expectedToken       = UmpToken("ump-token")
      val expectedDisplayName = DisplayName("John Smith")

      when(authService.authenticate(eqTo("john.smith"), eqTo(password))(any))
        .thenReturn(Future(Right(TokenAndDisplayName(expectedToken, expectedDisplayName))))

      val result: Future[Result] = controller.submit(request)

      redirectLocation(result).get                     shouldBe routes.CatalogueController.index.url
      Helpers.session(result).apply("ump.token")       shouldBe expectedToken.value
      Helpers.session(result).apply("ump.displayName") shouldBe expectedDisplayName.value
    }

    "show 400 BAD_REQUEST and error message when auth service does not recognize user credentials" in new Setup {
      val username = "n/a"
      val password = "n/a"
      val request  = FakeRequest().withFormUrlEncodedBody("username" -> username, "password" -> password)

      when(authService.authenticate(eqTo(username), eqTo(password))(any))
        .thenReturn(Future(Left(UmpUnauthorized)))

      val result = controller.submit(request)

      status(result) shouldBe 400

      contentAsString(result) should include(messagesApi("sign-in.wrong-credentials"))
    }

    "show 400 BAD_REQUEST and error message when no username or password are provided" in new Setup {
      val request = FakeRequest().withFormUrlEncodedBody("username" -> "", "password" -> "")

      val result = controller.submit(request)

      status(result)          shouldBe 400
      contentAsString(result) should include(messagesApi("sign-in.wrong-credentials"))
    }
  }

  "Showing sign-in page" should {
    "provide a link to help people who forgotten their password" in new Setup {
      val result                = controller.showSignInPage(targetUrl = None)(FakeRequest())
      val signInPage            = Jsoup.parse(contentAsString(result))
      val forgottenPasswordLink = signInPage.select("#forgotten-password")

      forgottenPasswordLink.attr("href") shouldBe selfServiceUrl
      forgottenPasswordLink.text         shouldBe selfServiceUrl
    }
  }

  "Signing out" should {
    "redirect to landing page and clear session" in new Setup {
      val request = FakeRequest()

      val result = controller.signOut(request)

      redirectLocation(result)      shouldBe Some(routes.AuthController.showSignInPage().url)
      result.futureValue.newSession shouldBe Some(Session())
    }
  }

  private[this] trait Setup {
    val messagesApi    = app.injector.instanceOf[MessagesApi]
    val mcc            = app.injector.instanceOf[MessagesControllerComponents]
    val authService    = mock[AuthService]
    val selfServiceUrl = "self-service-url"
    val config         = Configuration("self-service-url" -> selfServiceUrl)
    val controller     = new AuthController(authService, config, mcc)
  }
}
