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

package uk.gov.hmrc.cataloguefrontend.util

import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class MarkdownLoaderSpec
  extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with ScalaFutures {

  "MarkdownLoader.markdownFromString" should {
    "correctly apply markdown rules to a string" in {
      val s: String = "Test string [link this](https://thing.thing)"
      val result = MarkdownLoader.markdownFromString(s)
      result shouldBe Right("<p>Test string <a href=\"https://thing.thing\">link this</a></p>")
    }

    "return a string when markdown is formatted incorrectly" in {
      val s: String = "Test string (link this)[https://thing.thing]"
      val result = MarkdownLoader.markdownFromString(s)
      result.isLeft shouldBe true
    }
  }
}
