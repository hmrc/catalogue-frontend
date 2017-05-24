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

import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpec, FunSuite, Matchers}
import play.api.libs.json.{JsObject, JsString, Json}

class EventSpec extends FunSpec with Matchers with MockitoSugar {

  describe("Json write") {

    it("should convert a valid json string to an Event") {
      val jsonString = """{
                         |  "eventType" : "ServiceOwnerUpdated",
                         |  "data" : {
                         |      "service" : "Catalogue",
                         |      "name" : "Armin Keyvanloo"
                         |    },
                         |  "metadata" : {},
                         |  "timestamp" : 123
                         |}"""
        .stripMargin

      val event = Json.parse(jsonString).as[Event]
      event shouldBe Event(EventType.ServiceOwnerUpdated, timestamp = 123l, JsObject(Seq("service" -> JsString("Catalogue"), "name" -> JsString("Armin Keyvanloo"))))
    }

    it("should covert an Event to json and back correctly") {
      val event = Event(EventType.ServiceOwnerUpdated, timestamp = 123l, JsObject(Seq("service" -> JsString("Catalogue"), "name" -> JsString("Armin Keyvanloo"))))

      val js = Json.toJson(event)

      js.as[Event] shouldBe event
    }

  }

}
