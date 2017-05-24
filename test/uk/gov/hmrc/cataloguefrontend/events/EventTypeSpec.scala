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

package uk.gov.hmrc.cataloguefrontend.events

import org.scalatest.{FunSpec, Matchers}
import play.api.libs.json.Json

class EventTypeSpec extends FunSpec with Matchers {

  describe("EventType") {
    it("should convert to and from json (read/write)") {
      val json = Json.toJson(EventType.ServiceOwnerUpdated)
      json.as[EventType.Value] shouldBe EventType.ServiceOwnerUpdated
    }
  }
}
