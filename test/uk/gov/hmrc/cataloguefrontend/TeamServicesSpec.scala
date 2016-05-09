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
import org.scalatest._
import org.scalatestplus.play.OneServerPerTest
import play.api.libs.ws.WS
import play.api.test.FakeApplication
import uk.gov.hmrc.play.test.UnitSpec

class TeamServicesSpec extends UnitSpec with BeforeAndAfter with OneServerPerTest with WireMockEndpoints {

  override def newAppForTest(testData: TestData): FakeApplication = new FakeApplication(
    additionalConfiguration = Map(
      "microservice.services.catalogue.port" -> endpointPort
    ))

  "landing page" should {
    "show a list of services" in {
      catalogEndpoint(GET, "/catalogue/api/teamA/services", willRespondWith = (200, Some(
        """{
          |  "cacheTimestamp": 1459857420000,
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

      val response = await(WS.url(s"http://localhost:$port/catalogue/teams/teamA").get)

      response.status shouldBe 200
      response.body should include("Last updated at: 12:57 05/04/2016")

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
  }
}
