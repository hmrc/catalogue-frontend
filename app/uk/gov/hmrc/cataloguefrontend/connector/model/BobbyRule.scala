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

import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.VersionRange

import java.time.LocalDate

case class BobbyRule(
  group         : String,
  artefact      : String,
  range         : VersionRange,
  reason        : String,
  from          : LocalDate,
  exemptProjects: Seq[String]
):
  def id: String =
    s"$group:$artefact:${range.range}:$from"

object BobbyRule:
  val reads: Reads[BobbyRule] =
    ( (__ \ "organisation" ).read[String]
    ~ (__ \ "name"         ).read[String]
    ~ (__ \ "range"        ).read[VersionRange](VersionRange.format)
    ~ (__ \ "reason"       ).read[String]
    ~ (__ \ "from"         ).read[LocalDate]
    ~(__ \ "exemptProjects").read[Seq[String]]
    )(BobbyRule.apply)

case class BobbyRuleSet(
  libraries: Seq[BobbyRule],
  plugins  : Seq[BobbyRule]
)

object BobbyRuleSet:
  val reads: Reads[BobbyRuleSet] =
    given Reads[BobbyRule] = BobbyRule.reads
    Json.reads[BobbyRuleSet]
