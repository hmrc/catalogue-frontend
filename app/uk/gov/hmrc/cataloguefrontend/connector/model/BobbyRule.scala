/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, Reads, __}


case class BobbyRule(
    group   : String
  , artefact: String
  , range   : BobbyVersionRange
  , reason  : String
  , from    : LocalDate
  )

object BobbyRule {
  val reads: Reads[BobbyRule] = {
    implicit val bvrf = BobbyVersionRange.format
    ( (__ \ "organisation").read[String]
    ~ (__ \ "name"        ).read[String]
    ~ (__ \ "range"       ).read[BobbyVersionRange]
    ~ (__ \ "reason"      ).read[String]
    ~ (__ \ "from"        ).read[LocalDate]
    )(BobbyRule.apply _)
  }
}

case class BobbyRuleSet(libraries: Seq[BobbyRule], plugins: Seq[BobbyRule])
object BobbyRuleSet {
  val reads: Reads[BobbyRuleSet] = {
    implicit val brr = BobbyRule.reads
    Json.reads[BobbyRuleSet]
  }
}