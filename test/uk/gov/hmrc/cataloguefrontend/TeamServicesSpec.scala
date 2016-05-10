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
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scalatest._
import org.scalatestplus.play.OneServerPerTest
import play.api.libs.ws.WS
import play.api.test.FakeApplication
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.play.test.UnitSpec

class TeamServicesSpec extends UnitSpec with BeforeAndAfter with OneServerPerTest with WireMockEndpoints {

  override def newAppForTest(testData: TestData): FakeApplication = new FakeApplication(
    additionalConfiguration = Map(
      "microservice.services.teams-and-services.port" -> endpointPort
    ))

  val timeStamp = new DateTime(2016, 4, 5, 12, 57).getMillis
  val formatted = DateTimeFormat.forPattern("HH:mm dd/MM/yyyy").print(timeStamp)

  "Team services page" should {

    "show a list of services" in {
      teamsAndServicesEndpoint(GET, "/api/teamA/services", willRespondWith = (200, Some(
        s"""{
          |  "cacheTimestamp": $timeStamp,
          |  "data": [
          |    {
          |	     "name": "teamA-serv",
          |	     "githubUrl": {
          |		     "name": "github",
          |		     "url": "https://github.com/hmrc/teamA-serv"
          |	     },
          |	     "ci": [
          |		     {
          |		       "name": "open1",
          |		       "url": "http://open1/teamA-serv"
          |		     },
          |		     {
          |		       "name": "open2",
          |		       "url": "http://open2/teamA-serv"
          |		     }
          |	     ]
          |    },
          |    {
          |	     "name": "teamA-frontend",
          |	     "githubUrl": {
          |	       "name": "github",
          |	       "url": "https://github.com/hmrc/teamA-frontend"
          |	     },
          |	     "ci": [
          |	 	     {
          |	         "name": "open1",
          |		       "url": "http://open1/teamA-frontend"
          |		     },
          |		     {
          |		       "name": "open2",
          |		       "url": "http://open2/teamA-frontend"
          |		     }
          |	     ]
          |	   }
          |  ]
          |}""".stripMargin
      )))

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: $formatted")

      response.body should include("teamA-serv")
      response.body should include("""id="teamA-serv"""")
      response.body should include("https://github.com/hmrc/teamA-serv")
      response.body should include("http://open1/teamA-serv")
      response.body should include("http://open2/teamA-serv")

      response.body should include("teamA-frontend")
      response.body should include("""id="teamA-frontend"""")
      response.body should include("https://github.com/hmrc/teamA-frontend")
      response.body should include("http://open1/teamA-frontend")
      response.body should include("http://open2/teamA-frontend")
    }

    "show a message if no services are found" in {

      teamsAndServicesEndpoint(GET, "/api/teamA/services", willRespondWith = (200, Some(
        s"""{
            |  "cacheTimestamp": $timeStamp,
            |  "data": []
            |}""".stripMargin
      )))

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: $formatted")
      response.body should include(s"${ViewMessages.noServices}")
    }
  }
}
