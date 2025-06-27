/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.healthmetrics

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{KeyReads, Reads, __}

import java.time.LocalDate

case class HealthMetricTimelineCount(
  date : LocalDate
, count: Int
)

object HealthMetricTimelineCount:
  val reads: Reads[HealthMetricTimelineCount] =
    ( (__ \ "date" ).read[LocalDate]
    ~ (__ \ "count").read[Int]
    )(HealthMetricTimelineCount.apply)


case class LatestHealthMetrics(metrics: Map[HealthMetric, Int])

object LatestHealthMetrics:
  val reads: Reads[LatestHealthMetrics] =
     (__ \ "metrics").read[Map[HealthMetric, Int]].map(LatestHealthMetrics.apply)
