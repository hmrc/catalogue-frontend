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

package uk.gov.hmrc.cataloguefrontend

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

import scala.util.Try

case class Timestamped[T](data: T, timestamp: Option[Instant]) {
  def formattedTimestamp =
    timestamp
      .map(
        _.atZone(ZoneId.of("GMT"))
          .format(DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm")))
      .getOrElse("(None)")
}

object Timestamped {
  def fromStringInstant[T](data: T, stringTimestamp: Option[String] = None): Timestamped[T] =
    Timestamped(
      data,
      stringTimestamp.flatMap(s =>
        Try(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(s))).toOption))
}
