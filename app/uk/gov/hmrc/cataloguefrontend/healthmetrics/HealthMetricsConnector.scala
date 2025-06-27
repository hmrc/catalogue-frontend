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


import play.api.libs.json.Reads
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, TeamName}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HealthMetricsConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using
  ExecutionContext
):
  import uk.gov.hmrc.http.HttpReads.Implicits._

  private val url: String = servicesConfig.baseUrl("health-metrics")

  def healthMetricsTimelineCounts(
    team        : TeamName
  , healthMetric: HealthMetric
  , to          : LocalDate
  , from        : LocalDate
  )(using
    HeaderCarrier
  ): Future[Seq[HealthMetricTimelineCount]] =
    given Reads[HealthMetricTimelineCount] = HealthMetricTimelineCount.reads
    httpClientV2
      .get(url"$url/health-metrics/timeline?team=${team.asString}&healthMetric=${healthMetric.asString}&from=$from&to=$to")
      .execute[Seq[HealthMetricTimelineCount]]

  def latestTeamHealthMetrics(
    team: TeamName
  )(using
    HeaderCarrier
  ): Future[LatestHealthMetrics] =
    given Reads[LatestHealthMetrics] = LatestHealthMetrics.reads
    httpClientV2
      .get(url"$url/health-metrics/teams/${team.asString}/health-metrics/latest")
      .execute[LatestHealthMetrics]
  
  def latestDigitalServiceHealthMetrics(
    digitalService: DigitalService
  )(using
    HeaderCarrier
  ): Future[LatestHealthMetrics] =
    given Reads[LatestHealthMetrics] = LatestHealthMetrics.reads
    httpClientV2
      .get(url"$url/health-metrics/digital-services/${digitalService.asString}/health-metrics/latest")
      .execute[LatestHealthMetrics]
