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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.model

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.Environment

import scala.util.Random.shuffle

class EnvironmentOrderingSpec extends WordSpec with Matchers {

  implicit def strToEnv(s: String): Environment = Environment(s)

  "Environment ordering" should {
    "order environments" in {
      val ordered = List[Environment]("production", "externaltest", "staging", "qa", "integration", "development")
      shuffle(ordered).sorted shouldBe ordered
    }

    "handle suffixes and prefixes by sorting lexicographically" in {
      val ordered = List[Environment]("production-ECS", "production-Heritage", "ECS-qa", "Heritage-qa")
      ordered.reverse.sorted shouldBe ordered
    }

    "puts unknown environments at the end, and lexicographically orders them" in {
      val ordered = List[Environment]("production", "development", "unknown-1", "unknown-2")
      ordered.reverse.sorted shouldBe ordered
    }
  }
}
