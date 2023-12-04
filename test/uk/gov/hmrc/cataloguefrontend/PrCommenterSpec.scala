/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.jsondata.{PrCommenterJsonData, TeamsAndRepositoriesJsonData}
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class PrCommenterSpec extends UnitSpec with BeforeAndAfter with FakeApplicationBuilder {

  override def beforeEach(): Unit = {
    super.beforeEach()
    setupAuthEndpoint()
  }

  "PrCommenter Page" should {
    "show page with some results" in {
      serviceEndpoint(GET, "/api/v2/teams", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.teams)))
      serviceEndpoint(GET, "/api/v2/repositories?name=11-seven-teams-repo", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.repositoriesSharedRepoSearchResult)))
      serviceEndpoint(GET, "/api/v2/repositories", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.repositoriesTeamAData)))
      serviceEndpoint(GET, "/pr-commenter/reports", willRespondWith = (200, Some(PrCommenterJsonData.reportResults)))

      val response = wsClient.url(s"http://localhost:$port/pr-commenter/recommendations?name=11-seven-teams-repo").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200
      response.body should include("PR-Commenter Recommendations")

      val document = Jsoup.parse(response.body)
      document.select("h1").attr("id") shouldBe "search-service-header"
      document.select("h1").text() shouldBe "PR-Commenter Recommendations"

      document.select("tbody.list tr").get(0).select("td.teams div.repo-team").attr("title") shouldBe "teamA\nteamB\nteamC\nteamD\nteamE\nteamF\nteamG\nteamH"
      document.select("tbody.list tr").get(0).select("td.teams div.repo-team a").attr("href") shouldBe "/repositories/11-seven-teams-repo#teams"
      document.select("tbody.list tr").get(0).select("td.teams div.repo-team a").text() shouldBe "Shared by 9 teams"
    }
  }
}
