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

package uk.gov.hmrc.cataloguefrontend.connector.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import scala.util.Random

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class BobbyRuleViolationSpec extends AnyFreeSpec with Matchers {

  "BobbyRuleViolation" - {

    "should order by next valid version" in {
      val orderedList =
        List(
          BobbyRuleViolation(
            reason = "reason1"
          , range  = BobbyVersionRange.parse("[1.0.0,)").get
          , from   = LocalDate.now
          )
        , BobbyRuleViolation(
            reason = "reason2"
          , range  = BobbyVersionRange.parse("(,99.99.99)").get
          , from   = LocalDate.now
          )
        , BobbyRuleViolation(
            reason = "reason3"
          , range  = BobbyVersionRange.parse("(,2.1.0)").get
          , from   = LocalDate.now
          )
        , BobbyRuleViolation(
            reason = "reason4"
          , range  = BobbyVersionRange.parse("(,1.2.0]").get
          , from   = LocalDate.now
          )
        , BobbyRuleViolation(
            reason = "reason5"
          , range  = BobbyVersionRange.parse("(,1.2.0)").get
          , from   = LocalDate.now
          )
        , BobbyRuleViolation(
            reason = "reason6"
          , range  = BobbyVersionRange.parse("(,1.2.0)").get
          , from   = LocalDate.now.minus(1, ChronoUnit.DAYS)
          )
        )
      Random.shuffle(orderedList).sorted shouldBe orderedList
    }
  }
}
