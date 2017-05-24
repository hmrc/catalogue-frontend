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

import java.util.Date

import play.api.libs.json._


sealed trait EventData
case class ServiceOwnerUpdatedEventData(service: String, name: String) extends EventData
case class SomeOtherEventData(something: String, somethingElse: Long) extends EventData // <--- this is an example event type showing how to add other EventData types


object EventData extends EventData {
  implicit val  serviceOwnerUpdatedEventDataFormat = Json.format[ServiceOwnerUpdatedEventData]
  implicit val  someOtherEventDataFormat = Json.format[SomeOtherEventData]
}

case class Event(eventType: EventType.Value,
                 timestamp: Long = new Date().getTime,
                 data: JsObject,
                 metadata: JsObject = JsObject(Seq.empty))

object Event {
  implicit val format = Json.format[Event]
}








