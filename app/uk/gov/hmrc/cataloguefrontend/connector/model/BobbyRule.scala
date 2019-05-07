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

import java.time.LocalDate

import play.api.libs.json.{Json, Reads}

case class BobbyRule(organisation: String, name: String, range: BobbyVersionRange, reason: String, from: LocalDate) {
  val groupArtifactName: String = {
    val wildcard = "*"
    if (organisation == wildcard && name == wildcard) "*" else s"$organisation:$name"
  }
}

object BobbyRule {
  val reads: Reads[BobbyRule] = {
    implicit val bvrr = BobbyVersionRange.reads
    Json.reads[BobbyRule]
  }
}

case class BobbyRuleSet(libraries: Seq[BobbyRule], plugins: Seq[BobbyRule])
object BobbyRuleSet {
  val reads: Reads[BobbyRuleSet] = {
    implicit val brr = BobbyRule.reads
    Json.reads[BobbyRuleSet]
  }
}