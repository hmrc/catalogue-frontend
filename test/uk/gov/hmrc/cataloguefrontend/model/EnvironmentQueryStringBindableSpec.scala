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

package uk.gov.hmrc.cataloguefrontend.model

import org.scalatest.{Matchers, OptionValues, WordSpec}

class EnvironmentQueryStringBindableSpec extends WordSpec with Matchers with OptionValues {

  "an Environment QueryString parameter" should {

    "be bound to an Environment value when valid" in {
      Environment.values.foreach { environment =>
        val params = Map("environment" -> Seq(environment.asString))

        Environment.queryStringBindable.bind(key = "env", params).value shouldBe Right(environment)
      }
    }

    "not be bound when missing" in {
      Environment.queryStringBindable.bind(key = "env", Map.empty) shouldBe None
    }

    "fail to be bound when there is no associated value" in {
      val params = Map("environment" -> Seq.empty)

      Environment.queryStringBindable.bind(key = "env", params).value shouldBe 'Left
    }

    "fail to be bound when there is more than one associated value" in {
      val params = Map("environment" -> Seq(Environment.Production.asString, Environment.QA.asString))

      Environment.queryStringBindable.bind(key = "env", params).value shouldBe 'Left
    }

    "fail to be bound when the value is unrecognised" in {
      val params = Map("environment" -> Seq("unknown"))

      Environment.queryStringBindable.bind(key = "env", params).value shouldBe 'Left
    }
  }
}
