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

package view.partials

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.auth.AuthController
import views.html.partials.with_username

class WithDisplayNameSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  "Working with logged in user's username" should {
    "be possible if user is logged in" in {
      implicit val request = FakeRequest().withSession(AuthController.SESSION_USERNAME -> "John Smith")
      val expectedOutput   = "<p> Something rendered when user logged-in </p>"

      val output =
        with_username(ifLoggedIn = _ => Html(expectedOutput))(ifNotLoggedIn = Html("not expecting to see this"))

      output.toString().trim shouldBe expectedOutput
    }

    "fallback to a default html if user not logged in" in {
      implicit val requestWithoutDisplayName = FakeRequest()
      val expectedOutput                     = "<p> Something rendered when user NOT logged-in </p>"

      val output =
        with_username(ifLoggedIn = _ => Html("not expecting to see this"))(ifNotLoggedIn = Html(expectedOutput))

      output.toString.trim shouldBe expectedOutput
    }
  }
}
