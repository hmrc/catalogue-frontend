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
import org.mockito.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.ws.{WSClient, WSResponse}
import uk.gov.hmrc.cataloguefrontend.FakeApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier

class HealthIndicatorsControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with FakeApplicationBuilder with OptionValues with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  "HealthIndicatorsController.indicatorsForRepo()" should {
    "respond with status 200 and contain specified elements" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/team-indicator-dashboard-frontend",
        willRespondWith = (
          200,
          Some(testJson)
        ))

      private val response: WSResponse =
        ws.url(s"http://localhost:$port/health-indicators/team-indicator-dashboard-frontend").get.futureValue

      response.status shouldBe 200
      response.body   should include("""frontend-bootstrap - Critical security upgrade: [CVE](https://confluence.tools.tax.service.gov.uk/x/sNukC)""")
      response.body   should include("""<td id="section_2_row_0_col_2">No Readme defined</td>""")
    }

    "respond with status 404 when repository is not found" in new Setup {
      serviceEndpoint(
        GET,
        "health-indicators/repositories/team-indicator-dashboard-frontend",
        willRespondWith = (
          404,
          None
        ))

      private val response: WSResponse =
        ws.url(s"http://localhost:$port/service/team-indicator-dashboard-frontend/health-indicators").get.futureValue

      response.status shouldBe 404
    }
  }

  "HealthIndicatorsController.indicatorsForAllRepos" should {
    "respond with status 200 and include all repos when no filters are set" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/?sort=desc",
        willRespondWith = (200, Some(testJson3Repo))
      )

      serviceEndpoint(
        GET,
        "/api/teams_with_repositories",
        willRespondWith = (200, Some(teamsJSON))
      )

      private val response: WSResponse =
        ws.url(s"http://localhost:$port/health-indicators").get.futureValue

      response.status shouldBe 200
      response.body   should include("""<a href="/health-indicators/team-indicator-dashboard-frontend">team-indicator-dashboard-frontend</a>""")
      response.body   should include("""<a href="/health-indicators/api-platform-scripts">api-platform-scripts</a>""")
      response.body   should include("""<a href="/health-indicators/the-childcare-service-prototype">the-childcare-service-prototype</a>""")
    }

    "respond with status 200 and include only services when repo_type=Service" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/?sort=desc",
        willRespondWith = (200, Some(testJson3Repo))
      )

      serviceEndpoint(
        GET,
        "/api/teams_with_repositories",
        willRespondWith = (200, Some(teamsJSON))
      )

      private val response: WSResponse =
        ws.url(s"http://localhost:$port/health-indicators?repo_type=Service").get.futureValue

      response.status shouldBe 200
      response.body   should include("""<a href="/health-indicators/team-indicator-dashboard-frontend">team-indicator-dashboard-frontend</a>""")
      response.body   shouldNot include("""<a href="/health-indicators/api-platform-scripts">api-platform-scripts</a>""")
      response.body   shouldNot include("""<a href="/health-indicators/the-childcare-service-prototype">the-childcare-service-prototype</a>""")
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
    lazy val ws                    = app.injector.instanceOf[WSClient]
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val testJson: String =
      """{
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
          |    }
          |  ]
          |}""".stripMargin

    val testJson3Repo: String =
      """[{
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

    val teamsJSON: String = """[{
                      |"name": "BTA",
                      |"repos": {
                      |"Service": [
                      |"add-taxes-frontend",
                      |"bta-stubs",
                      |"business-tax-account",
                      |"business-tax-prototype-hmrc-toolkit",
                      |"cesa-stub",
                      |"corporation-tax-frontend",
                      |"ct",
                      |"help-and-contact-frontend",
                      |"sa",
                      |"tax-account-router-frontend",
                      |"vat",
                      |"vat-frontend"
                      |],
                      |"Library": [
                      |"play-health",
                      |"tar-acceptance-tests"
                      |],
                      |"Prototype": [
                      |"add-manage-BTA-services-prototype",
                      |"bta-cards-prototype",
                      |"bta-design-prototype",
                      |"bta-london-prototype",
                      |"bta-prototype",
                      |"business-tax-prototype"
                      |],
                      |"Other": [
                      |"add-taxes-acceptance-tests",
                      |"alert-config",
                      |"app-config-base",
                      |"app-config-development",
                      |"app-config-externaltest",
                      |"app-config-production",
                      |"app-config-qa",
                      |"app-config-staging",
                      |"bobby-config",
                      |"build-jobs",
                      |"business-tax-account-acceptance-tests",
                      |"business-tax-account-dashboard",
                      |"business-tax-account-performance-tests",
                      |"corporation-tax-performance-tests",
                      |"grafana-dashboards",
                      |"hmrc-stubs-core",
                      |"jenkins-config",
                      |"jenkins-jobs",
                      |"kibana-dashboards",
                      |"mdtp-frontend-routes",
                      |"outage-pages",
                      |"performance-test-jobs",
                      |"play-sso-out",
                      |"self-assessment-performance-tests",
                      |"service-manager-config",
                      |"tar-performance-tests",
                      |"vat-performance-tests",
                      |"yta-dashboard"
                      |]
                      |},
                      |"ownedRepos": []
                      |}]""".stripMargin
  }
}
