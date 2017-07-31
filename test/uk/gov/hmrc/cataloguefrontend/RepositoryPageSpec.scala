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

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest._
import org.scalatestplus.play.OneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WS, WSResponse}
import play.twirl.api.Html
import uk.gov.hmrc.play.test.UnitSpec

class RepositoryPageSpec extends UnitSpec with BeforeAndAfter with OneServerPerSuite with WireMockEndpoints {

  case class RepositoryDetails(repositoryName: String, repositoryType: RepoType.RepoType)

  val repositoryDetails = Seq(
    RepositoryDetails("Service", RepoType.Service),
    RepositoryDetails("Library", RepoType.Library),
    RepositoryDetails("Other", RepoType.Other)
  )

  implicit override lazy val app = new GuiceApplicationBuilder().configure (
    "microservice.services.teams-and-services.host" -> host,
    "microservice.services.teams-and-services.port" -> endpointPort,
    "microservice.services.service-dependencies.host" -> host,
    "microservice.services.service-dependencies.port" -> endpointPort,
    "microservice.services.indicators.port" -> endpointPort,
    "microservice.services.indicators.host" -> host,
    "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler").build()

  "A repository page" should {

    "return a 404 when the teams-and-repositories microservice returns a 404" in {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (404, None))

      val response = await(WS.url(s"http://localhost:$port/repositories/serv").get)
      response.status shouldBe 404
    }

    "show the teams owning the repository with github links for a Service, Library and Other" in {

      repositoryDetails.foreach { repositoryDetails =>
        serviceEndpoint(GET, s"/api/repositories/${repositoryDetails.repositoryName}", willRespondWith = (200, Some(repositoryData(repositoryDetails))))

        val response = await(WS.url(s"http://localhost:$port/repositories/${repositoryDetails.repositoryName}").get)
        response.status shouldBe 200
        response.body should include(s"links on this page are automatically generated")
        response.body should include(s"teamA")
        response.body should include(s"teamB")
        response.body should include(s"github.com")
      }
    }

    "Render the frequent production indicators graph with throughput and stability" in {
      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith = (200, Some(repositoryData(RepositoryDetails("Other", RepoType.Other)))))
      serviceEndpoint(GET, "/api/indicators/repository/service-name/builds", willRespondWith = (200, Some(JsonData.jobExecutionTimeData)))

      val response = await(WS.url(s"http://localhost:$port/repositories/service-name").get)
      response.status shouldBe 200

      response.body should include(s"""data.addColumn('string', 'Period');""")
      response.body should include(s"""data.addColumn('number', "Duration");""")
      response.body should include(s"""data.addColumn({'type': 'string', 'role': 'tooltip', 'p': {'html': true}});""")

      response.body should include(s"""chart.draw(data, classicOptions);""")
    }

    "Render a message if the indicators service returns 404" in {
      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith = (200, Some(repositoryData(RepositoryDetails("Other", RepoType.Other)))))
      serviceEndpoint(GET, "/api/indicators/repository/service-name/builds", willRespondWith = (404, None))

      val response = await(WS.url(s"http://localhost:$port/repositories/service-name").get)
      response.status shouldBe 200

      response.body should include(s"""No data to show""")
      response.body should include(ViewMessages.noJobExecutionData)

      response.body shouldNot include(s"""chart.draw(data, options);""")
    }

    "Render a message if the indicators service encounters an error" in {
      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith = (200, Some(repositoryData(RepositoryDetails("Other", RepoType.Other)))))
      serviceEndpoint(GET, "/api/indicators/repository/service-name/builds", willRespondWith = (500, None))

      val response = await(WS.url(s"http://localhost:$port/repositories/service-name").get)
      response.status shouldBe 200
      response.body should include(s"""The catalogue encountered an error""")
      response.body should include(ViewMessages.indicatorsServiceError)

      response.body shouldNot include(s"""chart.draw(data, options);""")
    }

    "Render dependencies with red, green, amber and grey colours" in {

      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith = (200, Some(repositoryData(RepositoryDetails("Other", RepoType.Other)))))
      serviceEndpoint(GET, "/api/service-dependencies/dependencies/service-name", willRespondWith = (200, None))

      val response = await(WS.url(s"http://localhost:$port/repositories/service-name").get)

      val document = Jsoup.parse(response.body)

      document.select("#platform-dependencies").size() should be > 0

    }
  }

  def asDocument(html: String): Document = Jsoup.parse(html)

  def repositoryData(repositoryDetails: RepositoryDetails) =
    s"""
      |    {
      |	     "name": "${repositoryDetails.repositoryName}",
      |      "repoType": "${repositoryDetails.repositoryType}",
      |      "teamNames": ["teamA", "teamB"],
      |      "description": "some description",
      |      "createdAt": 1456326530000,
      |      "lastActive": 1478602555000,
      |	     "githubUrls": [{
      |		     "name": "github",
      |        "displayName": "github.com",
      |		     "url": "https://github.com/hmrc/${repositoryDetails.repositoryName}"
      |	     }],
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
