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

package uk.gov.hmrc.cataloguefrontend.servicemetrics

import play.api.Configuration
import play.api.libs.json.*
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, Environment, ServiceName, TeamName}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceMetricsConnector @Inject() (
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
, configuration : Configuration
)(using
  ec: ExecutionContext
):
  import HttpReads.Implicits._

  private val serviceMetricsBaseUrl: String =
    servicesConfig.baseUrl("service-metrics")

  private val logDuration: Duration =
    configuration.get[Duration]("service-metrics.logDuration")

  def metrics(
    environment   : Option[Environment]    = None
  , teamName      : Option[TeamName]       = None
  , digitalService: Option[DigitalService] = None
  , metricType    : Option[LogMetricId]    = None
  )(using HeaderCarrier): Future[Seq[ServiceMetric]] =
    given Reads[ServiceMetric] = ServiceMetric.reads
    val from = Instant.now().minus(logDuration.toMillis, ChronoUnit.MILLIS)
    httpClientV2
      .get(url"$serviceMetricsBaseUrl/service-metrics/log-metrics?team=${teamName.map(_.asString)}&digitalService=${digitalService.map(_.asString)}&metricType=${metricType.map(_.asString)}&environment=${environment.map(_.asString)}&from=$from")
      .execute[Seq[ServiceMetric]]

  def logMetrics(service: ServiceName)(using HeaderCarrier): Future[Seq[LogMetric]] =
    given Reads[LogMetric] = LogMetric.reads
    val from = Instant.now().minus(logDuration.toMillis, ChronoUnit.MILLIS)
    httpClientV2
      .get(url"$serviceMetricsBaseUrl/service-metrics/${service.asString}/log-metrics?from=$from")
      .execute[Seq[LogMetric]]

  def serviceProvision(
    environment   : Option[Environment]    = None
  , teamName      : Option[TeamName]       = None
  , digitalService: Option[DigitalService] = None
  , from          : Option[Instant]        = None
  , to            : Option[Instant]        = None
  )(using HeaderCarrier): Future[Seq[ServiceProvision]] =
    given Reads[ServiceProvision] = ServiceProvision.reads
    httpClientV2
      .get(url"$serviceMetricsBaseUrl/service-metrics/service-provision?team=${teamName.map(_.asString)}&digitalService=${digitalService.map(_.asString)}&environment=${environment.map(_.asString)}&from=$from&to=$to")
      .execute[Seq[ServiceProvision]]
