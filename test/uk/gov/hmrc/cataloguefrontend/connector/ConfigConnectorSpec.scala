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
import org.mockito.scalatest.MockitoSugar
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.ConfigService.AppliedConfig
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.DeploymentConfig
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.cataloguefrontend.service.ConfigService.{KeyName, ServiceName}

import scala.concurrent.ExecutionContext.Implicits.global

final class ConfigConnectorSpec
  extends UnitSpec
     with HttpClientV2Support
     with WireMockSupport
     with MockitoSugar {

  val servicesConfig =
    new ServicesConfig(
      Configuration(
        "microservice.services.service-configs.host"                    -> wireMockHost,
        "microservice.services.service-configs.port"                    -> wireMockPort,
        "microservice.services.service-configs.configKeysCacheDuration" -> "1 hour"
      )
    )

  val configConnector =
    new ConfigConnector(httpClientV2, servicesConfig, mock[AsyncCacheApi])

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "deploymentConfig" should {
    "return the deployment configuration for a service in an environment" in {
      stubFor(
        get(urlEqualTo("/service-configs/deployment-config/production/some-service"))
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

  "configSearch" should {
    "return AppliedConfig" in {
      stubFor(
        get(urlEqualTo("/service-configs/search?key=%22testKey%22"))
          .willReturn(aResponse().withBody(
            """[
              |  {
              |    "environment": "production",
              |    "serviceName": "test-service",
              |    "key": "testKey",
              |    "value": "testValue"
              |  }
              |]""".stripMargin))
      )

      val expected = Seq(AppliedConfig(Environment.Production, ServiceName("test-service"), KeyName("testKey"), "testValue"))

      val result =
        configConnector
          .configSearch("testKey")
          .futureValue

      result shouldBe expected
    }
  }
}
