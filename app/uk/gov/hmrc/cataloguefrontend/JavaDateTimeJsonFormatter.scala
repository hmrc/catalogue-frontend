/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import java.time._
import java.time.format.DateTimeFormatter

import _root_.play.api.libs.json._


object JavaDateTimeJsonFormatter {

  implicit val localDateTimeReads = new Reads[LocalDateTime] {
    override def reads(json: JsValue): JsResult[LocalDateTime] = json match {
      case JsNumber(v) => JsSuccess(
        LocalDateTime.ofEpochSecond(v.toLongExact, 0, ZoneOffset.UTC)
      )
      case v => JsError(s"invalid value for epoch second '$v'")
    }
  }

  implicit val localDateReads = new Reads[LocalDate] {
    override def reads(json: JsValue): JsResult[LocalDate] = json match {
      case JsString(v) => JsSuccess(
        LocalDate.parse(v,DateTimeFormatter.ofPattern("yyyy-MM-dd"))
      )
      case v => JsError(s"invalid value LocalDate '$v'")
    }
  }

}
