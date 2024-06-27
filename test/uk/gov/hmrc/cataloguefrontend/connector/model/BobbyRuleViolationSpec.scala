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

package uk.gov.hmrc.cataloguefrontend.connector.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import uk.gov.hmrc.cataloguefrontend.model.VersionRange

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import scala.util.Random

class BobbyRuleViolationSpec extends AnyWordSpec with Matchers {
  "BobbyRuleViolation" should {
    "order by next valid version" in {
      val now = LocalDate.now()
      val orderedList =
        List(
          BobbyRuleViolation(
            reason = "reason1"
          , range  = VersionRange("[1.0.0,)")
          , from   = now
          )
        , BobbyRuleViolation(
            reason = "reason2"
          , range  = VersionRange("(,99.99.99)")
          , from   = now
          )
        , BobbyRuleViolation(
            reason = "reason3"
          , range  = VersionRange("(,2.1.0)")
          , from   = now
          )
        , BobbyRuleViolation(
            reason = "reason4"
          , range  = VersionRange("(,1.2.0]")
          , from   = now
          )
        , BobbyRuleViolation(
            reason = "reason5"
          , range  = VersionRange("(,1.2.0)")
          , from   = now
          )
        , BobbyRuleViolation(
            reason = "reason6"
          , range  = VersionRange("(,1.2.0)")
          , from   = now.minus(1, ChronoUnit.DAYS)
          )
        )
      Random.shuffle(orderedList).sorted shouldBe orderedList
    }
  }
}
