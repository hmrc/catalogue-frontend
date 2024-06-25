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
  name   : ServiceName,
  version: Version,
  vendor : Vendor,
  kind   : Kind
)

object JdkVersion:
  private val vendorRead: Reads[Vendor] =
    JsPath
      .read[String]
      .map:
        _.toUpperCase match
          case "OPENJDK" => Vendor.OpenJDK
          case "ORACLE"  => Vendor.Oracle
          case _         => Vendor.Oracle // default to oracle

  private val kindRead: Reads[Kind] =
    JsPath
      .read[String]
      .map:
        _.toUpperCase match
          case "JRE" => Kind.JRE
          case "JDK" => Kind.JDK
          case _     => Kind.JDK // default to JDK

  val reads: Reads[JdkVersion] =
    ( (__ \ "name"   ).read[ServiceName](ServiceName.format)
    ~ (__ \ "version").read[Version](Version.format)
    ~ (__ \ "vendor" ).read[Vendor](vendorRead)
    ~ (__ \ "kind"   ).read[Kind](kindRead)
    )(JdkVersion.apply)

case class JdkUsageByEnv(
  env  : SlugInfoFlag,
  usage: Map[(Version, Vendor), Int]
)

enum Vendor(val asString: String, val imgPath: String):
  case Oracle  extends Vendor("Oracle" , "img/oracle2.gif")
  case OpenJDK extends Vendor("OpenJDK", "img/openjdk.png")

  override def toString(): String = // TODO remove this - for backward compatibility only
    asString

enum Kind(val asString: String):
  case JRE extends Kind("JRE")
  case JDK extends Kind("JDK")

  override def toString(): String = // TODO remove this - for backward compatibility only
    asString
