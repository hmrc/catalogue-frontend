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
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class GitHubProxyConnectorSpec
  extends AnyWordSpec
     with Matchers
     with OptionValues
     with WireMockSupport
     with HttpClientV2Support
     with ScalaFutures {

  private lazy val gitHubProxyConnector =
    GitHubProxyConnector(
      httpClientV2   = httpClientV2,
      ServicesConfig(Configuration(
        "microservice.services.platops-github-proxy.port" -> wireMockPort,
        "microservice.services.platops-github-proxy.host" -> wireMockHost
      ))
    )

  given HeaderCarrier = HeaderCarrier()

  "getGitHubProxyRaw" should {

    val rawRepositoryContent = "Raw Repository Content"

    "return response body as a String for a valid repo" in {
      stubFor(
        get(urlEqualTo("/platops-github-proxy/github-raw/foo"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(rawRepositoryContent)
          )
      )

      val response = gitHubProxyConnector
        .getGitHubProxyRaw("/foo")
        .futureValue
        .value

      response shouldBe rawRepositoryContent
    }

    "return None when repo Not Found" in {
      stubFor(
        get(urlEqualTo("/platops-github-proxy/github-raw/foo-non-existing"))
          .willReturn(aResponse().withStatus(404)))

      val response = gitHubProxyConnector
        .getGitHubProxyRaw("/foo-non-existing")
        .futureValue

      response shouldBe None
    }

    "return a failed future with exception message when a bad request occurs" in {

      val responseBody = "Error Response"

      stubFor(
        get(urlEqualTo("/platops-github-proxy/github-raw/foo"))
          .willReturn(aResponse().withStatus(500).withBody(responseBody))
      )

      val exception = gitHubProxyConnector
        .getGitHubProxyRaw("/foo")
        .failed
        .futureValue

      exception shouldBe a[RuntimeException]
      exception.getMessage should include(responseBody)
    }
  }
}
