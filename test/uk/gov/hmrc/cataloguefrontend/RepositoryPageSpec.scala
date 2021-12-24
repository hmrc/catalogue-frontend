/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.libs.ws._
import uk.gov.hmrc.cataloguefrontend.JsonData._
import uk.gov.hmrc.cataloguefrontend.connector.RepoType
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class RepositoryPageSpec extends UnitSpec with FakeApplicationBuilder {

  case class RepositoryDetails(
    repositoryName: String,
    repositoryType: RepoType
  )

  private[this] lazy val WS = app.injector.instanceOf[WSClient]

  "A repository page" should {
    "return a 404 when the teams-and-repositories microservice returns a 404" in {
      val repoName = "other"
      serviceEndpoint(GET, s"/api/repositories/$repoName"       , willRespondWith = (404, None))
      serviceEndpoint(GET, s"/api/jenkins-url/$repoName"        , willRespondWith = (404, None))
      serviceEndpoint(GET, s"/api/module-dependencies/$repoName", willRespondWith = (404, None))

      val response = WS.url(s"http://localhost:$port/repositories/$repoName").get.futureValue
      response.status shouldBe 404
    }

    "show the teams owning the repository with github links" in {
      val repoName = "other"
      val repositoryDetails = RepositoryDetails(repoName, RepoType.Other)

      serviceEndpoint(GET, s"/api/repositories/$repoName"       , willRespondWith = (200, Some(repositoryData(repositoryDetails))))
      serviceEndpoint(GET, s"/api/jenkins-url/$repoName"        , willRespondWith = (404, None))
      serviceEndpoint(GET, s"/api/module-dependencies/$repoName", willRespondWith = (404, None))

      val response = WS.url(s"http://localhost:$port/repositories/$repoName").get.futureValue

      response.status shouldBe 200
      response.body   should include(s"links on this page are automatically generated")
      response.body   should include(s"teamA")
      response.body   should include(s"teamB")
      response.body   should include(s"github.com")
    }

    "render dependencies" in {
      val repoName = "other"
      val repositoryDetails = RepositoryDetails(repoName, RepoType.Other)

      serviceEndpoint(GET, s"/api/repositories/$repoName"       , willRespondWith = (200, Some(repositoryData(repositoryDetails))))
      serviceEndpoint(GET, s"/api/jenkins-url/$repoName"        , willRespondWith = (404, None))
      serviceEndpoint(GET, s"/api/module-dependencies/$repoName", willRespondWith = (200, Some(repositoryModules(
                                                                                                repoName,
                                                                                                dependenciesCompile = dependencies
                                                                                              ))))

      val response = WS.url(s"http://localhost:$port/repositories/$repoName").get.futureValue

      response.status shouldBe 200

      val document = Jsoup.parse(response.body)

      document.select("#platform-dependencies-m1").size() shouldBe 1
    }
  }

  def repositoryData(repositoryDetails: RepositoryDetails): String =
    s"""
      {
        "name": "${repositoryDetails.repositoryName}",
        "isPrivate": false,
        "isArchived": false,
        "repoType": "${repositoryDetails.repositoryType}",
        "owningTeams": [ "The True Owners" ],
        "teamNames": ["teamA", "teamB"],
        "description": "some description",
        "createdAt": 1456326530000,
        "lastActive": 1478602555000,
        "defaultBranch": "master",
        "githubUrl": {
          "name": "github",
          "displayName": "github.com",
          "url": "https://github.com/hmrc/${repositoryDetails.repositoryName}"
        },
        "ci": [
          {
            "name": "open1",
            "displayName": "open 1",
            "url": "http://open1/${repositoryDetails.repositoryName}"
          },
          {
            "name": "open2",
            "displayName": "open 2",
            "url": "http://open2/${repositoryDetails.repositoryName}"
          }
        ],
        "environments" : [
          {
            "name" : "Production",
            "services" : [
              {
                "name": "ser1",
                "displayName": "service1",
                "url": "http://ser1/${repositoryDetails.repositoryName}"
              }, {
                "name": "ser2",
                "displayName": "service2",
                "url": "http://ser2/${repositoryDetails.repositoryName}"
              }
            ]
          }, {
            "name" : "Staging",
            "services" : [
               {
                 "name": "ser1",
                 "displayName": "service1",
                 "url": "http://ser1/${repositoryDetails.repositoryName}"
               }, {
                 "name": "ser2",
                 "displayName": "service2",
                 "url": "http://ser2/${repositoryDetails.repositoryName}"
              }
            ]
          }
        ]
      }
    """
}
