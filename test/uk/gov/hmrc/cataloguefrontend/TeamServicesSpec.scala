/*
 * Copyright 2016 HM Revenue & Customs
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
import org.scalatestplus.play.OneServerPerTest
import play.api.libs.ws.WS
import play.api.test.FakeApplication
import uk.gov.hmrc.play.test.UnitSpec

import scala.io.Source

class TeamServicesSpec extends UnitSpec with BeforeAndAfter with OneServerPerTest with WireMockEndpoints {

  def asDocument(html: String): Document = Jsoup.parse(html)

  override def newAppForTest(testData: TestData): FakeApplication = new FakeApplication(
    additionalConfiguration = Map(
      "microservice.services.teams-and-services.host" -> host,
      "microservice.services.teams-and-services.port" -> endpointPort,
      "microservice.services.user-management.port" -> endpointPort,
      "microservice.services.user-management.host" -> host,
      "usermanagement.portal.url" -> "http://usermanagement/link"
    ))

  "Team services page" should {

    "show a list of libraries and services" in {
      serviceEndpoint(GET, "/api/teams/teamA", willRespondWith = (200, Some(
        """{"Library":["teamA-lib"], "Deployable": [ "teamA-serv", "teamA-frontend" ]  }""".stripMargin
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

      mockTeamMembersApiCall

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: Tue, 14 Oct 1066 10:03:23 GMT")

      response.body should include("""<a href="/libraries/teamA-lib">teamA-lib</a>""")
      response.body should include("""<a href="/services/teamA-serv">teamA-serv</a>""")
      response.body should include("""<a href="/services/teamA-frontend">teamA-frontend</a>""")

    }

    "show user management portal link" ignore {
      serviceEndpoint(GET, "/api/teams/teamA", willRespondWith = (200, Some(
        """{"Library":[], "Service": []  }""".stripMargin
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

      mockTeamMembersApiCall

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: Tue, 14 Oct 1066 10:03:23 GMT")

    response.body.toString should include("""<a href="http://usermanagement/link/teamA" target="_blank">Team Members</a>""")

    }

    "show '(None)' if no timestamp is found" in {

      serviceEndpoint(GET, "/api/teams/teamA", willRespondWith = (200, Some(
        """{"Library":[], "Service": []  }""".stripMargin
      )))

      mockTeamMembersApiCall

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: (None)")

    }

    "show a message if no services are found" in {

      serviceEndpoint(GET, "/api/teams/teamA", willRespondWith = (200, Some(
        """{"Library":[], "Deployable": []  }""".stripMargin
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

      mockTeamMembersApiCall

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: Tue, 14 Oct 1066 10:03:23 GMT")
      response.body should include(ViewMessages.noRepoOfType("service"))
      response.body should include(ViewMessages.noRepoOfType("library"))
    }

    "show team members correctly" in {

      serviceEndpoint(GET, "/api/teams/teamA", willRespondWith = (200, Some(
        """{"Library":[], "Deployable": []  }""".stripMargin
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

      mockTeamMembersApiCall

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200
      val document = asDocument(response.body)


    }
  }

  def mockTeamMembersApiCall: Unit = {
    val json = Source.fromURL(getClass.getResource("/user-management-response.json")).getLines().mkString("\n")

    serviceEndpoint(
      method = GET,
      url = "/v1/organisations/mdtp/teams/teamA/members",
      willRespondWith = (200, Some(json)),
      extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))
  }
}
