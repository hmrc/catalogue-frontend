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

package uk.gov.hmrc.cataloguefrontend.connector.model

import play.api.libs.json.{OFormat, __}
import play.api.libs.functional.syntax._

case class JDKVersion(name:String, version: String)

trait JDKVersionFormats {

  val jdkFormat: OFormat[JDKVersion] =
    (
      (__ \ "name"     ).format[String]
    ~ (__ \ "jdkVersion" ).format[String]
    )(JDKVersion.apply, unlift(JDKVersion.unapply))
}

object JDKVersionFormats extends JDKVersionFormats


case class JDKUsageByEnv(env: String, usage: Map[String, Int])


trait JDKUsageByEnvFormat {

  val jdkUsageByEnvFormat: OFormat[JDKUsageByEnv] =
    (
      (__ \ "env").format[String]
    ~ (__ \ "usage").format[Map[String, Int]]
    )(JDKUsageByEnv.apply, unlift(JDKUsageByEnv.unapply))

}

object JDKUsageByEnvFormat extends JDKUsageByEnvFormat