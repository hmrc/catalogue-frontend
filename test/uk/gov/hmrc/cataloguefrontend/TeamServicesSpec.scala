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

import java.util

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.jsoup.select.Elements
import org.scalatest._
import org.scalatestplus.play.OneServerPerTest
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WS
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.TeamMember
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec
import scala.collection.JavaConversions._

import scala.io.Source

class TeamServicesSpec extends UnitSpec with BeforeAndAfter with OneServerPerTest with WireMockEndpoints {

  def asDocument(html: String): Document = Jsoup.parse(html)


  val frontPageUrl = "http://some.ump.com"

  override def newAppForTest(testData: TestData) = {
    new GuiceApplicationBuilder().configure(
      "microservice.services.teams-and-services.host" -> host,
      "microservice.services.teams-and-services.port" -> endpointPort,
      "microservice.services.user-management.url" -> endpointMockUrl,
      "usermanagement.portal.url" -> "http://usermanagement/link",
      "microservice.services.user-management.frontPageUrl" -> frontPageUrl,
      "play.ws.ssl.loose.acceptAnyCertificate" -> true).build()
  }

  "Team services page" should {

    "show a list of libraries and services" in {
      serviceEndpoint(GET, "/api/teams/teamA", willRespondWith = (200, Some(
        """{"Library":["teamA-lib"], "Deployable": [ "teamA-serv", "teamA-frontend" ]  }""".stripMargin
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

      mockTeamMembersApiCall("/user-management-response.json")

      mockTeamMembersApiCall("/user-management-response.json")

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

      mockTeamMembersApiCall("/user-management-response.json")

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: Tue, 14 Oct 1066 10:03:23 GMT")

    response.body.toString should include("""<a href="http://usermanagement/link/teamA" target="_blank">Team Members</a>""")

    }

    "show '(None)' if no timestamp is found" in {

      serviceEndpoint(GET, "/api/teams/teamA", willRespondWith = (200, Some(
        """{"Library":[], "Service": []  }""".stripMargin
      )))

      mockTeamMembersApiCall("/user-management-response.json")

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: (None)")

    }

    "show a message if no services are found" in {

      serviceEndpoint(GET, "/api/teams/teamA", willRespondWith = (200, Some(
        """{"Library":[], "Deployable": []  }""".stripMargin
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

      mockTeamMembersApiCall("/user-management-response.json")

      val response = await(WS.url(s"http://localhost:$port/teams/teamA").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: Tue, 14 Oct 1066 10:03:23 GMT")
      response.body should include(ViewMessages.noRepoOfType("service"))
      response.body should include(ViewMessages.noRepoOfType("library"))
    }

    "show team members correctly" in {
      val teamName = "CATO"

      serviceEndpoint(GET, "/api/teams/" + teamName, willRespondWith = (200, Some(
        """{"Library":[], "Deployable": []  }""".stripMargin
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

      mockTeamMembersApiCall("/large-user-management-response.json", teamName)

      //TODO!@ remove the feature toggle
      val response = await(WS.url(s"http://localhost:$port/teams/$teamName?teamMembers").get)

      response.status shouldBe 200
      val document = asDocument(response.body)

      import scala.collection.JavaConversions._

      val teamMembersLiElements = document.select("#team_members li").iterator().toSeq

      teamMembersLiElements.length shouldBe 14

      val jsonString: String = readFile(mockDataFileName)


      verifyTeamMemberElementsText(document)

      verifyTeamMemberHrefLinks(document)

      verifyTeamOwnerIndicatorLabel(document)
    }

    "show link to UMP's front page when no members node returned" in {

      def verifyForFile(fileName : String): Unit = {

        val teamName = "CATO"

        serviceEndpoint(GET, "/api/teams/" + teamName, willRespondWith = (200, Some(
          """{"Library":[], "Deployable": []  }""".stripMargin
        )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

        mockTeamMembersApiCall(fileName, teamName)

        //TODO!@ remove the feature toggle
        val response = await(WS.url(s"http://localhost:$port/teams/$teamName?teamMembers").get)

        response.status shouldBe 200
        response.body should include(frontPageUrl)

        val document = asDocument(response.body)

        document.select("#linkToRectify").text() shouldBe s"Team $teamName is not defined in the User Management Portal, please add it here"

      }

      verifyForFile("/user-management-empty-members.json")
      verifyForFile("/user-management-no-members.json")

    }

    "show error message if UMP is not available" in {
      serviceEndpoint(GET, "/api/teams/teamA", willRespondWith = (200, Some(
        """{"Library":[], "Deployable": []  }""".stripMargin
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

      mockTeamMembersApiCall("/user-management-response.json", httpReturnCode = 404)

      val response = await(WS.url(s"http://localhost:$port/teams/teamA?teamMembers").get)

      response.status shouldBe 200

      response.body should include("Sorry, the User Management Portal is not available")
    }

  }

  def verifyTeamOwnerIndicatorLabel(document: Document): Unit = {
    val serviceOwnersLiLabels = document.select("#team_members li .label-success")
    serviceOwnersLiLabels.size() shouldBe 2
    serviceOwnersLiLabels.iterator().toSeq.map(_.text()) shouldBe Seq("Service Owner", "Service Owner")
  }

  def verifyTeamMemberHrefLinks(document: Document): Boolean = {
    val hrefs = document.select("#team_members [href]").iterator().toList

    hrefs.size shouldBe 5
    hrefs(0).attributes().get("href") == "http://example.com/profile/m.q"
    hrefs(1).attributes().get("href") == "http://example.com/profile/s.m"
    hrefs(2).attributes().get("href") == "http://example.com/profile/k.s"
    hrefs(3).attributes().get("href") == "http://example.com/profile/mx.p"
    hrefs(4).attributes().get("href") == "http://example.com/profile/ma.b"
  }

  def verifyTeamMemberElementsText(document: Document): Unit = {
    val teamMembersLiElements = document.select("#team_members li").iterator().toList

    teamMembersLiElements.length shouldBe 5

    teamMembersLiElements(0).text() should include("M Q Service Owner")
    teamMembersLiElements(1).text() should include("S M Service Owner")
    teamMembersLiElements(2).text() should include("K S")
    teamMembersLiElements(3).text() should include("Ma B")
    teamMembersLiElements(4).text() should include("Mx P")
  }

  def mockTeamMembersApiCall(jsonFilePath: String, teamName: String = "teamA", httpReturnCode: Int = 200): String = {
    val json = readFile(jsonFilePath)

    val url = s"/v1/organisations/mdtp/teams/$teamName/members"
    serviceEndpoint(
      method = GET,
      url = url,
      willRespondWith = (httpReturnCode, Some(json)),
      extraHeaders = Map("X-Cache-Timestamp" -> "Tue, 14 Oct 1066 10:03:23 GMT"))

    json
  }

  def readFile(jsonFilePath: String): String = {
    Source.fromURL(getClass.getResource(jsonFilePath)).getLines().mkString("\n")
  }

  def extractMembers(jsonString: String):  Seq[TeamMember] = {
    (Json.parse(jsonString) \\ "members")
      .headOption
      .map(js => js.as[Seq[TeamMember]]).getOrElse(throw new RuntimeException(s"not able to extract team members from json: $jsonString"))

  }

}
