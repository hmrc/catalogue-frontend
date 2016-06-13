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
      "microservice.services.teams-and-services.port" -> endpointPort
    ))

  "A service page" should {


    "return a 404 when teams and services returns a 404" in {
      teamsAndServicesEndpoint(GET, "/api/service/serv", willRespondWith = (404, None))

      val response = await(WS.url(s"http://localhost:$port/service/serv").get)
      response.status shouldBe 404
    }

    "show the teams owning the service with github, ci and environment links" in {
      teamsAndServicesEndpoint(GET, "/api/service/serv",willRespondWith = (200, Some(
        """
          |    {
          |	     "name": "serv",
          |      "teamNames": ["teamA", "teamB"],
          |	     "githubUrls": [{
          |		     "name": "github",
          |		     "url": "https://github.com/hmrc/serv"
          |	     }],
          |	     "ci": [
          |		     {
          |		       "name": "open1",
          |		       "url": "http://open1/serv"
          |		     },
          |		     {
          |		       "name": "open2",
          |		       "url": "http://open2/serv"
          |		     }
          |	     ],
          |      "environments" : [{
          |        "name" : "env1",
          |        "services" : [{
          |          "name": "ser1",
          |          "url": "http://ser1/serv"
          |        }, {
          |          "name": "ser2",
          |          "url": "http://ser2/serv"
          |        }]
          |      },{
          |        "name" : "env2",
          |        "services" : [{
          |          "name": "ser1",
          |          "url": "http://ser1/serv"
          |        }, {
          |          "name": "ser2",
          |          "url": "http://ser2/serv"
          |        }]
          |       }]
          |     }
        """.stripMargin)))

      val response = await(WS.url(s"http://localhost:$port/service/serv").get)
      response.status shouldBe 200
      response.body should include(s"teamA")
      response.body should include(s"teamB")
      response.body should include(s"open1")
      response.body should include(s"open2")
      response.body should include(s"http://open1/serv")
      response.body should include(s"http://open2/serv")
      response.body should include(s"http://ser1/serv")
      response.body should include(s"http://ser2/serv")

    }
  }
}
