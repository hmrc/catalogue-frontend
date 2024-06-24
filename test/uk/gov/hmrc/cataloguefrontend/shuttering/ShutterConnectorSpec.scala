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

package uk.gov.hmrc.cataloguefrontend.shuttering

import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterConnector.ShutterEventsFilter
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext

class ShutterConnectorSpec
  extends AnyWordSpec
     with MockitoSugar
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with WireMockSupport
     with HttpClientV2Support {

  private trait Fixture {
    val servicesConfig = mock[ServicesConfig]
    when(servicesConfig.baseUrl("shutter-api"))
      .thenReturn(wireMockUrl)

    implicit val hc: HeaderCarrier    = HeaderCarrier()
    implicit val ec: ExecutionContext = ExecutionContext.global
    val connector = new ShutterConnector(httpClientV2, servicesConfig)
  }

  "Shutter Events" should {
    "be filtered by environment only when no service name is specified" in new Fixture {
      val filter = ShutterEventsFilter(environment = Environment.QA, serviceName = None)
      stubFor(
        get(urlPathEqualTo("/shutter-api/events"))
          .willReturn(aResponse().withBody("[]"))
      )

      connector.shutterEventsByTimestampDesc(filter).futureValue shouldBe empty

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/shutter-api/events"))
          .withQueryParam("type"            , equalTo("shutter-state-change"))
          .withQueryParam("data.environment", equalTo("qa"))
      )
    }

    "be filtered by serviceName and environment when a service name is specified" in new Fixture {
      val filter = ShutterEventsFilter(environment = Environment.QA, serviceName = Some(ServiceName("abc-frontend")))
      stubFor(
        get(urlPathEqualTo("/shutter-api/events"))
          .willReturn(aResponse().withBody("[]"))
      )

      connector.shutterEventsByTimestampDesc(filter).futureValue shouldBe empty

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/shutter-api/events"))
          .withQueryParam("type"            , equalTo("shutter-state-change"))
          .withQueryParam("data.environment", equalTo("qa"))
          .withQueryParam("data.serviceName", equalTo("abc-frontend"))
      )
    }
  }
}
