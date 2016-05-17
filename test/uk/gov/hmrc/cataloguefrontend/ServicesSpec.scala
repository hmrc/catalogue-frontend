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
import uk.gov.hmrc.play.test.UnitSpec

class ServicesSpec extends UnitSpec with BeforeAndAfter with OneServerPerTest with WireMockEndpoints {

  override def newAppForTest(testData: TestData): FakeApplication = new FakeApplication(
    additionalConfiguration = Map(
      "microservice.services.teams-and-services.port" -> endpointPort
    ))

  "Services list" should {

    "show a list of services and link to the team services page" in  {

      val timeStamp = new DateTime(2016, 4, 5, 12, 57).getMillis
      val formatted = DateTimeFormat.forPattern("HH:mm dd/MM/yyyy").print(timeStamp)

      teamsAndServicesEndpoint(GET, "/api/services", willRespondWith = (200, Some(
        s"""{
            |  "cacheTimestamp": $timeStamp,
            |  "data": [
            |    {
            |	     "name": "teamA-serv",
            |      "teamNames": ["teamA"],
            |	     "githubUrls": [{
            |		     "name": "github",
            |		     "url": "https://github.com/hmrc/teamA-serv"
            |	     }],
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
            |	     "name": "teamB-frontend",
            |      "teamNames": ["teamB"],
            |	     "githubUrls": [{
            |	       "name": "github",
            |	       "url": "https://github.com/hmrc/teamB-frontend"
            |	     }],
            |	     "ci": [
            |	 	     {
            |	         "name": "open1",
            |		       "url": "http://open1/teamB-frontend"
            |		     },
            |		     {
            |		       "name": "open2",
            |		       "url": "http://open2/teamB-frontend"
            |		     }
            |	     ]
            |	   }
            |  ]
            |}""".stripMargin
      )))

      val response = await(WS.url(s"http://localhost:$port/services").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: $formatted")

      response.body should include("""<span>teamA-serv</span>""")
      response.body should include("""<a href="/teams/teamA#teamA-serv">teamA</a>""")

      response.body should include("""<span>teamB-frontend</span>""")
      response.body should include("""<a href="/teams/teamB#teamB-frontend">teamB</a>""")
    }

    "show multiple team links when a service is owned by more than one team" in  {

      val timeStamp = new DateTime(2016, 4, 5, 12, 57).getMillis
      val formatted = DateTimeFormat.forPattern("HH:mm dd/MM/yyyy").print(timeStamp)

      teamsAndServicesEndpoint(GET, "/api/services", willRespondWith = (200, Some(
        s"""{
            |  "cacheTimestamp": $timeStamp,
            |  "data": [
            |    {
            |	     "name": "generic-serv",
            |      "teamNames": ["teamA", "teamB"],
            |	     "githubUrls": [{
            |		     "name": "github",
            |		     "url": "https://github.com/hmrc/teamA-serv"
            |	     }],
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
            |    }
            |  ]
            |}""".stripMargin
      )))

      val response = await(WS.url(s"http://localhost:$port/services").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: $formatted")
      response.body should include("""<a href="/teams/teamA#generic-serv">teamA</a>""")
      response.body should include("""<a href="/teams/teamB#generic-serv">teamB</a>""")
    }
  }
}
