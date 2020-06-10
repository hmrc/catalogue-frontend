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

package uk.gov.hmrc.cataloguefrontend

import java.time._
import java.time.format.DateTimeFormatter
import java.util.Date

import scala.util.Try

object DateHelper {

  val `dd-MM-yyyy`: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
  val `yyyy-MM-dd`: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  val `yyyy-MM-dd HH:mm z`: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
  val `yyyy-MM-dd HH:mm:ss z`: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

  implicit class JavaDateToLocalDateTime(d: Date) {
    def toLocalDate = LocalDateTime.ofInstant(d.toInstant, ZoneId.systemDefault())
  }

  implicit class LocalDateTimeImplicits(d: LocalDateTime) {

    def toDate: Date = Date.from(d.atZone(ZoneId.systemDefault()).toInstant)

    def epochSeconds = d.toEpochSecond(ZoneOffset.UTC)

    def epochMillis = d.atZone(ZoneId.of("GMT")).toInstant.toEpochMilli

    def asString = d.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))

    def asUTCString = {
      d.atZone(ZoneId.of("UTC")).format(`yyyy-MM-dd HH:mm:ss z`)
    }

    def asPattern(pattern: String) = d.format(DateTimeFormatter.ofPattern(pattern))

    def asRFC1123: String =
      DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.of(d, ZoneId.of("GMT")))

    def displayFormat: String =
      d.format(DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm"))
  }

  def stringToLocalDateTimeOpt(ds: String): Option[LocalDateTime] =
    Try {
      LocalDate.parse(ds, `yyyy-MM-dd`).atStartOfDay()
    }.toOption

  implicit class InstantToZonedDateTime(instant: Instant) {
    def asUTC: ZonedDateTime =
      // use ZoneId.of("UTC") rather than ZoneOffset.UTC to get "UTC" as short displayName rather than "Z"
      ZonedDateTime.ofInstant(instant, ZoneId.of("UTC"))
  }
}
