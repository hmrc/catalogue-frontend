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

package uk.gov.hmrc.cataloguefrontend.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UrlUtilsSpec extends AnyWordSpec with Matchers {

  "buildQueryParams" should {
    "generate a list of params" in {
      val params: Seq[(String, String)] = UrlUtils.buildQueryParams("foo" -> Some("Bar"), "fizz" -> Some("buzz"))
      params should contain theSameElementsAs Seq(("foo", "Bar"), ("fizz", "buzz"))
    }

    "drop empty params" in {
      val params = UrlUtils.buildQueryParams("baz" -> Some("buz"), "fizz" -> None)
      params should contain theSameElementsAs Seq(("baz", "buz"))
    }
  }
}
