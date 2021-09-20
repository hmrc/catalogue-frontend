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

package uk.gov.hmrc.cataloguefrontend.metrics.connector

import play.api.Logger
import play.api.libs.json.Reads
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.metrics.model._
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MetricsConnector @Inject() (
  httpClient: HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  import uk.gov.hmrc.http._
  import HttpReads.Implicits._

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private val platformProgressMetricsBaseURL: String = servicesConfig.baseUrl("platform-progress-metrics")
  private implicit val rF: Reads[MetricsResponse] = MetricsResponse.reads
  private val logger                              = Logger(this.getClass)

  def query(maybeTeam: Option[TeamName]): Future[MetricsResponse] =
    httpClient
      .GET[MetricsResponse](
        url"$platformProgressMetricsBaseURL/platform-progress-metrics/metrics?team=$maybeTeam"
      )
      .recover {
        case UpstreamErrorResponse.Upstream5xxResponse(x) =>
          logger.error(s"An error occurred when connecting to platform progress metrics service. baseUrl: $platformProgressMetricsBaseURL", x)
          MetricsResponse(Seq.empty)
      }
}
