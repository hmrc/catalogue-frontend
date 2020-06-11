/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws._
import uk.gov.hmrc.cataloguefrontend.{JsonData, WireMockEndpoints}
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class WhatsRunningWhereSpec extends UnitSpec with BeforeAndAfter with GuiceOneServerPerSuite with WireMockEndpoints {

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.releases-api.host"           -> host,
        "microservice.services.releases-api.port"           -> endpointPort,
        "microservice.services.teams-and-repositories.host" -> host,
        "microservice.services.teams-and-repositories.port" -> endpointPort,
        "play.ws.ssl.loose.acceptAnyCertificate"            -> true,
        "play.http.requestHandler"                          -> "play.api.http.DefaultHttpRequestHandler",
        "metrics.jvm"                                       -> false
      )
      .build()

  private[this] lazy val WS = app.injector.instanceOf[WSClient]

  "What's running where page" should {

    "show a list of applications, environments and version numbers for Heritage" in {

      serviceEndpoint(GET, "/api/teams_with_repositories", willRespondWith = (200, Some(JsonData.teamsWithRepos)))

      serviceEndpoint(GET, "/releases-api/profiles", willRespondWith = (200, Some(JsonData.profiles)))

      serviceEndpoint(
        GET,
        "/releases-api/whats-running-where",
        queryParameters = Seq("platform" -> Platform.Heritage.asString),
        willRespondWith = (
          200,
          Some("""[
              |  {
              |    "applicationName": "api-definition",
              |    "versions": [
              |      {
              |        "environment": "integration",
              |        "versionNumber": "1.57.0",
              |        "lastSeen": "2019-05-29T14:09:48Z"
              |      }
              |    ]
              |  },
              |  {
              |    "applicationName": "api-documentation",
              |    "versions": [
              |      {
              |        "environment": "integration",
              |        "versionNumber": "0.44.0",
              |        "lastSeen": "2019-05-29T14:09:46Z"
              |      }
              |    ]
              |  }
              |]""".stripMargin))
      )

      val response = WS.url(s"http://localhost:$port/whats-running-where").get.futureValue

      response.status shouldBe 200

      response.body should include("api-definition")
      response.body should include("1.57.0")

      response.body should include("api-documentation")
      response.body should include("0.44.0")

      response.body should include("integration")
    }

    "show a list of applications, environments and version numbers for ECS releases" in {

      serviceEndpoint(GET, "/api/teams_with_repositories", willRespondWith = (200, Some(JsonData.teamsWithRepos)))

      serviceEndpoint(GET, "/releases-api/profiles", willRespondWith = (200, Some(JsonData.profiles)))

      serviceEndpoint(
        GET,
        "/releases-api/whats-running-where",
        queryParameters = Seq("platform" -> Platform.ECS.asString),
        willRespondWith = (
          200,
          Some("""[
                 |  {
                 |    "applicationName": "api-definition",
                 |    "versions": [
                 |      {
                 |        "environment": "integration",
                 |        "versionNumber": "1.58.0",
                 |        "lastSeen": "2019-05-29T14:09:48Z"
                 |      }
                 |    ]
                 |  },
                 |  {
                 |    "applicationName": "api-documentation",
                 |    "versions": [
                 |      {
                 |        "environment": "integration",
                 |        "versionNumber": "0.41.0",
                 |        "lastSeen": "2019-05-29T14:09:46Z"
                 |      }
                 |    ]
                 |  }
                 |]""".stripMargin))
      )

      val response = WS.url(s"http://localhost:$port/whats-running-where-ecs").get.futureValue

      response.status shouldBe 200

      response.body should include("api-definition")
      response.body should include("1.58.0")

      response.body should include("api-documentation")
      response.body should include("0.41.0")

      response.body should include("Integration")
    }
  }
}
