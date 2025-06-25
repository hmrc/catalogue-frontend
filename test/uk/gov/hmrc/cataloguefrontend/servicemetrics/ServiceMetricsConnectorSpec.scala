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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, TeamName}
import uk.gov.hmrc.cataloguefrontend.test.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{Instant, Clock, ZoneId}
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

final class ServiceMetricsConnectorSpec
  extends UnitSpec
     with HttpClientV2Support
     with WireMockSupport
     with MockitoSugar:

  private val today    = Instant.parse("2025-06-24T13:00:00.0Z")
  private val clock    = Clock.fixed(today, ZoneId.of("UTC"))
  private val duration = 3.days

  private val underlyingConfig = Configuration(
    "microservice.services.service-metrics.host" -> wireMockHost
  , "microservice.services.service-metrics.port" -> wireMockPort
  , "service-metrics.logDuration"                -> duration.toString
  )

  private val serviceMetricsConnector =
    ServiceMetricsConnector(httpClientV2, ServicesConfig(underlyingConfig), underlyingConfig, clock)

  given HeaderCarrier = HeaderCarrier()

  "metrics" should:
    "filter log metrics" in:
      stubFor:
        get(urlMatching(s"/service-metrics/log-metrics\\?team=some-team&metricType=non-indexed-query&environment=qa&from=2025-06-21T13:00:00Z"))
          .willReturn(aResponse().withBody("""
            [{
              "service": "service-1",
              "id": "non-indexed-query",
              "environment": "qa",
              "kibanaLink": "http://link-1",
              "logCount": 1
            }, {
              "service": "service-2",
              "id": "non-indexed-query",
              "environment": "qa",
              "kibanaLink": "http://link-2",
              "logCount": 2
            }]""".stripMargin))

      serviceMetricsConnector
        .metrics(
          environment = Some(Environment.QA)
        , teamName    = Some(TeamName("some-team"))
        , metricType  = Some(LogMetricId.NonIndexedQuery)
        ).futureValue shouldBe:
          ServiceMetric(
            serviceName = ServiceName("service-1")
          , id          = LogMetricId.NonIndexedQuery
          , environment = Environment.QA
          , kibanaLink  = "http://link-1"
          , logCount    = 1
          ) ::
          ServiceMetric(
            serviceName = ServiceName("service-2")
          , id          = LogMetricId.NonIndexedQuery
          , environment = Environment.QA
          , kibanaLink  = "http://link-2"
          , logCount    = 2
          ) :: Nil


  "logMetrics" should:
    "return logging metrics for all relevant environments" in:
      stubFor:
        get(urlMatching(s"/service-metrics/some-service/log-metrics\\?from=2025-06-21T13:00:00Z"))
          .willReturn(aResponse().withBody("""
            [{
              "id": "slow-running-query",
              "displayName": "Slow Running Query",
              "environments": {
                "qa":         { "kibanaLink": "http://link-1", "count": 0 },
                "production": { "kibanaLink": "http://link-2", "count": 1 }
              }
            }, {
              "id": "non-indexed-query",
              "displayName": "Non-indexed Query",
              "environments": {
                "qa":         { "kibanaLink": "http://link-1", "count": 0 },
                "production": { "kibanaLink": "http://link-2", "count": 2 }
              }
            }, {
              "id": "container-kills",
              "displayName": "Container Kills",
              "environments": {
                "qa":         { "kibanaLink": "http://link-1", "count": 0 },
                "production": { "kibanaLink": "http://link-2", "count": 3 }
              }
            }]""".stripMargin))

      serviceMetricsConnector
        .logMetrics(ServiceName("some-service"))
        .futureValue shouldBe:
          LogMetric(
            id           = "slow-running-query"
          , displayName  = "Slow Running Query"
          , environments =  Map(Environment.QA -> EnvironmentResult("http://link-1", 0), Environment.Production -> EnvironmentResult("http://link-2", 1))
          ) ::
          LogMetric(
            id           = "non-indexed-query"
          , displayName  = "Non-indexed Query"
          , environments =  Map(Environment.QA -> EnvironmentResult("http://link-1", 0), Environment.Production -> EnvironmentResult("http://link-2", 2))
          ) ::
          LogMetric(
            id           = "container-kills"
          , displayName  = "Container Kills"
          , environments =  Map(Environment.QA -> EnvironmentResult("http://link-1", 0), Environment.Production -> EnvironmentResult("http://link-2", 3))
          ) :: Nil


  "serviceProvision" should:
    "filter service provision results" in:
      stubFor:
        get(urlMatching(s"/service-metrics/service-provision\\?team=some-team&environment=qa&from=2025-02-01T00:00:00Z&to=2025-02-28T23:59:59.999Z"))
          .willReturn(aResponse().withBody("""
            [{
              "from": "2025-02-01T00:00:00Z",
              "to": "2025-02-28T23:59:59.999Z",
              "service": "service-1",
              "environment": "qa",
              "metrics": {
                "instances": 32.071079749103944,
                "slots": 448.99514077803457,
                "time": 11.335835701679693,
                "requests": 2660620962,
                "memory": 1211
              }
            }]""".stripMargin))

      serviceMetricsConnector
        .serviceProvision(
          environment = Some(Environment.QA)
        , teamName    = Some(TeamName("some-team"))
        , from        = Some(Instant.parse("2025-02-01T00:00:00Z"))
        , to          = Some(Instant.parse("2025-02-28T23:59:59.999Z"))
        ).futureValue shouldBe:
          ServiceProvision(
            from        = Instant.parse("2025-02-01T00:00:00Z")
          , to          = Instant.parse("2025-02-28T23:59:59.999Z")
          , serviceName = ServiceName("service-1")
          , environment = Environment.QA
          , metrics     = Map(
                            "instances" -> BigDecimal(32.071079749103944)
                          , "slots"     -> BigDecimal(448.99514077803457)
                          , "time"      -> BigDecimal(11.335835701679693)
                          , "requests"  -> BigDecimal(2660620962L)
                          , "memory"    -> BigDecimal(1211)
                          )
          ) :: Nil

