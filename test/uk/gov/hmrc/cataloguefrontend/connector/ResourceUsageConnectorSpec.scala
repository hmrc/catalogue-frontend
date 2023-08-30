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

package uk.gov.hmrc.cataloguefrontend.connector

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.DeploymentSize
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global

final class ResourceUsageConnectorSpec
  extends UnitSpec
     with HttpClientV2Support
     with WireMockSupport {
  import ResourceUsageConnector._

  val servicesConfig =
    new ServicesConfig(
      Configuration(
        "microservice.services.service-configs.host" -> wireMockHost,
        "microservice.services.service-configs.port" -> wireMockPort
      )
    )

  val today = Instant.parse("2023-01-01T00:00:00.0Z")
  val clock = Clock.fixed(today, ZoneId.of("UTC"))

  val resourceUsageConnector =
    new ResourceUsageConnector(httpClientV2, servicesConfig, clock)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "The connector" should {
    "fetch and deserialise historic `ResourceUsage` for a service" in {
      stubFor(
        get(urlEqualTo("/service-configs/resource-usage/services/some-service/snapshots"))
          .willReturn(
            aResponse()
              .withBody(
                """
                  [ {
                    "date"        : "2022-01-01T00:00:00.0Z",
                    "serviceName" : "some-service",
                    "environment" : "production",
                    "slots"       : 1,
                    "instances"   : 2
                  }, {
                    "date"        : "2022-01-02T00:00:00.0Z",
                    "serviceName" : "some-service",
                    "environment" : "qa",
                    "slots"       : 3,
                    "instances"   : 4
                  }, {
                    "date"        : "2022-01-02T00:00:00.0Z",
                    "serviceName" : "some-service",
                    "environment" : "production",
                    "slots"       : 5,
                    "instances"   : 6
                  } ]
                  """
              )
          )
      )

      resourceUsageConnector.historicResourceUsageForService("some-service").futureValue shouldBe List(
          ResourceUsage(
            date        = Instant.parse("2022-01-01T00:00:00.0Z"),
            serviceName = "some-service",
            values      = Map(
                            Environment.Integration  -> DeploymentSize.empty,
                            Environment.Development  -> DeploymentSize.empty,
                            Environment.ExternalTest -> DeploymentSize.empty,
                            Environment.Staging      -> DeploymentSize.empty,
                            Environment.Production   -> DeploymentSize(1, 2),
                            Environment.QA           -> DeploymentSize.empty
                          )
          ),
          ResourceUsage(
            date        = Instant.parse("2022-01-02T00:00:00.0Z"),
            serviceName = "some-service",
            values      = Map(
                            Environment.Integration  -> DeploymentSize.empty,
                            Environment.Development  -> DeploymentSize.empty,
                            Environment.ExternalTest -> DeploymentSize.empty,
                            Environment.Staging      -> DeploymentSize.empty,
                            Environment.Production   -> DeploymentSize(1, 2),
                            Environment.QA           -> DeploymentSize.empty
                          )
          ),
          ResourceUsage(
            date        = Instant.parse("2022-01-02T00:00:00.0Z"),
            serviceName = "some-service",
            values      = Map(
                            Environment.Integration  -> DeploymentSize.empty,
                            Environment.Development  -> DeploymentSize.empty,
                            Environment.ExternalTest -> DeploymentSize.empty,
                            Environment.Staging      -> DeploymentSize.empty,
                            Environment.QA           -> DeploymentSize(3, 4),
                            Environment.Production   -> DeploymentSize(5, 6),
                          )
          )
          ,
          ResourceUsage(
            date        = today,
            serviceName = "some-service",
            values      = Map(
                            Environment.Integration  -> DeploymentSize.empty,
                            Environment.Development  -> DeploymentSize.empty,
                            Environment.ExternalTest -> DeploymentSize.empty,
                            Environment.Staging      -> DeploymentSize.empty,
                            Environment.Production   -> DeploymentSize(5, 6),
                            Environment.QA           -> DeploymentSize(3, 4),
                          )
          )
        )

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/service-configs/resource-usage/services/some-service/snapshots"))
      )
    }
  }
}
