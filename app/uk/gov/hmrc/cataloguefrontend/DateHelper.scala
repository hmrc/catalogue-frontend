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

package uk.gov.hmrc.cataloguefrontend

import java.time._
import java.time.format.DateTimeFormatter
import java.util.Date

object DateHelper {
  val utc = ZoneId.of("UTC")

  val `yyyy-MM-dd`: DateTimeFormatter            = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  val `dd-MM-yyyy HH:mm`: DateTimeFormatter      = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
  val `dd MMM uuuu HH:mm`: DateTimeFormatter     = DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm")
  val `yyyy-MM-dd HH:mm z`: DateTimeFormatter    = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
  val `yyyy-MM-dd HH:mm:ss z`: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

  val `dd MMM uuuu`: DateTimeFormatter     = DateTimeFormatter.ofPattern("dd MMM uuuu")


  implicit class JavaDateToLocalDateTime(d: Date) {
    def toLocalDate: LocalDateTime =
      LocalDateTime.ofInstant(d.toInstant, ZoneId.systemDefault())
  }

  implicit class LocalDateImplicits(d: LocalDate) {
    def atStartOfDayEpochMillis: Long =
      d.atStartOfDay(utc).toInstant.toEpochMilli

    def atEndOfDayEpochMillis: Long =
      d.atTime(LocalTime.MAX).atZone(utc).toInstant.toEpochMilli
  }

  implicit class LocalDateTimeImplicits(d: LocalDateTime) {
    def epochMillis: Long =
      d.atZone(utc).toInstant.toEpochMilli

    def asPattern(pattern: String): String =
      d.format(DateTimeFormatter.ofPattern(pattern))

    def displayFormat: String =
      d.format(`dd MMM uuuu HH:mm`)

  }

  implicit class InstantImplicits(d: Instant) {

    def asPattern(pattern: String): String =
      d.atZone(utc).format(DateTimeFormatter.ofPattern(pattern))

    def asPattern(dtf: DateTimeFormatter): String =
      d.atZone(utc).format(dtf)

    def displayFormat: String =
      d.atZone(utc).format(`dd MMM uuuu HH:mm`)

    def dateOnlyFormat: String =
      d.atZone(utc).toLocalDate.format(`dd MMM uuuu`)
  }

  def longToLocalDate(l: Long): LocalDate =
    Instant.ofEpochMilli(l).atZone(utc).toLocalDate
}
