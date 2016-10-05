/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest.{TestData, _}
import org.scalatestplus.play.OneServerPerTest
import play.api.libs.ws.WS
import play.api.test.FakeApplication
import uk.gov.hmrc.cataloguefrontend.JsonData._
import uk.gov.hmrc.play.test.UnitSpec

class ServicePageSpec extends UnitSpec with BeforeAndAfter with OneServerPerTest with WireMockEndpoints {

  override def newAppForTest(testData: TestData): FakeApplication = new FakeApplication(
    additionalConfiguration = Map(
      "microservice.services.teams-and-services.port" -> endpointPort,
      "microservice.services.teams-and-services.host" -> host,
      "microservice.services.indicators.port" -> endpointPort,
      "microservice.services.indicators.host" -> host
    ))

  "A service page" should {

    "return a 404 when teams and services returns a 404" in {
      serviceEndpoint(GET, "/api/services/serv", willRespondWith = (404, None))

      val response = await(WS.url(s"http://localhost:$port/repositories/serv").get)
      response.status shouldBe 404
    }

    "return a 404 when a Library is viewed as a service" in {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (200, Some(libraryDetailsData)))

      val response = await(WS.url(s"http://localhost:$port/services/serv").get)
      response.status shouldBe 404
    }

    "show the teams owning the service with github, ci and environment links and info box" in {

      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (200, Some(serviceDetailsData)))
      serviceEndpoint(GET, "/api/indicators/service/serv/throughput", willRespondWith = (200, Some(deploymentThroughputData)))

      val response = await(WS.url(s"http://localhost:$port/services/serv").get)
      response.status shouldBe 200
      response.body should include(s"links on this page are automatically generated")
      response.body should include(s"teamA")
      response.body should include(s"teamB")
      response.body should include(s"open 1")
      response.body should include(s"open 2")
      response.body should include(s"service1")
      response.body should include(s"service1")
      response.body should include(s"github.com")
      response.body should include(s"http://open1/serv")
      response.body should include(s"http://open2/serv")
      response.body should include(s"http://ser1/serv")
      response.body should include(s"http://ser2/serv")

    }

    "Render the frequent production indicators graph with throughput and stability" in {
      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith = (200, Some(serviceDetailsData)))
      serviceEndpoint(GET, "/api/indicators/service/service-name/deployments", willRespondWith = (200, Some(deploymentThroughputData)))

      val response = await(WS.url(s"http://localhost:$port/services/service-name?stability").get)
      response.status shouldBe 200
      response.body should include(s"""data.addColumn('string', 'Period');""")
      response.body should include(s"""data.addColumn('number', 'Lead time');""")
      response.body should include(s"""data.addColumn('number', 'Interval');""")

      response.body should include(s"""data.addColumn({'type': 'string', 'role': 'tooltip', 'p': {'html': true}});""")
      response.body should include(s"""data.addColumn('number', 'Interval');""")
      response.body should include(s"""data.addColumn({'type': 'string', 'role': 'tooltip', 'p': {'html': true}});""")

      response.body should include(s"""chart.draw(data, options);""")

      response.body should include(s"""data.addColumn('string', 'Period');""")
      response.body should include(s"""data.addColumn('number', "Hotfix rate");""")
      response.body should include(s"""data.addColumn({'type': 'string', 'role': 'tooltip', 'p': {'html': true}});""")
    }

    "Render the frequent production indicators graph with throughput only when stability feature toggle is not present" in {
      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith = (200, Some(serviceDetailsData)))
      serviceEndpoint(GET, "/api/indicators/service/service-name/deployments", willRespondWith = (200, Some(deploymentThroughputData)))

      val response = await(WS.url(s"http://localhost:$port/services/service-name").get)
      response.status shouldBe 200
      response.body should include(s"""new google.visualization.LineChart(document.getElementById('chart_div'));""")
      response.body should not include(ViewMessages.fprExplanationText)
      response.body should not include (s"""new google.visualization.LineChart(document.getElementById('chart_div_2'));""")
      response.body should include(ViewMessages.fprExplanationTextWithoutStability)
    }

    "Render a message if the indicators service returns 404" in {
      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith = (200, Some(serviceDetailsData)))
      serviceEndpoint(GET, "/api/indicators/service/service-name/throughput", willRespondWith = (404, None))

      val response = await(WS.url(s"http://localhost:$port/services/service-name").get)
      response.status shouldBe 200
      response.body should include(s"""No data to show""")
      response.body should include(ViewMessages.noIndicatorsData)

      response.body shouldNot include(s"""chart.draw(data, options);""")
    }

    "Render a message if the indicators service encounters and error" in {
      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith = (200, Some(serviceDetailsData)))
      serviceEndpoint(GET, "/api/indicators/service/service-name/deployments", willRespondWith = (500, None))

      val response = await(WS.url(s"http://localhost:$port/services/service-name").get)
      response.status shouldBe 200
      response.body should include(s"""The catalogue encountered an error""")
      response.body should include(ViewMessages.indicatorsServiceError)

      response.body shouldNot include(s"""chart.draw(data, options);""")
    }
  }


}
