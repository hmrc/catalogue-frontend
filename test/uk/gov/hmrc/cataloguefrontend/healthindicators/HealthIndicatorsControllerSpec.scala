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
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSResponse}
import uk.gov.hmrc.cataloguefrontend.WireMockEndpoints
import uk.gov.hmrc.http.HeaderCarrier

class HealthIndicatorsControllerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with GuiceOneServerPerSuite
    with WireMockEndpoints
    with OptionValues
    with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(500, Millis))

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .configure(
        Map(
          "microservice.services.health-indicators.port" -> endpointPort,
          "microservice.services.health-indicators.host" -> host,
          "metrics.jvm"                                  -> false
        ))
      .build()

  "HealthIndicatorsController" should {
    "respond with status 200 and contain specified elements" in new Setup {
      serviceEndpoint(
        GET,
        "/health-indicators/repositories/team-indicator-dashboard-frontend",
        willRespondWith = (
          200,
          Some(testJson)
        ))

      private val response: WSResponse =
        ws.url(s"http://localhost:$port/service/team-indicator-dashboard-frontend/health-indicators").get.futureValue

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

  "getScoreColour()" should {
    "return correct class string" in {
      HealthIndicatorsController.getScoreColour(100) shouldBe "repo-score-green"
      HealthIndicatorsController.getScoreColour(0) shouldBe "repo-score-amber"
      HealthIndicatorsController.getScoreColour(-100) shouldBe "repo-score-red"
    }
  }

  private[this] trait Setup {
    lazy val ws                    = app.injector.instanceOf[WSClient]
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val testJson: String =
      """{
          |  "repositoryName": "team-indicator-dashboard-frontend",
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
  }
}
