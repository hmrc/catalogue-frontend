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
import uk.gov.hmrc.cataloguefrontend.jsondata.{JsonData, ServiceDependenciesJsonData, TeamsAndRepositoriesJsonData}
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class DependencyExplorerSpec extends UnitSpec with BeforeAndAfter with FakeApplicationBuilder {

  override def beforeEach(): Unit = {
    super.beforeEach()
    setupAuthEndpoint()
  }

  "Dependency Explorer Page" should {
    "show search fields on landing on this page with no query" in {
      serviceEndpoint(GET, "/api/v2/teams", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.teams)))
      serviceEndpoint(GET, "/api/v2/repositories", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.repositoriesTeamAData)))
      serviceEndpoint(GET, "/api/groupArtefacts", willRespondWith = (200, Some(JsonData.emptyList)))
      serviceEndpoint(GET, "/pr-commenter/reports", willRespondWith = (200, Some(JsonData.emptyList)))

      val response = wsClient.url(s"http://localhost:$port/dependencyexplorer").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200
      response.body should include("Dependency Explorer")

      val document = Jsoup.parse(response.body)
      document.select("h1").attr("id") shouldBe "search-service-header"
      document.select("h1").text() shouldBe "Dependency Explorer"
      document.select("#search-by-dependency-form").select("#group-artefact-search").attr("value") shouldBe ""
    }

    "show teams correctly on results page" in {
      serviceEndpoint(GET, "/api/v2/teams", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.teams)))
      serviceEndpoint(GET, "/api/v2/repositories", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.repositoriesTeamAData)))
      serviceEndpoint(GET, "/pr-commenter/reports", willRespondWith = (200, Some(JsonData.emptyList)))
      serviceEndpoint(GET, "/api/groupArtefacts", willRespondWith = (200, Some(ServiceDependenciesJsonData.groupArtefactsFromHMRC)))
      serviceEndpoint(GET, "/api/repoDependencies?flag=latest&group=uk.gov.hmrc&artefact=bootstrap-backend-play-28&versionRange=%5B0.0.0,%5D&scope=compile&repoType=Service", willRespondWith = (200,Some(ServiceDependenciesJsonData.serviceDepsForBootstrapBackendPlay) ))

      val response = wsClient.url(s"http://localhost:$port/dependencyexplorer/results?group=uk.gov.hmrc&artefact=bootstrap-backend-play-28&versionRange=[0.0.0%2C]&asCsv=false&team=&flag=latest&scope[]=compile&repoType[]=Service").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200
      response.body should include("Dependency Explorer")

      val document = Jsoup.parse(response.body)
      document.select("h1").attr("id") shouldBe "search-service-header"
      document.select("h1").text() shouldBe "Dependency Explorer"
      document.select("#search-by-dependency-form").select("#group-artefact-search").get(0).attr("value") shouldBe "uk.gov.hmrc:bootstrap-backend-play-28"

      document.select("#search-results").select("tbody tr").get(0).select("td.teams a").text() shouldBe "teamA"
      document.select("#search-results").select("tbody tr").get(0).select("td.teams a").attr("href") shouldBe "/teams/teamA"
      document.select("#search-results").select("tbody tr").get(1).select("td.teams a").text() shouldBe "Shared by 9 teams"
      document.select("#search-results").select("tbody tr").get(1).select("td.teams a").attr("href") shouldBe "/service/teams-amd-repositories#owning-team"
    }
  }
}
