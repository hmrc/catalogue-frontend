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
import uk.gov.hmrc.play.test.UnitSpec

class ServicePageSpec extends UnitSpec with BeforeAndAfter with OneServerPerTest with WireMockEndpoints {

  override def newAppForTest(testData: TestData): FakeApplication = new FakeApplication(
    additionalConfiguration = Map(
      "microservice.services.teams-and-services.port" -> endpointPort,
      "microservice.services.indicators.port" -> endpointPort
    ))

  "A service page" should {

    "return a 404 when teams and services returns a 404" in {
      serviceEndpoint(GET, "/api/services/serv", willRespondWith = (404, None))

      val response = await(WS.url(s"http://localhost:$port/repositories/serv").get)
      response.status shouldBe 404
    }

    "return a 404 when a Library is viewed as a service" in {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (200, Some(libraryData)))

      val response = await(WS.url(s"http://localhost:$port/services/serv").get)
      response.status shouldBe 404
    }

    "show the teams owning the service with github, ci and environment links and info box" in {

      serviceEndpoint(GET, "/api/repositories/serv",willRespondWith = (200, Some(serviceData)))
      serviceEndpoint(GET, "/api/indicators/service/serv/fpr",willRespondWith = (200, Some(indicatorData)))

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

    "Render the frequent production indicators graph" in {
      serviceEndpoint(GET, "/api/repositories/service-name",willRespondWith = (200, Some(serviceData)))
      serviceEndpoint(GET, "/api/indicators/service/service-name/fpr",willRespondWith = (200, Some(indicatorData)))

      val response = await(WS.url(s"http://localhost:$port/services/service-name").get)
      response.status shouldBe 200
      response.body should include(s"""data.addColumn('string', 'Period');""")
      response.body should include(s"""data.addColumn('number', 'Lead time');""")
      response.body should include(s"""data.addColumn('number', 'Interval');""")

      response.body should include(s"""data.addColumn({'type': 'string', 'role': 'tooltip', 'p': {'html': true}});""")
      response.body should include(s"""data.addColumn('number', 'Interval');""")
      response.body should include(s"""data.addColumn({'type': 'string', 'role': 'tooltip', 'p': {'html': true}});""")

      response.body should include(s"""chart.draw(data, options);""")
    }

    "Render a message if the indicators service returns 404" in {
      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith = (200, Some(serviceData)))
      serviceEndpoint(GET, "/api/indicators/service/service-name/fpr",willRespondWith = (404, None))

      val response = await(WS.url(s"http://localhost:$port/services/service-name").get)
      response.status shouldBe 200
      response.body should include(s"""No data to show""")
      response.body should include(ViewMessages.noIndicatorsData)

      response.body shouldNot include(s"""chart.draw(data, options);""")
    }

    "Render a message if the indicators service encounters and error" in {
      serviceEndpoint(GET, "/api/repositories/service-name",willRespondWith = (200, Some(serviceData)))
      serviceEndpoint(GET, "/api/indicators/service/service-name/fpr", willRespondWith = (500, None))

      val response = await(WS.url(s"http://localhost:$port/services/service-name").get)
      response.status shouldBe 200
      response.body should include(s"""The catalogue encountered an error""")
      response.body should include(ViewMessages.indicatorsServiceError)

      response.body shouldNot include(s"""chart.draw(data, options);""")
    }
  }

  val serviceData =
    """
      |    {
      |	     "name": "serv",
      |      "repoType": "Deployable",
      |      "teamNames": ["teamA", "teamB"],
      |	     "githubUrls": [{
      |		     "name": "github",
      |        "displayName": "github.com",
      |		     "url": "https://github.com/hmrc/serv"
      |	     }],
      |	     "ci": [
      |		     {
      |		       "name": "open1",
      |		       "displayName": "open 1",
      |		       "url": "http://open1/serv"
      |		     },
      |		     {
      |		       "name": "open2",
      |		       "displayName": "open 2",
      |		       "url": "http://open2/serv"
      |		     }
      |	     ],
      |      "environments" : [{
      |        "name" : "env1",
      |        "services" : [{
      |          "name": "ser1",
      |		       "displayName": "service1",
      |          "url": "http://ser1/serv"
      |        }, {
      |          "name": "ser2",
      |		       "displayName": "service2",
      |          "url": "http://ser2/serv"
      |        }]
      |      },{
      |        "name" : "env2",
      |        "services" : [{
      |          "name": "ser1",
      |		       "displayName": "service1",
      |          "url": "http://ser1/serv"
      |        }, {
      |          "name": "ser2",
      |		       "displayName": "service2",
      |          "url": "http://ser2/serv"
      |        }]
      |       }]
      |     }
    """.stripMargin

  val libraryData =
    """
      |    {
      |	     "name": "serv",
      |      "repoType": "Library",
      |      "teamNames": ["teamA", "teamB"],
      |	     "githubUrls": [{
      |		     "name": "github",
      |        "displayName": "github.com",
      |		     "url": "https://github.com/hmrc/serv"
      |	     }],
      |	     "ci": [
      |		     {
      |		       "name": "open1",
      |		       "displayName": "open 1",
      |		       "url": "http://open1/serv"
      |		     },
      |		     {
      |		       "name": "open2",
      |		       "displayName": "open 2",
      |		       "url": "http://open2/serv"
      |		     }
      |	     ]
      |     }
    """.stripMargin

  val indicatorData =
    """
      |[
      |  {
      |    "period":"2015-11",
      |    "from": "2015-12-01",
      |    "to": "2016-02-29",
      |    "leadTime":{
      |      "median":6
      |    },
      |    "interval":{
      |      "median":1
      |    }
      |  },
      |  {
      |    "period":"2015-12",
      |    "from": "2015-12-01",
      |    "to": "2016-02-29",
      |    "leadTime":{
      |      "median":6
      |    },
      |    "interval":{
      |      "median":5
      |    }
      |  },
      |  {
      |    "period":"2016-01",
      |    "from": "2015-12-01",
      |    "to": "2016-02-29",
      |    "leadTime":{
      |      "median":6
      |    },
      |    "interval":{
      |      "median":6
      |    }
      |  }
      |]
    """.stripMargin
}
