/*
 * Copyright 2021 HM Revenue & Customs
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

final case class MetricsEntry (dependency: DependencyName, happyCount: Int, unHappyCount: Int)

object MetricsEntry {

  def apply(progressMetricses: Seq[ServiceProgressMetrics]): Seq[MetricsEntry] = progressMetricses
    .groupBy(_.name)
    .mapValues { metrics =>
        val (happy, unhappy) = metrics
          .partition(_.isHappy)
      (happy.size, unhappy.size)
    }
    .map{ case (dep, (happy, unhappy)) =>
      MetricsEntry(
        DependencyName(dep),
        happy,
        unhappy
      )
    }
    .toSeq

}
