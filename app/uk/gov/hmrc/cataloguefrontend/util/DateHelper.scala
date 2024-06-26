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

package uk.gov.hmrc.cataloguefrontend.util

import java.time._
import java.time.format.DateTimeFormatter

object DateHelper {
  val utc = ZoneId.of("UTC")

  // TODO consolidate presentation of dates
  // Search for "yyy-MM-dd" across service too
  val `yyyy-MM-dd`           : DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  val `dd MMM uuuu`          : DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM uuuu")
  val `dd MMM uuuu HH:mm`    : DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm")
  val `yyyy-MM-dd HH:mm:ss z`: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

  extension (d: LocalDate)
    def atStartOfDayInstant: Instant =
      d.atStartOfDay(utc).toInstant

    def atEndOfDayInstant: Instant =
      d.atTime(23, 59, 59).atZone(utc).toInstant // friendlier than `.atTime(LocalTime.MAX)` e.g. 2023-01-30T23:59:59.999999999Z
  end extension

  extension (d: Instant)
    def asPattern(pattern: String): String =
      d.atZone(utc).format(DateTimeFormatter.ofPattern(pattern))

    def asPattern(dtf: DateTimeFormatter): String =
      d.atZone(utc).format(dtf)

    def displayFormat: String =
      d.atZone(utc).format(`dd MMM uuuu HH:mm`)

    def dateOnlyFormat: String =
      d.atZone(utc).toLocalDate.format(`dd MMM uuuu`)
  end extension
}
