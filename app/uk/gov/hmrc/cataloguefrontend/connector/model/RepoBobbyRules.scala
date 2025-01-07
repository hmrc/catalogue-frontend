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
import uk.gov.hmrc.cataloguefrontend.model.Version
import uk.gov.hmrc.cataloguefrontend.connector.RepoType

case class RepoBobbyRules(
  repoName   : String
, repoVersion: Version
, repoType   : RepoType
, bobbyRules : Seq[BobbyRule]
)

object RepoBobbyRules:
  val reads: Reads[RepoBobbyRules] =
    given Reads[BobbyRule] = BobbyRule.reads
    ( (__ \ "repoName"   ).read[String]
    ~ (__ \ "repoVersion").read[Version](Version.format)
    ~ (__ \ "repoType"   ).read[RepoType]
    ~ (__ \ "bobbyRules" ).read[Seq[BobbyRule]]
    )(RepoBobbyRules.apply)
