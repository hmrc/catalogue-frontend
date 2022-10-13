/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.functional.syntax._
import uk.gov.hmrc.cataloguefrontend.model.SlugInfoFlag

case class JDKVersion(name: String, version: Version, vendor: Vendor, kind: Kind)

trait JDKVersionFormats {

  import play.api.libs.json._

  val versionFormat: Format[Version] =
    Format.of[String].inmap(Version.apply, _.toString)

  val vendorRead: Reads[Vendor] = JsPath
    .read[String]
    .map(_.toUpperCase match {
      case "OPENJDK" => OpenJDK
      case "ORACLE"  => Oracle
      case _         => Oracle // default to oracle
    })

  val kindRead: Reads[Kind] = JsPath
    .read[String]
    .map(_.toUpperCase match {
      case "JRE" => JRE
      case "JDK" => JDK
      case _     => JDK // default to JDK
    })

  val jdkFormat: Reads[JDKVersion] = {
    implicit val vf  = Version.format
    ((__ \ "name").read[String]
      ~ (__ \ "version").read[Version]
      ~ (__ \ "vendor").read[Vendor](vendorRead)
      ~ (__ \ "kind").read[Kind](kindRead))(JDKVersion)
  }
}

object JDKVersionFormats extends JDKVersionFormats

case class JDKUsageByEnv(env: SlugInfoFlag, usage: Map[JDKVersion, Int])

sealed trait Vendor

case object Oracle extends Vendor {
  override def toString: String = "Oracle"
}

case object OpenJDK extends Vendor {
  override def toString: String = "OpenJDK"
}

sealed trait Kind

case object JRE extends Kind {
  override def toString: String = "JRE"
}

case object JDK extends Kind {
  override def toString: String = "JDK"
}
