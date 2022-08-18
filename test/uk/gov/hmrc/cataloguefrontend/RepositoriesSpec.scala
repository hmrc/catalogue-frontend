/*
 * Copyright 2022 HM Revenue & Customs
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
import org.scalatest.BeforeAndAfter
import uk.gov.hmrc.cataloguefrontend.DateHelper._
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class RepositoriesSpec extends UnitSpec with BeforeAndAfter with FakeApplicationBuilder {

  override def beforeEach(): Unit = {
    super.beforeEach()
    setupAuthEndpoint()
  }

  "Repositories list" should {
    "show a list of all repositories when 'All' is selected" in {
      serviceEndpoint(GET, "/api/v2/teams", willRespondWith = (200, Some(JsonData.teams )))
      serviceEndpoint(GET, "/api/v2/repositories", willRespondWith = (200, Some(JsonData.repositoriesTeamAData)))

      val response = wsClient.url(s"http://localhost:$port/repositories?repoTypeFilter=").withAuthToken("Token token").get.futureValue
      response.status shouldBe 200
      response.body   should include("<h1>Repositories</h1>")
      val document = Jsoup.parse(response.body)
      document.select("#row0_name").select("td a").text()       shouldBe "teamA-library"
      document.select("#row0_name").select("td a[href]").attr("href") shouldBe "/repositories/teamA-library"
      document.select("#row0_created").text()                         shouldBe JsonData.createdAt.asPattern("yyyy-MM-dd")
      document.select("#row0_repotype").text()                        shouldBe "Library"
      document.select("#row0_lastActive").text()                      shouldBe JsonData.lastActiveAt.asPattern("yyyy-MM-dd")

      document.select("#row1_name").select("td a").text()      shouldBe "teamA-other"
      document.select("#row1_name").select("td a[href]").attr("href") shouldBe "/repositories/teamA-other"
      document.select("#row1_created").text()                         shouldBe JsonData.createdAt.asPattern("yyyy-MM-dd")
      document.select("#row1_repotype").text()                        shouldBe "Other"
      document.select("#row1_lastActive").text()                      shouldBe JsonData.lastActiveAt.asPattern("yyyy-MM-dd")

      document.select("#row2_name").select("td a").text()      shouldBe "teamA-proto"
      document.select("#row2_name").select("td a[href]").attr("href") shouldBe "/repositories/teamA-proto"
      document.select("#row2_created").text()                         shouldBe JsonData.createdAt.asPattern("yyyy-MM-dd")
      document.select("#row2_repotype").text()                        shouldBe "Prototype"
      document.select("#row2_lastActive").text()                      shouldBe JsonData.lastActiveAt.asPattern("yyyy-MM-dd")

      document.select("#row4_name").select("td a").text()      shouldBe "teamA-serv"
      document.select("#row4_name").select("td a[href]").attr("href") shouldBe "/repositories/teamA-serv"
      document.select("#row4_created").text()                         shouldBe JsonData.createdAt.asPattern("yyyy-MM-dd")
      document.select("#row4_repotype").text()                        shouldBe "Service"
      document.select("#row4_lastActive").text()                      shouldBe JsonData.lastActiveAt.asPattern("yyyy-MM-dd")
    }

    "show a list of all libraries when 'Library' is selected" in {
      serviceEndpoint(GET, "/api/v2/teams", willRespondWith = (200, Some(JsonData.teams)))
      serviceEndpoint(GET, "/api/v2/repositories?repoType=Library", willRespondWith = (200, Some(JsonData.repositoriesTeamADataLibrary)))

      val response = wsClient.url(s"http://localhost:$port/repositories?repoTypeFilter=Library").withAuthToken("Token token").get.futureValue
      response.status shouldBe 200
      response.body   should include("<h1>Repositories</h1>")

      val document = Jsoup.parse(response.body)
      document.select("tbody.list").select("tr").size() shouldBe 1
      document.select("#row0_name"      ).select("td a").text()      shouldBe "teamA-library"
      document.select("#row0_name"      ).select("td a[href]").attr("href") shouldBe "/repositories/teamA-library"
      document.select("#row0_created"   ).text()                            shouldBe JsonData.createdAt.asPattern("yyyy-MM-dd")
      document.select("#row0_repotype"  ).text()                            shouldBe "Library"
      document.select("#row0_lastActive").text()                            shouldBe JsonData.lastActiveAt.asPattern("yyyy-MM-dd")
    }
  }
}
