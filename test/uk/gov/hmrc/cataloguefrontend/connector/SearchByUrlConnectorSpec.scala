/*
 * Copyright 2024 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.SearchByUrlService.{FrontendRoute, FrontendRoutes}

import scala.concurrent.ExecutionContext
class SearchByUrlConnectorSpec
  extends AnyWordSpec
    with MockitoSugar
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WireMockSupport
    with HttpClientV2Support:

  private trait Setup:
    val servicesConfig = mock[ServicesConfig]

    when(servicesConfig.baseUrl("service-configs"))
      .thenReturn(wireMockUrl)

    given HeaderCarrier = HeaderCarrier()

    given ExecutionContext = ExecutionContext.global

    val connector = SearchByUrlConnector(httpClientV2, servicesConfig)
  end Setup

  "SearchByUrlConnector.search" should :
    "return production frontend routes containing the frontend path search term" in new Setup:

      val searchTerm = "/foo"

      stubFor(
        get(urlEqualTo(s"/service-configs/frontend-routes/search?frontendPath=$searchTerm"))
          .willReturn(
            aResponse()
              .withBody(
                """[
                  {
                    "service": "service-1",
                    "environment": "production",
                    "routesFile": "service-1.conf",
                    "routes": [
                      {
                        "routesFile": "",
                        "markerComments": [],
                        "frontendPath": "/foo",
                        "shutterKillswitch": {
                          "switchFile": "/etc/nginx/switches/mdtp/offswitch",
                          "statusCode": 503
                        },
                        "shutterServiceSwitch": {
                          "switchFile": "/etc/nginx/switches/mdtp/service-1",
                          "statusCode": 503,
                          "errorPage": "/shuttered/service-1"
                        },
                        "backendPath": "",
                        "isDevhub": false,
                        "ruleConfigurationUrl": "https://github.com/hmrc/.../service-1.conf#L184",
                        "isRegex": false
                      }
                    ]
                  },
                  {
                    "service": "service-1",
                    "environment": "production",
                    "routesFile": "service-1.conf",
                    "routes": [
                      {
                        "routesFile": "",
                        "markerComments": [],
                        "frontendPath": "/foo-bar",
                        "shutterKillswitch": {
                          "switchFile": "/etc/nginx/switches/mdtp/offswitch",
                          "statusCode": 503
                        },
                        "shutterServiceSwitch": {
                          "switchFile": "/etc/nginx/switches/mdtp/service-1",
                          "statusCode": 503,
                          "errorPage": "/shuttered/service-1"
                        },
                        "backendPath": "",
                        "isDevhub": false,
                        "ruleConfigurationUrl": "https://github.com/hmrc/.../service-1.conf#L184",
                        "isRegex": false
                      }
                    ]
                  }
                ]"""
              )
          )
      )

      connector.search(searchTerm).futureValue shouldBe List(
        FrontendRoutes(ServiceName("service-1"), Environment.Production, List(FrontendRoute("/foo"    , "https://github.com/hmrc/.../service-1.conf#L184", false, false))),
        FrontendRoutes(ServiceName("service-1"), Environment.Production, List(FrontendRoute("/foo-bar", "https://github.com/hmrc/.../service-1.conf#L184", false, false)))
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

      connector.search(searchTerm).futureValue shouldBe List.empty[FrontendRoutes]
      
end SearchByUrlConnectorSpec
