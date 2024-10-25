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

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.cataloguefrontend.model.ServiceName
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import RouteConfigurationConnector.{Route, RouteType}
import uk.gov.hmrc.cataloguefrontend.model.Environment

import scala.concurrent.ExecutionContext


class RouteConfigurationConnectorSpec
  extends AnyWordSpec
     with MockitoSugar
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with WireMockSupport
     with HttpClientV2Support:

  private trait Setup:
    val servicesConfig     = mock[ServicesConfig]

    when(servicesConfig.baseUrl("service-configs"))
      .thenReturn(wireMockUrl)

    given HeaderCarrier    = HeaderCarrier()
    given ExecutionContext = ExecutionContext.global
    val connector          = RouteConfigurationConnector(httpClientV2, servicesConfig)
  end Setup

  "RouteRulesConnector.routes" should:
    "return a services routes" in new Setup:

      val serviceName = ServiceName("service-1")

      stubFor(
        get(urlEqualTo(s"/service-configs/routes?serviceName=${serviceName.asString}"))
          .willReturn(
            aResponse()
              .withBody(
                """[
                    {"serviceName": "service-1","path": "fp","ruleConfigurationUrl": "rcu","isRegex": false,"routeType": "frontend","environment": "production"}
                   ,{"serviceName": "service-1","path": "fp","ruleConfigurationUrl": "rcu","isRegex": false,"routeType": "adminfrontend","environment": "production"}
                   ]"""
              )
          )
      )

      connector.routes(service = Some(serviceName)).futureValue shouldBe Seq(
        Route(
          serviceName          = serviceName,
          path                 = "fp",
          ruleConfigurationUrl = Some("rcu"),
          routeType            = RouteType.Frontend,
          environment          = Environment.Production
        ),
        Route(
          serviceName          = serviceName,
          path                 = "fp",
          ruleConfigurationUrl = Some("rcu"),
          routeType            = RouteType.AdminFrontend,
          environment          = Environment.Production
        )
      )

    "return all frontend service routes" in new Setup:

      val frontend = RouteType.Frontend

      stubFor(
        get(urlEqualTo(s"/service-configs/routes?routeType=${frontend.asString}"))
          .willReturn(
            aResponse()
              .withBody(
                """[
                    {"serviceName": "service-1","path": "fp","ruleConfigurationUrl": "rcu","isRegex": false,"routeType": "frontend","environment": "production"}
                   ,{"serviceName": "service-2","path": "fp","ruleConfigurationUrl": "rcu","isRegex": false,"routeType": "frontend","environment": "production"}
                   ]"""
              )
          )
      )

      connector.routes(routeType = Some(frontend)).futureValue shouldBe Seq(
        Route(
          serviceName          = ServiceName("service-1"),
          path                 = "fp",
          ruleConfigurationUrl = Some("rcu"),
          routeType            = frontend,
          environment          = Environment.Production
        ),
        Route(
          serviceName          = ServiceName("service-2"),
          path                 = "fp",
          ruleConfigurationUrl = Some("rcu"),
          routeType            = frontend,
          environment          = Environment.Production
        )
      )

  "RouteConfigurationConnector.searchFrontendPath" should :
    "return production frontend routes containing the path search term" in new Setup:

      val searchTerm = "/foo"

      stubFor(
        get(urlEqualTo(s"/service-configs/frontend-routes/search?frontendPath=$searchTerm&environment=${Environment.Production.asString}"))
          .willReturn(
            aResponse()
              .withBody(
                """[
                  {
                    "serviceName": "service-1",
                    "path": "/foo",
                    "ruleConfigurationUrl": "https://github.com/hmrc/.../service-1.conf#L184",
                    "isRegex": false,
                    "routeType": "frontend",
                    "environment": "production"
                  },
                  {
                    "serviceName": "service-1",
                    "path": "/foo/bar",
                    "ruleConfigurationUrl": "https://github.com/hmrc/.../service-1.conf#L184",
                    "isRegex": false,
                    "routeType": "frontend",
                    "environment": "production"
                  },
                  {
                    "serviceName": "service-1",
                    "path": "^/foo/bar\\-.*$",
                    "ruleConfigurationUrl": "https://github.com/hmrc/.../service-1.conf#L184",
                    "isRegex": true,
                    "routeType": "frontend",
                    "environment": "production"
                  }
                ]"""
              )
          )
      )

      connector.searchFrontendPath(searchTerm, Some(Environment.Production)).futureValue shouldBe Seq(
        Route(ServiceName("service-1"), "/foo"           , Some("https://github.com/hmrc/.../service-1.conf#L184"), false, RouteType.Frontend,  Environment.Production),
        Route(ServiceName("service-1"), "/foo/bar"       , Some("https://github.com/hmrc/.../service-1.conf#L184"), false, RouteType.Frontend,  Environment.Production),
        Route(ServiceName("service-1"), "^/foo/bar\\-.*$", Some("https://github.com/hmrc/.../service-1.conf#L184"), true , RouteType.Frontend,  Environment.Production)
      )

    "return no frontend routes when there is no matching search term" in new Setup:
      val searchTerm = "/foo"

      stubFor(
        get(urlEqualTo(s"/service-configs/frontend-routes/search?frontendPath=$searchTerm"))
          .willReturn(
            aResponse()
              .withBody("""[]""")
          )
      )

      connector.searchFrontendPath(searchTerm, None).futureValue shouldBe Seq.empty[Route]

end RouteConfigurationConnectorSpec
