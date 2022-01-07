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
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.DeploymentConfig
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientSupport, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

final class ConfigConnectorSpec
  extends UnitSpec
    with HttpClientSupport
    with WireMockSupport {

  val servicesConfig =
    new ServicesConfig(
      Configuration(
        "microservice.services.service-configs.host" -> wireMockHost,
        "microservice.services.service-configs.port" -> wireMockPort
      )
    )

  val configConnector =
    new ConfigConnector(httpClient, servicesConfig)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "deploymentConfig" should {
    "return the deployment configuration for a service in an environment" in {
      stubFor(
        get(urlEqualTo("/deployment-config/production/some-service"))
          .willReturn(aResponse().withBody("""{ "slots": 11, "instances": 3 }"""))
      )

      val deploymentConfig =
        configConnector
          .deploymentConfig("some-service", Environment.Production)
          .futureValue

      deploymentConfig shouldBe Some(DeploymentConfig(slots = 11, instances = 3))
    }

    "return None when the deployment configuration cannot be found" in {
      val deploymentConfig =
        configConnector
          .deploymentConfig("some-service", Environment.Production)
          .futureValue

      deploymentConfig shouldBe None
    }
  }
}
