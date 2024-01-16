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

package uk.gov.hmrc.cataloguefrontend.serviceconfigs

import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.scalatest.MockitoSugar
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.{DeploymentConfig, DeploymentSize, Zone}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsService.{AppliedConfig, ConfigSourceValue, KeyName, ServiceName}
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

final class ServiceConfigsConnectorSpec extends UnitSpec with HttpClientV2Support with WireMockSupport with MockitoSugar {

  val servicesConfig =
    new ServicesConfig(
      Configuration(
        "microservice.services.service-configs.host"                    -> wireMockHost,
        "microservice.services.service-configs.port"                    -> wireMockPort,
        "microservice.services.service-configs.configKeysCacheDuration" -> "1 hour"
      )
    )

  val serviceConfigsConnector =
    new ServiceConfigsConnector(httpClientV2, servicesConfig, mock[AsyncCacheApi])

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "deploymentConfig" should {
    "return the deployment configuration for a service in an environment" in {
      stubFor(
        get(urlEqualTo("/service-configs/deployment-config?environment=production&serviceName=some-service"))
          .willReturn(aResponse().withBody("""[{ "name" : "test1", "slots": 11, "instances": 3, "environment": "production", "zone": "protected" }]"""))
      )

      val deploymentConfig =
        serviceConfigsConnector
          .deploymentConfig(Some("some-service"), Some(Environment.Production))
          .futureValue

      deploymentConfig.headOption shouldBe Some(DeploymentConfig("test1", DeploymentSize(slots = 11, instances = 3), environment = Environment.Production, zone = Zone.Protected))
    }

    "return None when the deployment configuration cannot be found" in {
      stubFor(
        get(urlEqualTo("/service-configs/deployment-config?environment=production&serviceName=some-service"))
          .willReturn(aResponse().withBody("""[]"""))
      )

      val deploymentConfig =
        serviceConfigsConnector
          .deploymentConfig(Some("some-service"), Some(Environment.Production))
          .futureValue

      deploymentConfig.headOption shouldBe None
    }
  }

  "configSearch" should {
    "return AppliedConfig" in {
      stubFor(
        get(urlEqualTo("/service-configs/search?environment=production&key=test.key&keyFilterType=contains&value=testValue&valueFilterType=equalTo"))
          .willReturn(aResponse().withBody("""[
              |  {
              |    "serviceName": "test-service",
              |    "key": "test.key",
              |    "environments": {
              |      "production": { "source": "some-source", "sourceUrl": "some-url", "value": "testValue" }
              |     }
              |  }
              |]""".stripMargin))
      )

      serviceConfigsConnector
        .configSearch(
          teamName        = None,
          environments    = Seq(Environment.Production),
          serviceType     = None,
          key             = Some("test.key"),
          keyFilterType   = KeyFilterType.Contains,
          value           = Some("testValue"),
          valueFilterType = ValueFilterType.EqualTo
        )
        .futureValue shouldBe (
        Right(
          Seq(
            AppliedConfig(
              ServiceName("test-service"),
              KeyName("test.key"),
              Map(Environment.Production -> ConfigSourceValue("some-source", Some("some-url"), "testValue"))
            )
          )
        )
      )
    }
  }
}
