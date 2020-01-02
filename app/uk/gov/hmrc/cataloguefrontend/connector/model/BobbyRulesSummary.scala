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
import play.api.libs.json.{Json, JsValue, Reads, __}
import uk.gov.hmrc.cataloguefrontend.connector.SlugInfoFlag

case class BobbyRulesSummary(
    date   : LocalDate
  , summary: Map[(BobbyRule, SlugInfoFlag), Int]
  )

case class HistoricBobbyRulesSummary(
    date   : LocalDate
  , summary: Map[(BobbyRule, SlugInfoFlag), List[Int]]
  )

private object DataFormat {
  private implicit val brvf = BobbyRule.reads

  private def f[A](map: List[(JsValue, Map[String, A])]): Map[(BobbyRule, SlugInfoFlag), A] =
    map.flatMap { case (k1, v1) =>
      v1.map { case (k2, v2) =>
        ( ( k1.as[BobbyRule]
          , SlugInfoFlag.parse(k2).getOrElse(sys.error(s"Invalid SlugInfoFlag $k2")) // TODO propagate failure into client Format
          )
        , v2
        )
      }
    }.toMap

  def dataReads[A : Reads]: Reads[Map[(BobbyRule, SlugInfoFlag), A]] =
    implicitly[Reads[List[(JsValue, Map[String, A])]]].map(f[A])
}

object BobbyRulesSummary {

  val reads: Reads[BobbyRulesSummary] = {
    implicit val df = DataFormat.dataReads[Int]
    ( (__ \ "date"   ).read[LocalDate]
    ~ (__ \ "summary").read[Map[(BobbyRule, SlugInfoFlag), Int]]
    )(BobbyRulesSummary.apply _)
  }
}

object HistoricBobbyRulesSummary {

  val reads: Reads[HistoricBobbyRulesSummary] = {
    implicit val df = DataFormat.dataReads[List[Int]]
    ( (__ \ "date"   ).read[LocalDate]
    ~ (__ \ "summary").read[Map[(BobbyRule, SlugInfoFlag), List[Int]]]
    )(HistoricBobbyRulesSummary.apply _)
  }
}
