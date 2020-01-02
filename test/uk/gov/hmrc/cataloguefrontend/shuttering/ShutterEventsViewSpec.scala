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

package uk.gov.hmrc.cataloguefrontend.shuttering

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterStatus.{Shuttered, Unshuttered}

class ShutterEventsViewSpec extends WordSpec with Matchers {

  "The Shutter Events view" should {
    "present the reason for a shuttered event when the reason is known" in {
      val reasonText = "some reason"
      val status = Shuttered(reason = Some(reasonText), outageMessage = None, useDefaultOutagePage = true)

      ShutterEventsView.reasonFor(status) shouldBe reasonText
    }

    "present an empty reason for a shuttered event when the reason is not known" in {
      val status = Shuttered(reason = None, outageMessage = Some("outage text"), useDefaultOutagePage = false)

      ShutterEventsView.reasonFor(status) shouldBe ""
    }

    "present an empty reason for an unshuttered event" in {
      ShutterEventsView.reasonFor(Unshuttered) shouldBe ""
    }
  }
}
