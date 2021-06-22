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

  "HealthIndicatorsController.indicatorsForRepoType" should {
    "respond with status 200 and redirect to Services when no query params set" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/?sort=desc&repoType=Service",
        willRespondWith = (200, Some(testJsonRepoTypeService))
      )

      serviceEndpoint(
        GET,
        "/api/services?teamDetails=true",
        willRespondWith = (200, Some(teamsJSON))
      )

      private val response: WSResponse =
        ws.url(s"http://localhost:$port/health-indicators").get.futureValue

      response.status shouldBe 200
      response.body should include("""<a href="/health-indicators/team-indicator-dashboard-frontend">team-indicator-dashboard-frontend</a>""")
    }

    "respond with status 200 and include repo type service when repoType=Service" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/?sort=desc&repoType=Service",
        willRespondWith = (200, Some(testJsonRepoTypeService))
      )

      serviceEndpoint(
        GET,
        "/api/services?teamDetails=true",
        willRespondWith = (200, Some(teamsJSON))
      )

      private val response: WSResponse =
        ws.url(s"http://localhost:$port/health-indicators?repoType=Service").get.futureValue

      response.status shouldBe 200
      response.body should include("""<a href="/health-indicators/team-indicator-dashboard-frontend">team-indicator-dashboard-frontend</a>""")
    }

    "respond with status 200 and include all repo types when repoType=AllTypes" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/?sort=desc",
        willRespondWith = (200, Some(testJson3RepoTypes))
      )

      serviceEndpoint(
        GET,
        "/api/services?teamDetails=true",
        willRespondWith = (200, Some(teamsJSON))
      )

      private val response: WSResponse =
        ws.url(s"http://localhost:$port/health-indicators?repoType=All+Types").get.futureValue

      response.status shouldBe 200
      response.body should include("""<a href="/health-indicators/team-indicator-dashboard-frontend">team-indicator-dashboard-frontend</a>""")
      response.body should include("""<a href="/health-indicators/api-platform-scripts">api-platform-scripts</a>""")
      response.body should include("""<a href="/health-indicators/the-childcare-service-prototype">the-childcare-service-prototype</a>""")
    }

    "respond with status 200 and include repo type other when repoType=Other" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/?sort=desc&repoType=Other",
        willRespondWith = (200, Some(testJsonRepoTypeOther))
      )

      serviceEndpoint(
        GET,
        "/api/services?teamDetails=true",
        willRespondWith = (200, Some(teamsJSON))
      )

      private val response: WSResponse =
        ws.url(s"http://localhost:$port/health-indicators?repoType=Other").get.futureValue

      response.status shouldBe 200
      response.body should include("""<a href="/health-indicators/api-platform-scripts">api-platform-scripts</a>""")
    }

    "respond with status 200 and include repo type prototype when repoType=Prototype" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/?sort=desc&repoType=Prototype",
        willRespondWith = (200, Some(testJsonRepoTypePrototype))
      )

      serviceEndpoint(
        GET,
        "/api/services?teamDetails=true",
        willRespondWith = (200, Some(teamsJSON))
      )

      private val response: WSResponse =
        ws.url(s"http://localhost:$port/health-indicators?repoType=Prototype").get.futureValue

      response.status shouldBe 200
      response.body should include("""<a href="/health-indicators/the-childcare-service-prototype">the-childcare-service-prototype</a>""")
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


    val teamsJSON: String = """{"team-indicator-dashboard-frontend": [
                              |"Classic Services Manchester",
                              |"Classic Services Telford"
                              |]}""".stripMargin
  }
}
