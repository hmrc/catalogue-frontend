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
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.{ServiceName, SlugInfoFlag, Version}

case class SbtVersion(
  serviceName: ServiceName,
  version    : Version
)

object SbtVersion:
  val reads: Reads[SbtVersion] =
    ( (__ \ "serviceName").read[ServiceName]
    ~ (__ \ "version"    ).read[Version    ](Version.format    )
    )(SbtVersion.apply)


case class SbtUsageByEnv(
  env  : SlugInfoFlag,
  usage: Map[Version, Int]
)
