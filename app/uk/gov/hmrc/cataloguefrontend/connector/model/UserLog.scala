/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.connector.model

import play.api.libs.functional.syntax.unlift
import play.api.libs.functional.syntax._
import play.api.libs.json.{OFormat, __}

case class UserLog(
  userName: String,
  logs    : Seq[Log]
)

object UserLog {
  val userLogFormat: OFormat[UserLog] = {
    implicit val urf = Log.format
    ( ( __ \ "userName").format[String]
    ~ ( __ \ "logs"    ).format[Seq[Log]]
    )(UserLog.apply, unlift(UserLog.unapply))
  }
}

case class Log(
  page         : String,
  visitCounter : Int
)

object Log {
  val format: OFormat[Log] = {
    ( ( __ \ "uri"  ).format[String]
    ~ ( __ \ "count").format[Int]
    )(Log.apply, unlift(Log.unapply))
  }
}
