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

import cats.implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsResult, JsString, JsValue, Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.SlugInfoFlag

import java.time.LocalDate

case class BobbyRulesSummary(
  date   : LocalDate,
  summary: Map[(BobbyRule, SlugInfoFlag), Int]
)

case class HistoricBobbyRulesSummary(
  date   : LocalDate,
  summary: Map[(BobbyRule, SlugInfoFlag), List[Int]]
)

private object DataFormat:

  import uk.gov.hmrc.cataloguefrontend.util.CategoryHelper.given cats.Applicative[JsResult]

  def dataReads[A: Reads]: Reads[Map[(BobbyRule, SlugInfoFlag), A]] =
    given Reads[BobbyRule] = BobbyRule.reads
    summon[Reads[List[(JsValue, Map[JsString, A])]]]
      .flatMap: l =>
        _ =>
          l
            .flatTraverse: (k1, v1) =>
              v1
                .toList.traverse: (k2, v2) =>
                  summon[Reads[SlugInfoFlag]].reads(k2)
                    .map: flag =>
                      ( ( k1.as[BobbyRule]
                        , flag
                        )
                      , v2
                      )
            .map(_.toMap)

object BobbyRulesSummary:
  val reads: Reads[BobbyRulesSummary] =
    given Reads[Map[(BobbyRule, SlugInfoFlag), Int]] = DataFormat.dataReads[Int]
    ( (__ \ "date"   ).read[LocalDate]
    ~ (__ \ "summary").read[Map[(BobbyRule, SlugInfoFlag), Int]]
    )(BobbyRulesSummary.apply)

object HistoricBobbyRulesSummary:
  val reads: Reads[HistoricBobbyRulesSummary] =
    given Reads[Map[(BobbyRule, SlugInfoFlag), List[Int]]] = DataFormat.dataReads[List[Int]]
    ( (__ \ "date"   ).read[LocalDate]
    ~ (__ \ "summary").read[Map[(BobbyRule, SlugInfoFlag), List[Int]]]
    )(HistoricBobbyRulesSummary.apply)
