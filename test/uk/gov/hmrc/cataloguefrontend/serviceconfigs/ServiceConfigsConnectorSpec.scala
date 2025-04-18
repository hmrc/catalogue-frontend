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
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import uk.gov.hmrc.cataloguefrontend.cost.{DeploymentConfig, DeploymentSize, Zone}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsService.{AppliedConfig, ConfigSourceValue, KeyName}
import uk.gov.hmrc.cataloguefrontend.test.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

final class ServiceConfigsConnectorSpec
  extends UnitSpec
     with HttpClientV2Support
     with WireMockSupport
     with MockitoSugar {

  val servicesConfig =
    ServicesConfig(
      Configuration(
        "microservice.services.service-configs.host"                    -> wireMockHost,
        "microservice.services.service-configs.port"                    -> wireMockPort,
        "microservice.services.service-configs.configKeysCacheDuration" -> "1 hour"
      )
    )

  val serviceConfigsConnector =
    ServiceConfigsConnector(httpClientV2, servicesConfig, mock[AsyncCacheApi])

  given HeaderCarrier = HeaderCarrier()

  "deploymentConfig" should {
    "return the deployment configuration for a service in an environment" in {
      stubFor(
        get(urlEqualTo("/service-configs/deployment-config?environment=production&serviceName=some-service&applied=true"))
          .willReturn(aResponse().withBody("""[{
            "name"       : "test1",
            "slots"      : 11,
            "instances"  : 3,
            "environment": "production",
            "zone"       : "protected",
            "envVars"    : { "k1": "v1"},
            "jvm"        : { "k2": "v2"}
          }]"""))
      )

      val deploymentConfig =
        serviceConfigsConnector
          .deploymentConfig(Some(ServiceName("some-service")), Some(Environment.Production))
          .futureValue

      deploymentConfig shouldBe Seq(
        DeploymentConfig(
          ServiceName("test1"),
          DeploymentSize(slots = 11, instances = 3),
          environment = Environment.Production,
          zone        = Zone.Protected,
          envVars     = Map("k1" -> "v1"),
          jvm         = Map("k2" -> "v2")
        )
      )
    }

    "return None when the deployment configuration cannot be found" in {
      stubFor(
        get(urlEqualTo("/service-configs/deployment-config?environment=production&serviceName=some-service&applied=true"))
          .willReturn(aResponse().withBody("""[]"""))
      )

      val deploymentConfig =
        serviceConfigsConnector
          .deploymentConfig(Some(ServiceName("some-service")), Some(Environment.Production))
          .futureValue

      deploymentConfig.headOption shouldBe None
    }
  }

  "configSearch" should {
    "return AppliedConfig" in {
      stubFor(
        get(urlEqualTo("/service-configs/search?environment=production&key=test.key&keyFilterType=contains&value=testValue&valueFilterType=equalTo"))
          .willReturn(aResponse().withBody("""[
            {
              "serviceName": "test-service",
              "key": "test.key",
              "environments": {
                "production": { "source": "some-source", "sourceUrl": "some-url", "value": "testValue" }
               }
            }
          ]""".stripMargin))
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

  "repoNameForService" should {
    val service = ServiceName("service-1")
    "return repo name for a service" in {
      stubFor(
        get(urlEqualTo(s"/service-configs/services/repo-name?serviceName=${service.asString}"))
          .willReturn(
            aResponse()
              .withBody(""" "repo-1" """)
          )
      )
      serviceConfigsConnector.repoNameForService(service).futureValue shouldBe Some("repo-1")
    }

    "return none when no repo name for service found" in {
      stubFor(
        get(urlEqualTo(s"/service-configs/services/repo-name?serviceName=${service.asString}"))
          .willReturn(
            aResponse()
              .withStatus(404)
          )
      )
      serviceConfigsConnector.repoNameForService(service).futureValue shouldBe None
    }
  }

  "serviceRepoMappings" should {
    "return service to repo name mappings" in {
      stubFor(
        get(urlEqualTo(s"/service-configs/service-repo-names"))
          .willReturn(
            aResponse()
              .withBody(
                """[
                  |  {
                  |    "serviceName": "service-1",
                  |    "artefactName": "repo-1",
                  |    "repoName": "repo-1"
                  |  },
                  |  {
                  |    "serviceName": "service-2",
                  |    "artefactName": "repo-2",
                  |    "repoName": "repo-2"
                  |  },
                  |  {
                  |    "serviceName": "service-3",
                  |    "artefactName": "repo-3",
                  |    "repoName": "repo-3"
                  |  }
                  |]""".stripMargin)
          )
      )
      serviceConfigsConnector.serviceRepoMappings.futureValue shouldBe List(
        ServiceToRepoName("service-1", "repo-1", "repo-1"),
        ServiceToRepoName("service-2", "repo-2", "repo-2"),
        ServiceToRepoName("service-3", "repo-3", "repo-3")
      )
    }
  }
}
