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

import java.util.Date

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest._
import org.scalatestplus.play.OneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WS
import uk.gov.hmrc.play.test.UnitSpec
import DateHelper._

class ServicesAndLibrariesSpec extends UnitSpec with BeforeAndAfter with OneServerPerSuite with WireMockEndpoints {

  implicit override lazy val app = new GuiceApplicationBuilder().configure(
    Map(
      "microservice.services.teams-and-services.port" -> endpointPort,
      "microservice.services.teams-and-services.host" -> host,
      "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler")
  ).build()


  def asDocument(html: String): Document = Jsoup.parse(html)

  "Services list" should {

    "show a list of services and link to the team services page" in {

      val createdDate1 = 1429787242000L
      val createdDate2 = 1429787299000L
      val lastActiveDate1 = 1452785041000L
      val lastActiveDate2 = 1452785099000L


      serviceEndpoint(GET, "/api/services", willRespondWith = (200, Some(
        s"""[
          |   {"name":"teamA-serv", "createdAt": $createdDate1, "lastUpdatedAt": $lastActiveDate1},
          |   {"name":"teamB-frontend", "createdAt": $createdDate2, "lastUpdatedAt": $lastActiveDate2}
          |]""".stripMargin
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

      val response = await(WS.url(s"http://localhost:$port/services").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: Tue, 14 Oct 1066 10:03:23 GMT")
      response.body should include("<h1>Services</h1>")
      response.body should include("""href="/services/teamA-serv"""")
      response.body should include("""href="/services/teamB-frontend"""")

      val document = asDocument(response.body)


      document.select("#row0_created").text() shouldBe new Date(createdDate1).toLocalDate.asPattern("yyyy-MM-dd")
      document.select("#row0_lastActive").text() shouldBe new Date(lastActiveDate1).toLocalDate.asPattern("yyyy-MM-dd")
      document.select("#row1_created").text() shouldBe new Date(createdDate2).toLocalDate.asPattern("yyyy-MM-dd")
      document.select("#row1_lastActive").text() shouldBe new Date(lastActiveDate2).toLocalDate.asPattern("yyyy-MM-dd")


    }

    "show a list of libraries and link to the team library page" in {

      serviceEndpoint(GET, "/api/libraries", willRespondWith = (200, Some(
        """[
          |   {"name":"teamA-library", "createdAt": 1429787242000, "lastUpdatedAt": 1452785041000},
          |   {"name":"teamB-library", "createdAt": 1429787242000, "lastUpdatedAt": 1452785041000}
          |]""".stripMargin
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
