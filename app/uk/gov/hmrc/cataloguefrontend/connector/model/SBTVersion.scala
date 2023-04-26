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
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.model.SlugInfoFlag

case class SBTVersion(
  serviceName : String,
  version     : Version
)

trait SBTVersionFormats {

  val sbtVersionReads: Reads[SBTVersion] = {
    implicit val vf  = Version.format
    ( (__ \ "serviceName").read[String]
    ~ (__ \ "version"    ).read[Version]
    )(SBTVersion)
  }
}

object SBTVersionFormats extends SBTVersionFormats

case class SBTUsageByEnv(env: SlugInfoFlag, usage: Map[SBTVersion, Int])
