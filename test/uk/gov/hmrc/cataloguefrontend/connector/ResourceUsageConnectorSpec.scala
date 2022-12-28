/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.connector.ResourceUsageConnector.ResourceUsage
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

final class ResourceUsageConnectorSpec
  extends UnitSpec
     with HttpClientV2Support
     with WireMockSupport {

  val servicesConfig =
    new ServicesConfig(
      Configuration(
        "microservice.services.service-configs.host" -> wireMockHost,
        "microservice.services.service-configs.port" -> wireMockPort
      )
    )

  val resourceUsageConnector =
    new ResourceUsageConnector(httpClientV2, servicesConfig)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "The connector" should {

    "fetch and deserialise historic `ResourceUsage` for a service" in {

      stubFor(
        get(urlEqualTo("/service-configs/resource-usage/services/some-service/snapshots"))
          .willReturn(
            aResponse()
              .withBody(
                """
                  |[ {
                  |  "date" : "2022-01-01T00:00:00.0Z",
                  |  "serviceName" : "some-service",
                  |  "environment" : "production",
                  |  "slots" : 10,
                  |  "instances" : 10
                  |}, {
                  |  "date" : "2022-01-02T00:00:00.0Z",
                  |  "serviceName" : "some-service",
                  |  "environment" : "production",
                  |  "slots" : 10,
                  |  "instances" : 10
                  |} ]
                  |""".stripMargin
              )
          )
      )

      val expectedResourceUsages =
        List(
          ResourceUsage(
            LocalDateTime.of(2022, 1, 1, 0, 0).toInstant(ZoneOffset.UTC),
            "some-service",
            Environment.Production,
            10,
            10
          ),
          ResourceUsage(
            LocalDateTime.of(2022, 1, 2, 0, 0).toInstant(ZoneOffset.UTC),
            "some-service",
            Environment.Production,
            10,
            10
          )
        )

      val actualResourceUsages =
        resourceUsageConnector
          .historicResourceUsageForService("some-service")
          .futureValue

      actualResourceUsages shouldBe expectedResourceUsages

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/service-configs/resource-usage/services/some-service/snapshots"))
      )
    }
  }
}
