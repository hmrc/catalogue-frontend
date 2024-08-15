/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.createrepository

import uk.gov.hmrc.cataloguefrontend.test.UnitSpec

class CreatePrototypeFormSpec extends UnitSpec {
  "slackChannelCharacterValidation" when {
    "validates comma seperated list of slack channels" should {
      "return true for valid comma seperated slack channels" in {
        CreatePrototype.slackChannelCharacterValidation(
          "#channel-1,channel-2,channel_3"
        ) shouldBe true
      }
      "return true for single valid slack channel" in {
        CreatePrototype.slackChannelCharacterValidation(
          "#channel-1"
        ) shouldBe true
      }
      "return false for single invalid slack channel" in {
        CreatePrototype.slackChannelCharacterValidation(
          "#CHANNEL-1"
        ) shouldBe false
      }
      "return false valid slack channels incorrectly comma seperated" in {
        CreatePrototype.slackChannelCharacterValidation(
          "#channel-1, channel-2, channel_3"
        ) shouldBe false
      }
      "return false for correctly comma seperated slack channels with invalid name" in {
        CreatePrototype.slackChannelCharacterValidation(
          "#channel-1,CHANNEL-2"
        ) shouldBe false
      }
    }
  }
}
