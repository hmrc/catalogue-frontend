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
import play.api.libs.ws.readableAsString
import uk.gov.hmrc.cataloguefrontend.jsondata.JsonData
import uk.gov.hmrc.cataloguefrontend.connector.RepoType
import uk.gov.hmrc.cataloguefrontend.test.{FakeApplicationBuilder, UnitSpec}

class RepositoryPageSpec extends UnitSpec with FakeApplicationBuilder {

  case class RepositoryDetails(
    repositoryName: String,
    repositoryType: RepoType
  )

  override def beforeEach(): Unit =
    super.beforeEach()
    setupAuthEndpoint()
    setupEnableBranchProtectionAuthEndpoint()

  "A repository page" should {
    "return a 404 when the teams-and-repositories microservice returns a 404" in {
      val repoName = "other"

      serviceEndpoint(GET, s"/api/v2/repositories/$repoName", willRespondWith = (404, None))
      serviceEndpoint(GET, s"/api/repositories/$repoName/module-dependencies?version-latest", willRespondWith = (200, Some("[]")))

      val response = wsClient.url(s"http://localhost:$port/repositories/$repoName").withAuthToken("Token token").get().futureValue
      response.status shouldBe 404
    }

    "show the teams owning the repository with github links" in {
      val repoName = "other"
      val repositoryDetails = RepositoryDetails(repoName, RepoType.Other)

      serviceEndpoint(GET, s"/api/v2/repositories/$repoName", willRespondWith = (200, Some(repositoryData(repositoryDetails))))
      serviceEndpoint(GET, s"/api/repositories/$repoName/module-dependencies?version=latest", willRespondWith = (200, Some("[]")))

      val response = wsClient.url(s"http://localhost:$port/repositories/$repoName").withAuthToken("Token token").get().futureValue

      response.status shouldBe 200
      response.body   should include(s"links on this page are automatically generated")
      response.body   should include(s"teamA")
      response.body   should include(s"teamB")
      response.body   should include(s"GitHub")
    }

    "show dependency links when metadata or the artefact name resolves to a repository and omit links otherwise" in {
      val repoName = "other"
      def dependency(name: String, group: String, version: String): String =
        s"""{"name":"$name","group":"$group","currentVersion":"$version",
             "bobbyRuleViolations":[],"vulnerabilities":[],"scope":"compile"}"""
      val dependencies = Seq(
        dependency("example-unregistered-library", "uk.gov.hmrc.example", "1.0.0"),
        dependency("example-legacy-library", "uk.gov.hmrc.example", "2.0.0"),
        dependency("example-registered-library", "uk.gov.hmrc.example", "3.0.0")
      ).mkString("[", ",", "]")
      serviceEndpoint(GET, s"/api/v2/repositories/$repoName", willRespondWith = (200, Some(repositoryData(RepositoryDetails(repoName, RepoType.Other)))))
      serviceEndpoint(GET, s"/api/repositories/$repoName/module-dependencies?version=latest", willRespondWith = (200, Some(JsonData.repositoryModules(repoName, dependencies))))
      serviceEndpoint(GET, "/api/repository-name?group=uk.gov.hmrc.example&artefact=example-unregistered-library&version=1.0.0", willRespondWith = (404, None))
      serviceEndpoint(GET, "/api/v2/repositories/example-unregistered-library", willRespondWith = (404, None))
      serviceEndpoint(GET, "/api/repository-name?group=uk.gov.hmrc.example&artefact=example-legacy-library&version=2.0.0", willRespondWith = (404, None))
      serviceEndpoint(GET, "/api/v2/repositories/example-legacy-library", willRespondWith = (200, Some(repositoryData(RepositoryDetails("example-legacy-library", RepoType.Library)))))
      serviceEndpoint(GET, "/api/repository-name?group=uk.gov.hmrc.example&artefact=example-registered-library&version=3.0.0", willRespondWith = (200, Some("\"example-registered-library\"")))

      val response = wsClient.url(s"http://localhost:$port/repositories/$repoName").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200
      val document = Jsoup.parse(response.body)
      document.select("#example-unregistered-library-platform-dependencies-m1 .dep-name").text() shouldBe "example-unregistered-library"
      document.select("#example-unregistered-library-platform-dependencies-m1-current-version").text() shouldBe "1.0.0"
      document.select("#link-to-example-unregistered-library-platform-dependencies-m1").isEmpty shouldBe true
      document.select("#link-to-example-legacy-library-platform-dependencies-m1").attr("href") shouldBe
        "/dependency-repository?group=uk.gov.hmrc.example&artefact=example-legacy-library&version=2.0.0"
      document.select("#link-to-example-registered-library-platform-dependencies-m1").size() shouldBe 1
    }

    "render dependencies" in {
      val repoName = "other"
      val repositoryDetails = RepositoryDetails(repoName, RepoType.Other)

      serviceEndpoint(GET, s"/api/v2/repositories/$repoName", willRespondWith = (200, Some(repositoryData(repositoryDetails))))
      serviceEndpoint(GET, s"/api/repositories/$repoName/module-dependencies?version=latest", willRespondWith = (200, Some(JsonData.repositoryModules(
                                                                                                repoName,
                                                                                                dependenciesCompile = JsonData.dependencies
                                                                                              ))))

      val response = wsClient.url(s"http://localhost:$port/repositories/$repoName").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200

      val document = Jsoup.parse(response.body)
      document.select("#platform-dependencies-m1").size() shouldBe 1
    }
  }

  def repositoryData(repositoryDetails: RepositoryDetails): String =
    s"""
     {"name":           "${repositoryDetails.repositoryName}",
      "description":    "",
      "teamNames":      ["teamA", "teamB"],
      "createdDate":    "${JsonData.createdAt}",
      "lastActiveDate": "${JsonData.lastActiveAt}",
      "repoType":       "${repositoryDetails.repositoryType.asString}",
      "language":       "Scala",
      "isArchived":     false,
      "defaultBranch":  "main",
      "isDeprecated":   false,
      "url":            "http://github.com/repoa"
      }
    """
}
