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

package uk.gov.hmrc.cataloguefrontend.createawebhook

import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class CreateWebhookFormSpec extends UnitSpec {

  "eventsAreValid" should {
    "return true" when {
      "valid events are provided" in {
        CreateWebhookForm.eventsAreValid(Seq(WebhookEventType.values.head.value())) shouldBe true
      }
    }
    "return false" when {
      "no events are provided" in {
        CreateWebhookForm.eventsAreValid(Seq.empty) shouldBe false
      }
      "provided events are not supported" in {
        CreateWebhookForm.eventsAreValid(Seq("InvalidEvent")) shouldBe false
      }
    }
  }

  "webhookUrlIsValid" should {
    "return true" when {
      "webhook url is valid" in {
        CreateWebhookForm.webhookUrlIsValid("http://url.com") shouldBe true
      }
    }
    "return false" when {
      "webhook url is invalid" in {
        CreateWebhookForm.webhookUrlIsValid("ht://url.com") shouldBe false
      }
    }
  }
}
