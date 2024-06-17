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
import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext

class RouteRulesConnectorSpec
  extends AnyWordSpec
     with MockitoSugar
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with WireMockSupport
     with HttpClientV2Support {

  private trait Fixture {
    val servicesConfig = mock[ServicesConfig]
    when(servicesConfig.baseUrl("service-configs"))
      .thenReturn(wireMockUrl)

    implicit val hc: HeaderCarrier    = HeaderCarrier()
    implicit val ec: ExecutionContext = ExecutionContext.global
    val connector = new RouteRulesConnector(httpClientV2, servicesConfig)
  }

  "RouteRulesConnector.serviceRoutes" should {
    "return service routes" in new Fixture {
      stubFor(
        get(urlPathEqualTo("/service-configs/frontend-route/service1"))
          .willReturn(aResponse().withBody("""[
            { "environment": "prod",
              "routes": [
                {"frontendPath": "fp", "ruleConfigurationUrl": "rcu", "isRegex": false}
              ]
            }
          ]"""))
      )

      import RouteRulesConnector.{EnvironmentRoute, Route}
      connector.frontendRoutes("service1").futureValue shouldBe Seq(
        EnvironmentRoute(
          environment = "prod"
        , routes      = Route(
                          frontendPath         = "fp"
                        , ruleConfigurationUrl = "rcu"
                        , isRegex              = false
                        ) :: Nil
        , isAdmin = false
      ))

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/service-configs/frontend-route/service1"))
      )
    }
  }
}
