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
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, Reads, __}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.cataloguefrontend.connector.SlugInfoFlag

case class BobbyRulesSummary(
    date: LocalDate
  , summary: Map[BobbyRule, Map[SlugInfoFlag, List[Int]]]
  )

object BobbyRulesSummary {
  import play.api.libs.json.{__, Json, JsValue, JsError, Reads}
  import play.api.libs.functional.syntax._

  private implicit val brvf = BobbyRule.reads

  def f(map: List[(JsValue, Map[String, List[Int]])]): Map[BobbyRule, Map[SlugInfoFlag, List[Int]]] =
    map.map { case (k1, v1) => ( k1.as[BobbyRule]
                                , v1.map { case (k2, v2) => ( SlugInfoFlag.parse(k2).getOrElse(sys.error("TODO handle failure"))
                                                            , v2
                                                            )
                                        }
                                )
            }.toMap

  val reads: Reads[BobbyRulesSummary] =
    ( (__ \ "date"     ).read[LocalDate]
    ~ (__ \ "summary"  ).read[List[(JsValue, Map[String, List[Int]])]].map(f)
    )(BobbyRulesSummary.apply _)
}
