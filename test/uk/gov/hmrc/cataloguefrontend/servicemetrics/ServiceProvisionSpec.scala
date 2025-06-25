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

package uk.gov.hmrc.cataloguefrontend.servicemetrics

import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}

import java.time.Instant

class ServiceProvisionSpec extends AnyWordSpec with Matchers with OptionValues:

  "a service provision" should:
    "should calculate costs" in:
      val sp = ServiceProvision(
        from        = Instant.now
      , to          = Instant.now
      , serviceName = ServiceName("some-service")
      , environment = Environment.QA
      , metrics     = Map(
                        "instances" -> BigDecimal(32.071079749103944)
                      , "slots"     -> BigDecimal(448.99514077803457)
                      , "time"      -> BigDecimal(11.335835701679693)
                      , "requests"  -> BigDecimal(2660620962L)
                      , "memory"    -> BigDecimal(1211)
                      )
      )

      sp.slotsPerInstance               shouldBe Some(BigDecimal("14.00000075739823991048106938966487")) // string keeps precision otherwise truncates to double
      sp.percentageOfMaxMemoryUsed      shouldBe Some(BigDecimal("67.57812134403213125522503137262788"))
      sp.costPerInstanceInPence         shouldBe Some(BigDecimal("8750.000473373899944050668368540544"))
      sp.costPerRequestInPence          shouldBe Some(BigDecimal("0.0001054723566393789865397595104717513"))
      sp.totalRequestTime               shouldBe Some(BigDecimal("30160362.089676969805524666"))
      sp.costPerTotalRequestTimeInPence shouldBe Some(BigDecimal("0.009304330039270996888487567960705089"))

    "should handle dividing by zero" in:
      val sp = ServiceProvision(
        from        = Instant.now
      , to          = Instant.now
      , serviceName = ServiceName("some-service")
      , environment = Environment.QA
      , metrics     = Map(
                        "instances" -> BigDecimal(0)
                      , "slots"     -> BigDecimal(0)
                      , "time"      -> BigDecimal(0)
                      , "requests"  -> BigDecimal(0)
                      , "memory"    -> BigDecimal(0)
                      )
      )

      sp.slotsPerInstance               shouldBe None
      sp.percentageOfMaxMemoryUsed      shouldBe None
      sp.costPerInstanceInPence         shouldBe None
      sp.costPerRequestInPence          shouldBe None
      sp.totalRequestTime               shouldBe Some(BigDecimal(0)) // no division
      sp.costPerTotalRequestTimeInPence shouldBe None

    "should handle empty metrics (not deployed or reporting)" in:
      val sp = ServiceProvision(
        from        = Instant.now
      , to          = Instant.now
      , serviceName = ServiceName("some-service")
      , environment = Environment.QA
      , metrics     = Map.empty
      )

      sp.slotsPerInstance               shouldBe None
      sp.percentageOfMaxMemoryUsed      shouldBe None
      sp.costPerInstanceInPence         shouldBe None
      sp.costPerRequestInPence          shouldBe None
      sp.totalRequestTime               shouldBe None
      sp.costPerTotalRequestTimeInPence shouldBe None
