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
import uk.gov.hmrc.cataloguefrontend.healthindicators.RatingType.{BobbyRule, BuildStability, LeakDetection, ReadMe}
import uk.gov.hmrc.cataloguefrontend.healthindicators.RepoType.AllTypes
import uk.gov.hmrc.http.HeaderCarrier

class HealthIndicatorsConnectorSpec
  extends AnyWordSpec
    with Matchers
    with OptionValues
    with FakeApplicationBuilder{

  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()


  private lazy val healthIndicatorsConnector = app.injector.instanceOf[HealthIndicatorsConnector]

  "getHealthIndicators()" should {
    "return a repository rating when given a valid repository name" in {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/team-indicator-dashboard-frontend",
        willRespondWith = (200, Some(testJson1Repo))
      )

      val response = healthIndicatorsConnector.getRepositoryRating("team-indicator-dashboard-frontend")
        .futureValue.value

      val ratings = Seq(
        Rating(
          BobbyRule,
          -400,
          Seq(
            Score(-100, "frontend-bootstrap - Bug in Metrics Reporting", None),
            Score(-100, "frontend-bootstrap - Critical security upgrade: [CVE](https://confluence.tools.tax.service.gov.uk/x/sNukC)", None)
          )
        ),
        Rating(LeakDetection, 0, Seq()),
        Rating(ReadMe, -50, Seq(Score(-50, "No Readme defined", None))),
        Rating(BuildStability, 0, Seq(Score(0, "Build Not Found", None)))
      )

      val expectedResponse = RepositoryRating("team-indicator-dashboard-frontend",RepoType.Service, -450, ratings)

      response shouldBe expectedResponse
    }

    "return None when repository is not found" in {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/team-indicator-dashboard-frontend",
        willRespondWith = (
          404,
          None
        ))

      val response = healthIndicatorsConnector
        .getRepositoryRating("team-indicator-dashboard-frontend")
        .futureValue

      response shouldBe None
    }
  }

  "getAllHealthIndicators()" should {
    "return a list of repository ratings" in {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/?sort=desc",
        willRespondWith = (200, Some(testJson3Repo))
      )

      val response = healthIndicatorsConnector.getAllRepositoryRatings(Some(RepoType.AllTypes))
        .futureValue

      val expectedResponse = Seq(
        RepositoryRating("team-indicator-dashboard-frontend",RepoType.Service, -450, Seq.empty),
        RepositoryRating("api-platform-scripts",  RepoType.Other, 50, Seq.empty),
        RepositoryRating("the-childcare-service-prototype", RepoType.Prototype, 50, Seq.empty)
      )

      response shouldBe expectedResponse
    }
  }

  private val testJson1Repo: String = """{
                   |  "repositoryName": "team-indicator-dashboard-frontend",
                   |  "repositoryType": "Service",
                   |  "repositoryScore": -450,
                   |  "ratings": [
                   |    {
                   |      "ratingType": "BobbyRule",
                   |      "ratingScore": -400,
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
                   |      "ratingType": "LeakDetection",
                   |      "ratingScore": 0,
                   |      "breakdown": []
                   |    },
                   |    {
                   |      "ratingType": "ReadMe",
                   |      "ratingScore": -50,
                   |      "breakdown": [
                   |        {
                   |          "points": -50,
                   |          "description": "No Readme defined"
                   |        }
                   |      ]
                   |    },
                   |{
                   |      "ratingType": "BuildStability",
                   |      "ratingScore": 0,
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
                  |  "repositoryName": "team-indicator-dashboard-frontend",
                  |  "repositoryType": "Service",
                  |  "repositoryScore": -450,
                  |  "ratings": []
                  |},
                  |{
                  | "repositoryName": "api-platform-scripts",
                  | "repositoryType": "Other",
                  | "repositoryScore": 50,
                  | "ratings": []
                  |},
                  |{
                  | "repositoryName": "the-childcare-service-prototype",
                  | "repositoryType": "Prototype",
                  | "repositoryScore": 50,
                  | "ratings": []
                  |}]""".stripMargin
}
