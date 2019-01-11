/*
 * Copyright 2019 HM Revenue & Customs
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

package view

import org.jsoup.Jsoup
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.FakeRequest
import play.twirl.api.Html
import views.html.standard_layout

class StandardLayoutSpec extends WordSpec with Matchers with GuiceOneAppPerSuite {

  "standard layout" should {

    "show Sign In link if user is not signed-in" in {
      val document = Jsoup.parse(standard_layout()(Html("n/a"))(FakeRequest()).toString())

      val signInLink = document.select("nav ul.nav.navbar-nav.navbar-right > li > a#sign-in")
      signInLink.attr("href") shouldBe "/sign-in"
      signInLink.text         shouldBe "Sign in"
    }

    "show name of a user if they are signed-in" in {
      val expectedDisplayName = "John Smith"
      val request             = FakeRequest().withSession("ump.displayName" -> expectedDisplayName)

      val document = Jsoup.parse(standard_layout()(Html("n/a"))(request).toString())

      val userName = document.select("nav ul.nav.navbar-nav.navbar-right > li.dropdown > a")
      userName.text shouldBe expectedDisplayName
    }

    "show a link to sign-out if user is signed-in" in {
      val expectedDisplayName = "John Smith"
      val request             = FakeRequest().withSession("ump.displayName" -> expectedDisplayName)

      val document = Jsoup.parse(standard_layout()(Html("n/a"))(request).toString())

      val signOutLink = document.select("nav ul.nav.navbar-nav.navbar-right > li.dropdown > ul > li > a")

      signOutLink.text         shouldBe "Sign out"
      signOutLink.attr("href") shouldBe "/sign-out"

    }
  }

}
