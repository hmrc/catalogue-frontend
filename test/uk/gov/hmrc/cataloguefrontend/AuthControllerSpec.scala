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

package uk.gov.hmrc.cataloguefrontend

import org.jsoup.Jsoup
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.OneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.test.FakeRequest
import play.api.test.Helpers._

class AuthControllerSpec extends WordSpec with Matchers with OneAppPerSuite {

  "Authenticating" should {

    "redirect to landing page if successful" in new Setup {
      val request = FakeRequest().withFormUrlEncodedBody("username" -> "n/a", "password" -> "n/a")

      val result = controller.submit(request)

      redirectLocation(result).get shouldBe routes.CatalogueController.landingPage().url
    }

    "show 400 BAD_REQUEST and error message when no username or password are provided" in new Setup {
      val request = FakeRequest().withFormUrlEncodedBody("username" -> "", "password" -> "")

      val result = controller.submit(request)

      val foo = Some(Some(Some(1)))

      status(result)          shouldBe 400
      contentAsString(result) should include(messagesApi("sign-in.wrong-credentials"))
    }

  }

  "Showing sign-in page" should {
    "provide a link to help people who forgotten their password" in new Setup {
      val result                = controller.showSignInPage(FakeRequest())
      val signInPage            = Jsoup.parse(contentAsString(result))
      val forgottenPasswordLink = signInPage.select("#forgotten-password")

      forgottenPasswordLink.attr("href") shouldBe "https://selfservice.tools.tax.service.gov.uk"
      forgottenPasswordLink.text         shouldBe "https://selfservice.tools.tax.service.gov.uk"
    }
  }

  private trait Setup {
    val messagesApi = app.injector.instanceOf[MessagesApi]
    val controller  = new AuthController(messagesApi)
  }

}
