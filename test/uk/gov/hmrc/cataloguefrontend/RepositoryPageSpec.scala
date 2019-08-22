/*
 * Copyright 2019 HM Revenue & Customs
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
import org.jsoup.nodes.Document
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws._
import uk.gov.hmrc.cataloguefrontend.connector.RepoType
import uk.gov.hmrc.play.test.UnitSpec

class RepositoryPageSpec
    extends UnitSpec
    with BeforeAndAfter
    with GuiceOneServerPerSuite
    with WireMockEndpoints
    with BeforeAndAfterEach {

  case class RepositoryDetails(repositoryName: String, repositoryType: RepoType.RepoType)

  val repositoryDetails = Seq(
    RepositoryDetails("Service", RepoType.Service),
    RepositoryDetails("Library", RepoType.Library),
    RepositoryDetails("Other", RepoType.Other)
  )

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.teams-and-repositories.host" -> host,
        "microservice.services.teams-and-repositories.port" -> endpointPort,
        "microservice.services.service-dependencies.host"   -> host,
        "microservice.services.service-dependencies.port"   -> endpointPort,
        "microservice.services.leak-detection.port"         -> endpointPort,
        "microservice.services.leak-detection.host"         -> host,
        "play.http.requestHandler"                          -> "play.api.http.DefaultHttpRequestHandler",
        "metrics.jvm"                                       -> false
      )
      .build()

  private[this] lazy val WS           = app.injector.instanceOf[WSClient]
  private[this] lazy val viewMessages = app.injector.instanceOf[ViewMessages]

  override def beforeEach(): Unit = {
    super.beforeEach()
    serviceEndpoint(GET, "/reports/repositories", willRespondWith = (200, Some("[]")))
  }

  "A repository page" should {

    "return a 404 when the teams-and-repositories microservice returns a 404" in {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (404, None))

      val response = await(WS.url(s"http://localhost:$port/repositories/serv").get)
      response.status shouldBe 404
    }

    "show the teams owning the repository with github links for a Service, Library and Other" in {

      repositoryDetails.foreach { repositoryDetails =>
        serviceEndpoint(
          GET,
          s"/api/repositories/${repositoryDetails.repositoryName}",
          willRespondWith = (200, Some(repositoryData(repositoryDetails)))
        )

        val response = await(WS.url(s"http://localhost:$port/repositories/${repositoryDetails.repositoryName}").get)

        response.status shouldBe 200
        response.body   should include(s"links on this page are automatically generated")
        response.body   should include(s"teamA")
        response.body   should include(s"teamB")
        response.body   should include(s"github.com")
      }
    }

    "Render dependencies with red, green, amber and grey colours" in {

      serviceEndpoint(
        GET,
        "/api/repositories/service-name",
        willRespondWith = (200, Some(repositoryData(RepositoryDetails("Other", RepoType.Other))))
      )

      serviceEndpoint(
        GET,
        "/api/service-dependencies/dependencies/service-name",
        willRespondWith = (200, None)
      )

      val response = await(WS.url(s"http://localhost:$port/repositories/service-name").get)

      val document = Jsoup.parse(response.body)

      document.select("#platform-dependencies").size() should be > 0

    }
  }

  def asDocument(html: String): Document = Jsoup.parse(html)

  def repositoryData(repositoryDetails: RepositoryDetails): String =
    s"""
      |    {
      |	     "name": "${repositoryDetails.repositoryName}",
      |      "isPrivate": false,
      |      "repoType": "${repositoryDetails.repositoryType}",
      |      "owningTeams": [ "The True Owners" ],
      |      "teamNames": ["teamA", "teamB"],
      |      "description": "some description",
      |      "createdAt": 1456326530000,
      |      "lastActive": 1478602555000,
      |	     "githubUrl": {
      |		     "name": "github",
      |        "displayName": "github.com",
      |		     "url": "https://github.com/hmrc/${repositoryDetails.repositoryName}"
      |	     },
      |	     "ci": [
      |		     {
      |		       "name": "open1",
      |		       "displayName": "open 1",
      |		       "url": "http://open1/${repositoryDetails.repositoryName}"
      |		     },
      |		     {
      |		       "name": "open2",
      |		       "displayName": "open 2",
      |		       "url": "http://open2/${repositoryDetails.repositoryName}"
      |		     }
      |	     ],
      |      "environments" : [{
      |        "name" : "env1",
      |        "services" : [{
      |          "name": "ser1",
      |		       "displayName": "service1",
      |          "url": "http://ser1/${repositoryDetails.repositoryName}"
      |        }, {
      |          "name": "ser2",
      |		       "displayName": "service2",
      |          "url": "http://ser2/${repositoryDetails.repositoryName}"
      |        }]
      |      },{
      |        "name" : "env2",
      |        "services" : [{
      |          "name": "ser1",
      |		       "displayName": "service1",
      |          "url": "http://ser1/${repositoryDetails.repositoryName}"
      |        }, {
      |          "name": "ser2",
      |		       "displayName": "service2",
      |          "url": "http://ser2/${repositoryDetails.repositoryName}"
      |        }]
      |       }]
      |     }
    """.stripMargin
}
