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
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class DependencyExplorerSpec extends UnitSpec with BeforeAndAfter with FakeApplicationBuilder {

  override def beforeEach(): Unit = {
    super.beforeEach()
    setupAuthEndpoint()
  }

  "Dependency Explorer Page" should {
    "show search fields on landing on this page with no query" in {

      serviceEndpoint(GET, "/api/v2/teams", willRespondWith = (200, Some(JsonData.teams)))
      serviceEndpoint(GET, "/api/v2/repositories", willRespondWith = (200, Some(JsonData.repositoriesTeamAData)))
      serviceEndpoint(GET, "/api/groupArtefacts", willRespondWith = (200, Some(JsonData.teams)))

      val response = wsClient.url(s"http://localhost:$port/dependencyexplorer").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200
      response.body should include("<h1>Dependency Explorer</h1>")
      val document = Jsoup.parse(response.body)
      document.select("#search-by-dependency-form").select("#group-artefact-search").attr("value") shouldBe ""
    }
  }
}
