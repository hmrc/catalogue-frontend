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

package uk.gov.hmrc.cataloguefrontend.healthindicators

import com.github.tomakehurst.wiremock.http.RequestMethod.GET
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.FakeApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.healthindicators.MetricType.{BobbyRule, BuildStability, LeakDetection, ReadMe}
import uk.gov.hmrc.http.HeaderCarrier

class HealthIndicatorsConnectorSpec
  extends AnyWordSpec
    with Matchers
    with OptionValues
    with FakeApplicationBuilder{

  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()


  private lazy val healthIndicatorsConnector = app.injector.instanceOf[HealthIndicatorsConnector]

  "getIndicator()" should {
    "return a indicator for a repo when given a valid repo name" in {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/team-indicator-dashboard-frontend",
        willRespondWith = (200, Some(testJson1Repo))
      )

      val response = healthIndicatorsConnector.getIndicator("team-indicator-dashboard-frontend")
        .futureValue.value

      val weightedMetrics = Seq(
        WeightedMetric(
          BobbyRule,
          -400,
          Seq(
            Breakdown(-100, "frontend-bootstrap - Bug in Metrics Reporting", None),
            Breakdown(-100, "frontend-bootstrap - Critical security upgrade: [CVE](https://confluence.tools.tax.service.gov.uk/x/sNukC)", None)
          )
        ),
        WeightedMetric(LeakDetection, 0, Seq()),
        WeightedMetric(ReadMe, -50, Seq(Breakdown(-50, "No Readme defined", None))),
        WeightedMetric(BuildStability, 0, Seq(Breakdown(0, "Build Not Found", None)))
      )

      val expectedResponse = Indicator("team-indicator-dashboard-frontend",RepoType.Service, -450, weightedMetrics)

      response shouldBe expectedResponse
    }

    "return None when repo is not found" in {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/team-indicator-dashboard-frontend",
        willRespondWith = (
          404,
          None
        ))

      val response = healthIndicatorsConnector
        .getIndicator("team-indicator-dashboard-frontend")
        .futureValue

      response shouldBe None
    }
  }

  "getAllIndicators()" should {
    "return a list of indicators" in {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/?sort=desc",
        willRespondWith = (200, Some(testJson3Repo))
      )

      val response = healthIndicatorsConnector.getAllIndicators(RepoType.AllTypes)
        .futureValue

      val expectedResponse = Seq(
        Indicator("team-indicator-dashboard-frontend",RepoType.Service, -450, Seq.empty),
        Indicator("api-platform-scripts",  RepoType.Other, 50, Seq.empty),
        Indicator("the-childcare-service-prototype", RepoType.Prototype, 50, Seq.empty)
      )

      response shouldBe expectedResponse
    }
  }

  private val testJson1Repo: String = """{
                   |  "repoName": "team-indicator-dashboard-frontend",
                   |  "repoType": "Service",
                   |  "overallScore": -450,
                   |  "weightedMetrics": [
                   |    {
                   |      "metricType": "bobby-rule",
                   |      "score": -400,
                   |      "breakdown": [
                   |        {
                   |          "points": -100,
                   |          "description": "frontend-bootstrap - Bug in Metrics Reporting"
                   |        },
                   |        {
                   |          "points": -100,
                   |          "description": "frontend-bootstrap - Critical security upgrade: [CVE](https://confluence.tools.tax.service.gov.uk/x/sNukC)"
                   |        }
                   |      ]
                   |    },
                   |    {
                   |      "metricType": "leak-detection",
                   |      "score": 0,
                   |      "breakdown": []
                   |    },
                   |    {
                   |      "metricType": "read-me",
                   |      "score": -50,
                   |      "breakdown": [
                   |        {
                   |          "points": -50,
                   |          "description": "No Readme defined"
                   |        }
                   |      ]
                   |    },
                   |{
                   |      "metricType": "build-stability",
                   |      "score": 0,
                   |      "breakdown": [
                   |        {
                   |          "points": 0,
                   |          "description": "Build Not Found"
                   |        }
                   |      ]
                   |    }
                   |  ]
                   |}""".stripMargin


  private val testJson3Repo: String = """[{
                  |  "repoName": "team-indicator-dashboard-frontend",
                  |  "repoType": "Service",
                  |  "overallScore": -450,
                  |  "weightedMetrics": []
                  |},
                  |{
                  | "repoName": "api-platform-scripts",
                  | "repoType": "Other",
                  | "overallScore": 50,
                  | "weightedMetrics": []
                  |},
                  |{
                  | "repoName": "the-childcare-service-prototype",
                  | "repoType": "Prototype",
                  | "overallScore": 50,
                  | "weightedMetrics": []
                  |}]""".stripMargin
}
