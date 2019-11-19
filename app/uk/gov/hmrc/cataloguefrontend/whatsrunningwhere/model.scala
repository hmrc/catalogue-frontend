/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import java.time.LocalDateTime

import play.api.libs.functional.syntax._
import play.api.libs.json._
import JsonCodecs._

case class WhatsRunningWhere(applicationName: ApplicationName,
                             versions: List[WhatsRunningWhereVersion])

object WhatsRunningWhere {
  implicit val whatsRunningWhereFormat: Reads[WhatsRunningWhere] = (
    (__ \ "applicationName").read[ApplicationName] and
    (__ \ "versions").read[List[WhatsRunningWhereVersion]]
  )(WhatsRunningWhere.apply _)
}

case class WhatsRunningWhereVersion(environment: Environment,
                                    versionNumber: VersionNumber,
                                    lastSeen: TimeSeen)

object WhatsRunningWhereVersion {
  implicit val versionFormat: Reads[WhatsRunningWhereVersion] = (
    (__ \ "environment").read[Environment] and
    (__ \ "versionNumber").read[VersionNumber] and
    (__ \ "lastSeen").read[TimeSeen]
  )(WhatsRunningWhereVersion.apply _)
}

object JsonCodecs {
  def format[A, B](f: A => B, g: B => A)(implicit fa: Format[A]): Format[B] = fa.inmap(f, g)

  implicit val applicationNameFormat: Format[ApplicationName] = format(ApplicationName.apply, unlift(ApplicationName.unapply))
  implicit val versionNumberFormat: Format[VersionNumber] = format(VersionNumber.apply, unlift(VersionNumber.unapply))
  implicit val environmentFormat: Format[Environment] = format(Environment.apply, unlift(Environment.unapply))
  implicit val timeSeenFormat: Format[TimeSeen] = format(TimeSeen.apply, unlift(TimeSeen.unapply))
}

case class TimeSeen(time: LocalDateTime)

case class ApplicationName(asString: String) extends AnyVal

case class Environment(asString: String) extends AnyVal

case class ProfileName(asString: String) extends AnyVal

case class VersionNumber(asString: String) extends AnyVal

