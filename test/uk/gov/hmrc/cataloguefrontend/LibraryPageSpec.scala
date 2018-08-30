/*
 * Copyright 2018 HM Revenue & Customs
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
import org.jsoup.Jsoup
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws._
import uk.gov.hmrc.play.test.UnitSpec

class LibraryPageSpec extends UnitSpec with BeforeAndAfter with GuiceOneServerPerSuite with WireMockEndpoints {

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.teams-and-repositories.host"   -> host,
      "microservice.services.teams-and-repositories.port"   -> endpointPort,
      "microservice.services.indicators.port"           -> endpointPort,
      "microservice.services.indicators.host"           -> host,
      "microservice.services.service-dependencies.host" -> host,
      "microservice.services.service-dependencies.port" -> endpointPort,
      "microservice.services.leak-detection.port"       -> endpointPort,
      "microservice.services.leak-detection.host"       -> host,
      "play.http.requestHandler"                        -> "play.api.http.DefaultHttpRequestHandler"
    )
    .build()

  private[this] val WS = app.injector.instanceOf[WSClient]

  override def beforeEach(): Unit = {
    super.beforeEach()
    serviceEndpoint(GET, "/reports/repositories", willRespondWith = (200, Some("[]")))
  }

  "A library page" should {

    "return a 404 when teams and services returns a 404" in {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (404, None))

      val response = await(WS.url(s"http://localhost:$port/services/serv").get)
      response.status shouldBe 404
    }

    "return a 404 when a Service is viewed as a Library" in {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (200, Some(serviceData)))

      val response = await(WS.url(s"http://localhost:$port/library/serv").get)
      response.status shouldBe 404
    }

    "show the teams owning the service with github and ci links and info box" in {

      serviceEndpoint(GET, "/api/repositories/lib", willRespondWith = (200, Some(libraryData)))

      val response = await(WS.url(s"http://localhost:$port/library/lib").get)
      response.status shouldBe 200
      response.body   should include(s"links on this page are automatically generated")
      response.body   should include(s"teamA")
      response.body   should include(s"teamB")
      response.body   should include(s"ci open 1")
      response.body   should include(s"ci open 2")
      response.body   should include(s"github.com")
      response.body   should include(s"http://ci.open1/lib")
      response.body   should include(s"http://ci.open2/lib")
      response.body   should not include "service1"
      response.body   should not include "service1"
      response.body   should not include "http://ser1/serv"
      response.body   should not include "http://ser2/serv"
    }

    "Render dependencies with red, green, amber and grey colours" in {

      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith                      = (200, Some(libraryData)))
      serviceEndpoint(GET, "/api/service-dependencies/dependencies/service-name", willRespondWith = (200, None))

      val response = await(WS.url(s"http://localhost:$port/library/service-name").get)

      val document = Jsoup.parse(response.body)

      document.select("#platform-dependencies").size() shouldBe 1

    }

  }

  val serviceData: String =
    """
      |    {
      |	     "name": "serv",
      |      "isPrivate": false,
      |      "repoType": "Service",
      |      "owningTeams": [ "The True Owners" ],
      |      "teamNames": ["teamA", "teamB"],
      |      "description": "some description",
      |      "createdAt": 1456326530000,
      |      "lastActive": 1478602555000,
      |	     "githubUrl": {
      |		     "name": "github",
      |        "displayName": "github.com",
      |		     "url": "https://github.com/hmrc/serv"
      |	     },
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

  val libraryData: String =
    """
      |    {
      |	     "name": "lib",
      |      "isPrivate": false,
      |      "description": "some description",
      |      "createdAt": 1456326530000,
      |      "lastActive": 1478602555000,
      |      "repoType": "Library",
      |      "owningTeams": [ "The True Owners" ],
      |      "teamNames": ["teamA", "teamB"],
      |	     "githubUrl": {
      |		     "name": "github",
      |        "displayName": "github.com",
      |		     "url": "https://github.com/hmrc/lib"
      |	     },
      |	     "ci": [
      |		     {
      |		       "name": "open1",
      |		       "displayName": "ci open 1",
      |		       "url": "http://ci.open1/lib"
      |		     },
      |		     {
      |		       "name": "open2",
      |		       "displayName": "ci open 2",
      |		       "url": "http://ci.open2/lib"
      |		     }
      |	     ]
      |     }
    """.stripMargin

  val indicatorData: String =
    """
      |[
      |  {
      |    "period":"2015-11",
      |    "leadTime":{
      |      "median":6
      |    },
      |    "interval":{
      |      "median":1
      |    }
      |  },
      |  {
      |    "period":"2015-12",
      |    "leadTime":{
      |      "median":6
      |    },
      |    "interval":{
      |      "median":5
      |    }
      |  },
      |  {
      |    "period":"2016-01",
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
