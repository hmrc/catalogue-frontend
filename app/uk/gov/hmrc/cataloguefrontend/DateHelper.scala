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

import java.time.format.DateTimeFormatter
import java.time._
import java.util.Date

import scala.util.Try

object DateHelper {

  val `dd-MM-yyyy`: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

  implicit class JavaDateToLocalDateTime(d: Date) {
    def toLocalDate = LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault())
  }

  implicit class LocalDateTimeImplicits(d: LocalDateTime) {


    def toDate: Date = Date.from(d.atZone(ZoneId.systemDefault()).toInstant)

    def epochSeconds = d.toEpochSecond(ZoneOffset.UTC)

    def asString = d.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))

    def asRFC1123: String = {
      DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.of(d, ZoneId.of("GMT")))
    }

  }

  implicit def stringToLocalDateTimeOpt(ds: String) = {
    Try {
      LocalDate.parse(ds, `dd-MM-yyyy`).atStartOfDay()
    }.toOption

  }

}
