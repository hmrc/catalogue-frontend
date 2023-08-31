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

package views

import org.jsoup.Jsoup
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.auth.AuthController
import views.html.standard_layout

class StandardLayoutSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  "standard layout" should {
    "show Sign In link if user is not signed-in" in {
      val request  = FakeRequest(GET, "/currentPage")
      val document = Jsoup.parse(standard_layout()(Html("n/a"))(request).toString())

      val signInLink = document.select("a#sign-in")
      signInLink.attr("href") shouldBe "/sign-in?targetUrl=%2FcurrentPage"
      signInLink.text         shouldBe "Sign in"
    }

    "show name of a user if they are signed-in" in {
      val expectedDisplayName = "John Smith"
      val request             = FakeRequest(GET, "/currentPage").withSession(AuthController.SESSION_USERNAME -> expectedDisplayName)

      val document = Jsoup.parse(standard_layout()(Html("n/a"))(request).toString())

      val userName = document.select("a#logged-in-user")
      userName.text shouldBe expectedDisplayName
    }

    "show a link to sign-out if user is signed-in" in {
      val expectedDisplayName = "John Smith"
      val request             = FakeRequest(GET, "/currentPage").withSession(AuthController.SESSION_USERNAME -> expectedDisplayName)

      val document = Jsoup.parse(standard_layout()(Html("n/a"))(request).toString())

      val signOutLink = document.select("a#sign-out")

      signOutLink.text         shouldBe "Sign out"
      signOutLink.attr("href") shouldBe "/sign-out"
    }
  }
}
