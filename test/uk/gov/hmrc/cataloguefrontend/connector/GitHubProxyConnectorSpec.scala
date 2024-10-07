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
import uk.gov.hmrc.cataloguefrontend.model.Version

import scala.concurrent.ExecutionContext.Implicits.global
import java.time.Instant

class GitHubProxyConnectorSpec
  extends AnyWordSpec
     with Matchers
     with OptionValues
     with WireMockSupport
     with HttpClientV2Support
     with ScalaFutures:

  private lazy val gitHubProxyConnector =
    GitHubProxyConnector(
      httpClientV2 = httpClientV2,
      ServicesConfig(Configuration(
        "microservice.services.platops-github-proxy.port" -> wireMockPort,
        "microservice.services.platops-github-proxy.host" -> wireMockHost
      ))
    )

  given HeaderCarrier = HeaderCarrier()

  "getGitHubProxyRaw" should:
    "return response body as a String for a valid repo" in:
      val rawRepositoryContent = "Raw Repository Content"

      stubFor:
        get(urlEqualTo("/platops-github-proxy/github-raw/foo"))
          .willReturn(aResponse().withStatus(200).withBody(rawRepositoryContent))

      gitHubProxyConnector
        .getGitHubProxyRaw("/foo")
        .futureValue
        .value shouldBe rawRepositoryContent

    "return None when repo Not Found" in:
      stubFor:
        get(urlEqualTo("/platops-github-proxy/github-raw/foo-non-existing"))
          .willReturn(aResponse().withStatus(404))

      gitHubProxyConnector
        .getGitHubProxyRaw("/foo-non-existing")
        .futureValue shouldBe None

    "return a failed future with exception message when a bad request occurs" in:
      val responseBody = "Error Response"

      stubFor:
        get(urlEqualTo("/platops-github-proxy/github-raw/foo"))
          .willReturn(aResponse().withStatus(500).withBody(responseBody))

      val exception =
        gitHubProxyConnector
          .getGitHubProxyRaw("/foo")
          .failed
          .futureValue

      exception shouldBe a[RuntimeException]
      exception.getMessage should include(responseBody)

  "compare" should:
    val commits =
      GitHubProxyConnector.Commit(
        author   = "Shnick"
      , date    =  Instant.parse("2023-11-29T19:48:59Z")
      , message = "BDOG-2800 fix play 30 test"
      , htmlUrl = "https://github.com/hmrc/platops-example-frontend-microservice/commit/da8d2710545e39c44994f0b4621b118d22ae91d2"
      ) ::
      GitHubProxyConnector.Commit(
        author  = "Shnick"
      , date    = Instant.parse("2023-11-30T09:35:46Z")
      , message = "Merge pull request #30 from hmrc/BDOG-2800\n\nBDOG-2800 fix play 30 test"
      , htmlUrl = "https://github.com/hmrc/platops-example-frontend-microservice/commit/99ee4985b7d2b8a12cad3de850fd5c044085c9f4"
      ) :: Nil

    "compare v2.358.0...v2.359.0" in:
      stubFor:
        get(urlEqualTo("/platops-github-proxy/github-rest/platops-example-frontend-microservice/compare/v2.358.0...v2.359.0"))
          .willReturn(aResponse().withStatus(200).withBodyFile("github-compare.json"))

      gitHubProxyConnector
        .compare("platops-example-frontend-microservice", Version("2.358.0"), Version("2.359.0"))
        .futureValue shouldBe Some(GitHubProxyConnector.Compare(
          aheadBy      = 2
        , behindBy     = 0
        , totalCommits = 2
        , commits      = commits
        , htmlUrl      = "https://github.com/hmrc/platops-example-frontend-microservice/compare/v2.358.0...v2.359.0"
        ))

    "compare v2.359.0...v2.358.0 - by swapping versions and manipulating commit counts" in:
      stubFor:
        get(urlEqualTo("/platops-github-proxy/github-rest/platops-example-frontend-microservice/compare/v2.358.0...v2.359.0"))
          .willReturn(aResponse().withStatus(200).withBodyFile("github-compare.json"))

      gitHubProxyConnector
        .compare("platops-example-frontend-microservice", Version("2.359.0"), Version("2.358.0"))
        .futureValue shouldBe Some(GitHubProxyConnector.Compare(
          aheadBy      = 0
        , behindBy     = 2
        , totalCommits = 2
        , commits      = commits
        , htmlUrl      = "https://github.com/hmrc/platops-example-frontend-microservice/compare/v2.358.0...v2.359.0"
        ))
