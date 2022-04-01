/*
 * Copyright 2022 HM Revenue & Customs
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
import org.mockito.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.FakeApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant

class HealthIndicatorsControllerSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with FakeApplicationBuilder
     with OptionValues
     with ScalaFutures
     with IntegrationPatience {

  override def beforeEach(): Unit = {
    super.beforeEach()
    setupAuthEndpoint()
  }

  "HealthIndicatorsController.breakdownForRepo()" should {
    "respond with status 200 and contain specified elements" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/indicators/team-indicator-dashboard-frontend",
        willRespondWith = (
          200,
          Some(testJson)
        ))

      val response = wsClient.url(s"http://localhost:$port/health-indicators/team-indicator-dashboard-frontend").withAuthToken("Token token").get.futureValue
      response.status shouldBe 200
      response.body   should include("""frontend-bootstrap - Critical security upgrade: [CVE](https://confluence.tools.tax.service.gov.uk/x/sNukC)""")
      response.body   should include("""<td id="section_2_row_0_col_2">No Readme defined</td>""")
    }

    "respond with status 404 when repository is not found" in new Setup {
      serviceEndpoint(
        GET,
        "health-indicators/indicators/team-indicator-dashboard-frontend",
        willRespondWith = (
          404,
          None
        ))

      val response = wsClient.url(s"http://localhost:$port/service/team-indicator-dashboard-frontend/health-indicators").withAuthToken("Token token").get.futureValue
      response.status shouldBe 404
    }
  }

  "HealthIndicatorsController.indicatorsForRepoType" should {
    "respond with status 200 and default to repoType=Service when no query params set" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/indicators?sort=desc&repoType=Service",
        willRespondWith = (200, Some(testJsonRepoTypeService))
      )

      serviceEndpoint(
        GET,
        "/api/v2/repositories",
        willRespondWith = (200, Some(repositoriesJson))
      )

      serviceEndpoint(
        GET,
        "/api/v2/teams",
        willRespondWith = (200, Some(teamsJSON))
      )

      val response = wsClient.url(s"http://localhost:$port/health-indicators").withAuthToken("Token token").get.futureValue
      response.status shouldBe 200
      response.body should include("""<a href="/health-indicators/team-indicator-dashboard-frontend"><span class="repoName">team-indicator-dashboard-frontend</span></a>""")
    }

    "respond with status 200 and include repo type service when repoType=Service" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/indicators?sort=desc&repoType=Service",
        willRespondWith = (200, Some(testJsonRepoTypeService))
      )

      serviceEndpoint(
        GET,
        "/api/v2/repositories",
        willRespondWith = (200, Some(repositoriesJson))
      )

      serviceEndpoint(
        GET,
        "/api/v2/teams",
        willRespondWith = (200, Some(teamsJSON))
      )

      val response = wsClient.url(s"http://localhost:$port/health-indicators?repoType=Service").withAuthToken("Token token").get.futureValue
      response.status shouldBe 200
      response.body should include("""<a href="/health-indicators/team-indicator-dashboard-frontend"><span class="repoName">team-indicator-dashboard-frontend</span></a>""")
    }

    "respond with status 200 and include all repo types when repoType=AllTypes" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/indicators?sort=desc",
        willRespondWith = (200, Some(testJson3RepoTypes))
      )

      serviceEndpoint(
        GET,
        "/api/v2/repositories",
        willRespondWith = (200, Some(repositoriesJson))
      )

      serviceEndpoint(
        GET,
        "/api/v2/teams",
        willRespondWith = (200, Some(teamsJSON))
      )

      val response = wsClient.url(s"http://localhost:$port/health-indicators?repoType=All+Types").withAuthToken("Token token").get.futureValue
      response.status shouldBe 200
      response.body should include("""<a href="/health-indicators/team-indicator-dashboard-frontend"><span class="repoName">team-indicator-dashboard-frontend</span></a>""")
      response.body should include("""<a href="/health-indicators/api-platform-scripts"><span class="repoName">api-platform-scripts</span></a>""")
      response.body should include("""<a href="/health-indicators/the-childcare-service-prototype"><span class="repoName">the-childcare-service-prototype</span></a>""")
    }

    "respond with status 200 and include repo type other when repoType=Other" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/indicators?sort=desc&repoType=Other",
        willRespondWith = (200, Some(testJsonRepoTypeOther))
      )

      serviceEndpoint(
        GET,
        "/api/v2/repositories",
        willRespondWith = (200, Some(repositoriesJson))
      )

      serviceEndpoint(
        GET,
        "/api/v2/teams",
        willRespondWith = (200, Some(teamsJSON))
      )

      val response = wsClient.url(s"http://localhost:$port/health-indicators?repoType=Other").withAuthToken("Token token").get.futureValue
      response.status shouldBe 200
      response.body should include("""<a href="/health-indicators/api-platform-scripts"><span class="repoName">api-platform-scripts</span></a>""")
    }

    "respond with status 200 and include repo type prototype when repoType=Prototype" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/indicators?sort=desc&repoType=Prototype",
        willRespondWith = (200, Some(testJsonRepoTypePrototype))
      )

      serviceEndpoint(
        GET,
        "/api/v2/repositories",
        willRespondWith = (200, Some(repositoriesJson))
      )

      serviceEndpoint(
        GET,
        "/api/v2/teams",
        willRespondWith = (200, Some(teamsJSON))
      )

      val response = wsClient.url(s"http://localhost:$port/health-indicators?repoType=Prototype").withAuthToken("Token token").get.futureValue
      response.status shouldBe 200
      response.body should include("""<a href="/health-indicators/the-childcare-service-prototype"><span class="repoName">the-childcare-service-prototype</span></a>""")
    }

    "filter by team" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/indicators?sort=desc",
        willRespondWith = (200, Some(testJson3RepoTypes))
      )

      serviceEndpoint(
        GET,
        "/api/v2/repositories",
        willRespondWith = (200, Some(repositoriesJson))
      )

      serviceEndpoint(
        GET,
        "/api/v2/teams",
        willRespondWith = (200, Some(teamsJSON))
      )

      val response = wsClient.url(s"http://localhost:$port/health-indicators?team=Classic+Services+Manchester&repoType=All+Types").withAuthToken("Token token").get.futureValue
      response.status shouldBe 200
      response.body should include("""<a href="/health-indicators/team-indicator-dashboard-frontend"><span class="repoName">team-indicator-dashboard-frontend</span></a>""")
      response.body shouldNot include("""<a href="/health-indicators/api-platform-scripts"><span class="repoName">api-platform-scripts</span></a>""")
      response.body shouldNot include("""<a href="/health-indicators/the-childcare-service-prototype"><span class="repoName">the-childcare-service-prototype</span></a>""")
    }
  }

  "getScoreColour()" should {
    "return correct class string" in {
      HealthIndicatorsController.getScoreColour(100)  shouldBe "repo-score-green"
      HealthIndicatorsController.getScoreColour(0)    shouldBe "repo-score-amber"
      HealthIndicatorsController.getScoreColour(-100) shouldBe "repo-score-red"
    }
  }

  private[this] trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val testJson: String =
      """{
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
          |      "metricType": "github",
          |      "score": -50,
          |      "breakdown": [
          |        {
          |          "points": -50,
          |          "description": "No Readme defined"
          |        }
          |      ]
          |    }
          |  ]
          |}""".stripMargin

    val testJson3RepoTypes: String =
      """[{
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

    val testJsonRepoTypeService: String =
      """[{
        |  "repoName": "team-indicator-dashboard-frontend",
        |  "repoType": "Service",
        |  "overallScore": -450,
        |  "weightedMetrics": []
        |}
        |]""".stripMargin

    val testJsonRepoTypeOther: String =
      """[
        |{
        | "repoName": "api-platform-scripts",
        | "repoType": "Other",
        | "overallScore": 50,
        | "weightedMetrics": []
        |}]""".stripMargin

    val testJsonRepoTypePrototype: String =
      """[
        |{
        | "repoName": "the-childcare-service-prototype",
        | "repoType": "Prototype",
        | "overallScore": 50,
        | "weightedMetrics": []
        |}]""".stripMargin

    val createdAt    = Instant.parse("2016-05-23T16:45:30.00Z")
    val lastActiveAt = Instant.parse("2016-10-12T10:30:12.00Z")


    val repositoriesJson: String = s"""[
                              | {
                              | "name":"team-indicator-dashboard-frontend",
                              | "description": "",
                              | "teamNames":["Classic Services Manchester","Classic Services Telford"],
                              | "createdDate":"$createdAt",
                              | "lastActiveDate":"$lastActiveAt",
                              | "repoType":"Service",
                              | "language":"Scala",
                              | "isArchived":false,
                              | "defaultBranch":"main",
                              | "isDeprecated":false,
                              | "url": "http://git/repoa"
                              | }
                              |]""".stripMargin

    val teamsJSON: String = """[{
                              |  "name": "Classic Services Manchester",
                              |  "createdDate": "2020-10-28T13:15:19Z",
                              |  "lastActiveDate": "2021-07-09T10:00:49Z",
                              |  "repos": 7
                              |},{
                              |  "name": "Classic Services Telford",
                              |  "createdDate": "2020-10-28T13:15:19Z",
                              |  "lastActiveDate": "2021-07-09T10:00:49Z",
                              |  "repos": 7
                              |}]""".stripMargin
  }
}
