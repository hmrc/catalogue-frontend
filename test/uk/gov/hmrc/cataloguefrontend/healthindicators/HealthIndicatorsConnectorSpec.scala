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

package uk.gov.hmrc.cataloguefrontend.healthindicators

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.connector.RepoType
import uk.gov.hmrc.cataloguefrontend.healthindicators.MetricType.{GitHub, LeakDetection}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport

class HealthIndicatorsConnectorSpec
  extends AnyWordSpec
     with Matchers
     with OptionValues
     with GuiceOneAppPerSuite
     with WireMockSupport
     with ScalaFutures
     with IntegrationPatience {

  private given HeaderCarrier = HeaderCarrier()

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        Map(
          "microservice.services.health-indicators.port" -> wireMockPort,
          "microservice.services.health-indicators.host" -> wireMockHost,
        )
      )
      .build()

  private lazy val healthIndicatorsConnector = app.injector.instanceOf[HealthIndicatorsConnector]

  "getIndicator()" should {
    "return a indicator for a repo when given a valid repo name" in {
      stubFor(
        get(urlEqualTo("/health-indicators/indicators/team-indicator-dashboard-frontend"))
          .willReturn(aResponse().withBody(testJson1Repo))
      )

      val response = healthIndicatorsConnector.getIndicator("team-indicator-dashboard-frontend")
        .futureValue.value

      val weightedMetrics = Seq(
        WeightedMetric(LeakDetection , 0  , Seq()),
        WeightedMetric(GitHub        , -50, Seq(Breakdown(-50, "No Readme defined", None)))
      )

      val expectedResponse = Indicator("team-indicator-dashboard-frontend", RepoType.Service, -450, weightedMetrics)

      response shouldBe expectedResponse
    }

    "return None when repo is not found" in {
      stubFor(
        get(urlEqualTo("/health-indicators/indicators/team-indicator-dashboard-frontend"))
          .willReturn(aResponse().withStatus(404))
      )

      val response = healthIndicatorsConnector
        .getIndicator("team-indicator-dashboard-frontend")
        .futureValue

      response shouldBe None
    }
  }

  "getIndicators()" should {
    "return a list of indicators" in {
      stubFor(
        get(urlEqualTo("/health-indicators/indicators?sort=desc"))
          .willReturn(aResponse().withBody(testJson3Repo))
      )

      val response = healthIndicatorsConnector.getIndicators(None)
        .futureValue

      val expectedResponse = Seq(
        Indicator("team-indicator-dashboard-frontend", RepoType.Service  , -450, Seq.empty),
        Indicator("api-platform-scripts"             , RepoType.Other    , 50  , Seq.empty),
        Indicator("the-childcare-service-prototype"  , RepoType.Prototype, 50  , Seq.empty)
      )

      response shouldBe expectedResponse
    }
  }

  private val testJson1Repo: String =
    """{
        "repoName": "team-indicator-dashboard-frontend",
        "repoType": "Service",
        "overallScore": -450,
        "weightedMetrics": [
          {
            "metricType": "leak-detection",
            "score": 0,
            "breakdown": []
          },
          {
            "metricType": "github",
            "score": -50,
            "breakdown": [
              {
                "points": -50,
                "description": "No Readme defined"
              }
            ]
          }
        ]
      }"""


  private val testJson3Repo: String =
    """[{
        "repoName": "team-indicator-dashboard-frontend",
        "repoType": "Service",
        "overallScore": -450,
        "weightedMetrics": []
      },
      {
       "repoName": "api-platform-scripts",
       "repoType": "Other",
       "overallScore": 50,
       "weightedMetrics": []
      },
      {
       "repoName": "the-childcare-service-prototype",
       "repoType": "Prototype",
       "overallScore": 50,
       "weightedMetrics": []
      }]"""
}
