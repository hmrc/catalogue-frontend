/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientSupport, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext

class LeakDetectionConnectorSpec
   extends UnitSpec
     with HttpClientSupport
     with WireMockSupport
 {
  import ExecutionContext.Implicits.global

  val servicesConfig =
    new ServicesConfig(
      Configuration(
        "microservice.services.leak-detection.host" -> wireMockHost,
        "microservice.services.leak-detection.port" -> wireMockPort
      )
    )

  val leakDetectionConnector = new LeakDetectionConnector(httpClient, servicesConfig)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "repositoriesWithLeaks" should {
    "return repositories with leaks" in {
      stubFor(
        get(urlEqualTo("/reports/repositories"))
          .willReturn(aResponse().withBody("""["repo1","repo2"]"""))
      )

      leakDetectionConnector.repositoriesWithLeaks.futureValue shouldBe Seq(
        RepositoryWithLeaks("repo1"),
        RepositoryWithLeaks("repo2")
      )

      verify(
        getRequestedFor(urlEqualTo("/reports/repositories"))
          .withHeader("Accept", equalTo("application/json"))
      )
    }

    "return empty if leak detection service returns status different than 2xx" in {
      stubFor(
        get(urlEqualTo("/reports/repositories"))
          .willReturn(aResponse().withStatus(502))
      )

      leakDetectionConnector.repositoriesWithLeaks.futureValue shouldBe Seq.empty

      verify(getRequestedFor(urlEqualTo("/reports/repositories")))
    }
  }
}
