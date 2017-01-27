/*
 * Copyright 2017 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.DateHelper._
import uk.gov.hmrc.play.test.UnitSpec

class RepositoriesSpec extends UnitSpec with BeforeAndAfter with OneServerPerSuite with WireMockEndpoints {

  implicit override lazy val app = new GuiceApplicationBuilder().configure(
    Map(
      "microservice.services.teams-and-services.port" -> endpointPort,
      "microservice.services.teams-and-services.host" -> host,
      "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler")
  ).build()


  def asDocument(html: String): Document = Jsoup.parse(html)

  "Repositories list" should {

    "show a list of all repositories when 'All' is selected" in {

      serviceEndpoint(GET, "/api/repositories", willRespondWith = (200, Some(
        JsonData.repositoriesData
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Fri, 14 Oct 1983 10:03:23 GMT"))

      val response = await(WS.url(s"http://localhost:$port/repositories").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: 14 Oct 1983 10:03")
      response.body should include("<h1>Repositories</h1>")

      val document = asDocument(response.body)

      document.select("#row0_name").select("td a").text() shouldBe "teamA-serv"
      document.select("#row0_name").select("td a[href]").attr("href") shouldBe "/service/teamA-serv"
      document.select("#row0_created").text() shouldBe JsonData.createdAt.asPattern("yyyy-MM-dd")
      document.select("#row0_repotype").text() shouldBe "Service"
      document.select("#row0_lastActive").text() shouldBe JsonData.lastActiveAt.asPattern("yyyy-MM-dd")

      document.select("#row1_name").select("td a").text() shouldBe "teamB-library"
      document.select("#row1_name").select("td a[href]").attr("href") shouldBe "/library/teamB-library"
      document.select("#row1_created").text() shouldBe JsonData.createdAt.asPattern("yyyy-MM-dd")
      document.select("#row1_repotype").text() shouldBe "Library"
      document.select("#row1_lastActive").text() shouldBe JsonData.lastActiveAt.asPattern("yyyy-MM-dd")

      document.select("#row2_name").select("td a").text() shouldBe "teamB-other"
      document.select("#row2_name").select("td a[href]").attr("href") shouldBe "/repositories/teamB-other"
      document.select("#row2_created").text() shouldBe JsonData.createdAt.asPattern("yyyy-MM-dd")
      document.select("#row2_repotype").text() shouldBe "Other"
      document.select("#row2_lastActive").text() shouldBe JsonData.lastActiveAt.asPattern("yyyy-MM-dd")
    }


    "show a list of all libraries when 'Library' is selected" in {

      serviceEndpoint(GET, "/api/repositories", willRespondWith = (200, Some(
        JsonData.repositoriesData
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Fri, 14 Oct 1983 10:03:23 GMT"))

      val response = await(WS.url(s"http://localhost:$port/repositories?name=&type=Library").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: 14 Oct 1983 10:03")
      response.body should include("<h1>Repositories</h1>")

      val document = asDocument(response.body)

      document.select("tbody.list").select("tr").size() shouldBe 1

      document.select("#row0_name").select("td a").text() shouldBe "teamB-library"
      document.select("#row0_name").select("td a[href]").attr("href") shouldBe "/library/teamB-library"
      document.select("#row0_created").text() shouldBe JsonData.createdAt.asPattern("yyyy-MM-dd")
      document.select("#row0_repotype").text() shouldBe "Library"
      document.select("#row0_lastActive").text() shouldBe JsonData.lastActiveAt.asPattern("yyyy-MM-dd")
    }

  }
}
