/*
 * Copyright 2017 HM Revenue & Customs
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

import org.scalatest.{Matchers, WordSpec}

class UrlHelperSpec extends WordSpec with Matchers {

  "UrlHelper" should  {
    "append a slash to the url if it doesn't end with one" in {
      "abc".appendSlash shouldBe "abc/"
    }
  }

  "UrlHelper" should  {
    "not append a slash to the url if it already ends with one" in {
      "abc/".appendSlash shouldBe "abc/"
    }
  }
}
