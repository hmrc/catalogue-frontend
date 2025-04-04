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

package uk.gov.hmrc.cataloguefrontend.connector.model

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.{ServiceName, SlugInfoFlag, Version}

case class JdkVersion(
  serviceName: ServiceName,
  version    : Version,
  vendor     : Vendor,
  kind       : Kind
)

object JdkVersion:
  val reads: Reads[JdkVersion] =
    ( (__ \ "name"   ).read[ServiceName]
    ~ (__ \ "version").read[Version](Version.format)
    ~ (__ \ "vendor" ).read[Vendor](Vendor.reads)
    ~ (__ \ "kind"   ).read[Kind](Kind.reads)
    )(JdkVersion.apply)

case class JdkUsageByEnv(
  env  : SlugInfoFlag,
  usage: Map[(Version, Vendor, Kind), Int]
)

enum Vendor(val asString: String, val imgPath: String):
  case Oracle  extends Vendor("Oracle" , "img/oracle2.gif")
  case OpenJDK extends Vendor("OpenJDK", "img/openjdk.png")

object Vendor:
   val reads: Reads[Vendor] =
    JsPath
      .read[String]
      .map:
        _.toUpperCase match
          case "OPENJDK" => Vendor.OpenJDK
          case "ORACLE"  => Vendor.Oracle
          case _         => Vendor.Oracle // default to oracle

enum Kind(val asString: String):
  case JRE extends Kind("JRE")
  case JDK extends Kind("JDK")

object Kind:
  val reads: Reads[Kind] =
    JsPath
      .read[String]
      .map:
        _.toUpperCase match
          case "JRE" => Kind.JRE
          case "JDK" => Kind.JDK
          case _     => Kind.JDK // default to JDK
