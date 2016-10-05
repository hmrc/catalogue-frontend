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

class ServicesAndLibrariesSpec extends UnitSpec with BeforeAndAfter with OneServerPerTest with WireMockEndpoints {

  override def newAppForTest(testData: TestData): FakeApplication = new FakeApplication(
    additionalConfiguration = Map(
      "microservice.services.teams-and-services.port" -> endpointPort,
      "microservice.services.teams-and-services.host" -> host
    ))

  "Services list" should {

    "show a list of services and link to the team services page" in  {

      serviceEndpoint(GET, "/api/services",willRespondWith = (200, Some(
        """[ "teamA-serv", "teamB-frontend" ]"""
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

      val response = await(WS.url(s"http://localhost:$port/services").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: Tue, 14 Oct 1066 10:03:23 GMT")
      response.body should include("<h1>Services</h1>")
      response.body should include("""href="/services/teamA-serv"""")
      response.body should include("""href="/services/teamB-frontend"""")
    }

    "show a list of libraries and link to the team library page" in  {

      serviceEndpoint(GET, "/api/libraries",willRespondWith = (200, Some(
        """[ "teamA-library", "teamB-library" ]"""
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

      val response = await(WS.url(s"http://localhost:$port/libraries").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: Tue, 14 Oct 1066 10:03:23 GMT")
      response.body should include("<h1>Libraries</h1>")
      response.body should include("""href="/libraries/teamA-library"""")
      response.body should include("""href="/libraries/teamB-library"""")
    }
  }
}
