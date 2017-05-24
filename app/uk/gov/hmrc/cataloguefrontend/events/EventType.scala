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

import play.api.libs.json._

object EventType extends Enumeration {

  type EventType = Value

  val ServiceOwnerUpdated, Other = Value

  implicit val eventType = new Format[EventType] {
    override def reads(json: JsValue): JsResult[EventType] = json match {
      case JsString(s) => {
        try {
          JsSuccess(EventType.withName(s))
        } catch {
          case _: NoSuchElementException => JsError(s"Enumeration expected of type: '${EventType.getClass}', but it does not appear to contain the value: '$s'")
        }
      }
      case _ => JsError("String value expected")
    }

    override def writes(o: EventType): JsValue = JsString(o.toString)
  }

}
