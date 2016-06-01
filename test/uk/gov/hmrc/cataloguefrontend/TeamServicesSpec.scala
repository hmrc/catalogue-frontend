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

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest._
import org.scalatestplus.play.OneServerPerTest
import play.api.libs.ws.WS
import play.api.test.FakeApplication
import uk.gov.hmrc.play.test.UnitSpec

class TeamServicesSpec extends UnitSpec with BeforeAndAfter with OneServerPerTest with WireMockEndpoints {

  override def newAppForTest(testData: TestData): FakeApplication = new FakeApplication(
    additionalConfiguration = Map(
      "microservice.services.teams-and-services.port" -> endpointPort
    ))

  "Team services page" should {

    "show a list of services" in {
      teamsAndServicesEndpoint(GET, "/api/teams/teamA/services", willRespondWith = (200, Some(
        s"""[
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
          |	     "name": "teamA-frontend",
          |      "teamNames": ["teamA"],
          |	     "githubUrls": [{
          |	       "name": "github",
          |	       "url": "https://github.com/hmrc/teamA-frontend"
          |	     }],
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
          |]""".stripMargin
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: Tue, 14 Oct 1066 10:03:23 GMT")

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

    "show other teams that a service belongs to" in {
      teamsAndServicesEndpoint(GET, "/api/teams/teamA/services", willRespondWith = (200, Some(
        s"""[
            |    {
            |	     "name": "teamA-serv",
            |      "teamNames": ["teamA", "teamAnother"],
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
            ]""".stripMargin
      )))

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200

      response.body should include(ViewMessages.otherTeamsAre)
      response.body should include("<li><a href=\"/teams/teamAnother#teamA-serv\">teamAnother</a></li>")
      response.body shouldNot include("<li><a href=\"/teams/teamA#teamA-serv")
    }

    "show multiple github links if they are present" in {
      teamsAndServicesEndpoint(GET, "/api/teams/teamA/services", willRespondWith = (200, Some(
        s"""[
            |    {
            |	     "name": "teamA-serv",
            |      "teamNames": ["teamA", "teamAnother"],
            |	     "githubUrls": [
            |        {
            |		       "name": "github",
            |		       "url": "https://notopen.com/hmrc/teamA-serv"
            |	       },
            |        {
            |		       "name": "github-open",
            |		       "url": "https://github.com/hmrc/teamA-serv"
            |	       }
            |      ],
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
            |]""".stripMargin
      )))

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)
      response.status shouldBe 200

      response.body should include("<li><a href=\"https://notopen.com/hmrc/teamA-serv\" target=\"_blank\">github</a></li>")
      response.body should include("<li><a href=\"https://github.com/hmrc/teamA-serv\" target=\"_blank\">github-open</a></li>")
    }

    "show '(None)' if no timestamp is found" in {

      teamsAndServicesEndpoint(GET, "/api/teams/teamA/services", willRespondWith = (200, Some(
        "[]".stripMargin
      )))

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: (None)")
      response.body should include(s"${ViewMessages.noServices}")
    }

    "show a message if no services are found" in {

      teamsAndServicesEndpoint(GET, "/api/teams/teamA/services", willRespondWith = (200, Some(
        "[]".stripMargin
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: Tue, 14 Oct 1066 10:03:23 GMT")
      response.body should include(s"${ViewMessages.noServices}")
    }
  }
}
