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
import org.jsoup.nodes.Document
import org.scalatest.BeforeAndAfter
import play.api.libs.json.Json
import play.api.libs.ws._
import uk.gov.hmrc.cataloguefrontend.DateHelper._
import uk.gov.hmrc.cataloguefrontend.JsonData._
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.TeamMember
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

import java.time.ZoneOffset
import scala.collection.JavaConverters._
import scala.io.Source

class TeamServicesSpec extends UnitSpec with BeforeAndAfter with FakeApplicationBuilder {

  def asDocument(html: String): Document = Jsoup.parse(html)

  val umpFrontPageUrl = "http://some.ump.fontpage.com"

  private[this] lazy val WS           = app.injector.instanceOf[WSClient]
  private[this] lazy val viewMessages = app.injector.instanceOf[ViewMessages]

  val teamName = "teamA"

  override def beforeEach(): Unit = {
    super.beforeEach()
    serviceEndpoint(GET, "/reports/repositories", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/api/teams/teamA/dependencies", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/api/teams/CATO/dependencies", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/api/repositories?archived=true", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/api/teams/teamA/slug-dependencies?flag=production", willRespondWith = (200, Some("{}")))
    serviceEndpoint(GET, "/api/teams/CATO/slug-dependencies?flag=production", willRespondWith = (200, Some("{}")))
  }

  "Team services page" should {
    "show a list of libraries, services, prototypes and repositories" in {
      serviceEndpoint(
        GET,
        "/api/teams_with_details/teamA",
        willRespondWith = (
          200,
          Some(
            """
               {
                 "name": "teamA",
                 "firstActiveDate":1234560,
                 "lastActiveDate":1234561,
                 "repos":{
                    "Library": [
                        "teamA-lib"
                    ],
                    "Service": [
                        "teamA-serv",
                        "teamA-frontend"
                    ],
                    "Prototype": [
                        "service1-prototype",
                        "service2-prototype"
                    ],
                    "Other": [
                        "teamA-other"
                    ]
                 },
                 "ownedRepos": []
               }
            """
          ))
      )

      mockHttpApiCall(s"/v2/organisations/teams/$teamName/members", "/user-management-response.json")

      val response = WS.url(s"http://localhost:$port/teams/teamA").get.futureValue

      response.status shouldBe 200

      val htmlDocument = asDocument(response.body)
      val anchorTags   = htmlDocument.getElementsByTag("a").asScala.toList

      def assertAnchor(href: String, text: String): Unit =
        assert(
          anchorTags.exists(e => e.text == text && e.attr("href") == href)
        )

      assertAnchor("/library/teamA-lib", "teamA-lib")
      assertAnchor("/service/teamA-serv", "teamA-serv")
      assertAnchor("/service/teamA-frontend", "teamA-frontend")
      assertAnchor("/prototype/service1-prototype", "service1-prototype")
      assertAnchor("/prototype/service2-prototype", "service2-prototype")
      assertAnchor("/repositories/teamA-other", "teamA-other")
    }

    "filter out archived repositories from a list of libraries, services, prototypes and repositories" in {
      serviceEndpoint(GET, "/api/repositories?archived=true", willRespondWith = (200, Some(
        """
          |[{
          |  "name": "service1-prototype",
          |  "createdAt": 12345,
          |  "lastUpdatedAt": 67890,
          |  "repoType": "Prototype",
          |  "language": "Scala",
          |  "archived": true
          |}]
          |""".stripMargin)))

      serviceEndpoint(
        GET,
        "/api/teams_with_details/teamA",
        willRespondWith = (
          200,
          Some(
            """
               {
                 "name": "teamA",
                 "firstActiveDate":1234560,
                 "lastActiveDate":1234561,
                 "repos":{
                    "Library": [
                        "teamA-lib"
                    ],
                    "Service": [
                        "teamA-serv",
                        "teamA-frontend"
                    ],
                    "Prototype": [
                        "service1-prototype",
                        "service2-prototype"
                    ],
                    "Other": [
                        "teamA-other"
                    ]
                 },
                 "ownedRepos": []
               }
            """
          ))
      )

      mockHttpApiCall(s"/v2/organisations/teams/$teamName/members", "/user-management-response.json")

      val response = WS.url(s"http://localhost:$port/teams/teamA").get.futureValue

      response.status shouldBe 200

      val htmlDocument = asDocument(response.body)
      val anchorTags   = htmlDocument.getElementsByTag("a").asScala.toList

      def assertAnchor(href: String, text: String): Unit =
        assert(anchorTags.exists { e =>
          e.text == text && e.attr("href") == href
        })

      def assertNoAnchor(href: String, text: String): Unit = {
        val anchorExists = anchorTags.exists { e =>
          e.text == text && e.attr("href") == href
        }
        assert(!anchorExists)
      }

      assertAnchor("/library/teamA-lib", "teamA-lib")
      assertAnchor("/service/teamA-serv", "teamA-serv")
      assertAnchor("/service/teamA-frontend", "teamA-frontend")
      // Note: assert that this anchor does NOT exist
      assertNoAnchor("/prototype/service1-prototype", "service1-prototype")
      assertAnchor("/prototype/service2-prototype", "service2-prototype")
      assertAnchor("/repositories/teamA-other", "teamA-other")
    }

    "show user management portal link" ignore {
      serviceEndpoint(
        GET,
        "/api/teams_with_details/teamA",
        willRespondWith = (
          200,
          Some(
            """
              {
                "repos": { "Library": [], "Service": [] },
                "ownedRepos" = []
              }
            """
          ))
      )

      mockHttpApiCall(s"/v2/organisations/teams/$teamName/members", "/user-management-response.json")

      val response = WS.url(s"http://localhost:$port/teams/teamA").get.futureValue

      response.status shouldBe 200

      response.body.toString should include(
        """<a href="http://usermanagement/link/teamA" target="_blank">Team Members</a>""")
    }

    "show a message if no services are found" in {
      serviceEndpoint(
        GET,
        "/api/teams_with_details/teamA",
        willRespondWith = (
          200,
          Some(
            """
              {
                "name":"teamA",
                "firstActiveDate":12345,
                "lastActiveDate":1234567,
                "repos": { "Library": [], "Service": [] },
                "ownedRepos" : []
              }
            """
          ))
      )

      mockHttpApiCall(s"/v2/organisations/teams/$teamName/members", "/user-management-response.json")

      val response = WS.url(s"http://localhost:$port/teams/teamA").get.futureValue

      response.status shouldBe 200
      response.body   should include(viewMessages.noRepoOfTypeForTeam("service"))
      response.body   should include(viewMessages.noRepoOfTypeForTeam("library"))
    }

    "show team members correctly" in {
      val teamName = "CATO"

      serviceEndpoint(
        GET,
        "/api/teams_with_details/" + teamName,
        willRespondWith = (
          200,
          Some(
            s"""
              {
                "name":"$teamName",
                "firstActiveDate":12345,
                "lastActiveDate":1234567,
                "repos": { "Library": [], "Service": [] },
                "ownedRepos": []
              }
            """
          ))
      )

      mockHttpApiCall(s"/v2/organisations/teams/$teamName/members", "/large-user-management-response.json")

      val response = WS.url(s"http://localhost:$port/teams/$teamName").get.futureValue

      response.status shouldBe 200
      val document = asDocument(response.body)

      verifyTeamMemberElementsText(document)

      verifyTeamMemberHrefLinks(document)

      verifyTeamOwnerIndicatorLabel(document)
    }

    "show a First Active and Last Active fields in the Details box" in {
      serviceEndpoint(
        GET,
        "/api/teams_with_details/" + teamName,
        willRespondWith = (
          200,
          Some(
            s"""
               {
                "name": "$teamName",
                "firstActiveDate":${createdAt.toInstant(ZoneOffset.UTC).toEpochMilli},
                "lastActiveDate":${lastActiveAt.toInstant(ZoneOffset.UTC).toEpochMilli},
                "repos":{
                   "Library": [
                   ],
                   "Service": [
                           "service1"
                   ]
                },
                "ownedRepos": []
               }
            """
          ))
      )

      mockHttpApiCall(s"/v2/organisations/teams/$teamName/members", "/user-management-response.json")
      val response = WS.url(s"http://localhost:$port/teams/$teamName").get.futureValue

      response.status shouldBe 200

      response.body should include(createdAt.displayFormat)
      response.body should include(lastActiveAt.displayFormat)
    }

    "show link to UMP's front page when no members node returned" ignore {

      def verifyForFile(fileName: String): Unit = {

        val teamName = "CATO"

        serviceEndpoint(
          GET,
          "/api/teams_with_details/" + teamName,
          willRespondWith = (
            200,
            Some(
              s"""
                {
                  "name": "$teamName",
                  "firstActiveDate": 12345,
                  "lastActiveDate": 1234567,
                  "repos": {"Library":[], "Service": [] },
                  "ownedRepos": []
                }
              """
            ))
        )

        mockHttpApiCall(s"/v2/organisations/teams/$teamName/members", fileName)

        val response = WS.url(s"http://localhost:$port/teams/$teamName").get.futureValue

        response.status shouldBe 200
        response.body   should include(umpFrontPageUrl)

        val document = asDocument(response.body)

        document
          .select("#linkToRectify")
          .text() shouldBe s"Team $teamName is not defined in the User Management Portal, please add it here"
      }

      verifyForFile("/user-management-empty-members.json")
      verifyForFile("/user-management-no-members.json")
    }

    "show error message if UMP is not available" in {
      serviceEndpoint(
        GET,
        "/api/teams_with_details/teamA",
        willRespondWith = (
          200,
          Some(
            """
              {
                "name": "teamA",
                "firstActiveDate": 12345,
                "lastActiveDate": 1234567,
                "repos": {"Library":[], "Service": [] },
                "ownedRepos" : []
              }
            """
          ))
      )

      mockHttpApiCall(
        url = s"/v2/organisations/teams/$teamName/members",
        "/user-management-response.json",
        httpCodeToBeReturned = 404)

      val response = WS.url(s"http://localhost:$port/teams/teamA").get.futureValue

      response.status shouldBe 200

      response.body should include("Sorry, the User Management Portal is not available")
    }

    "show team details correctly" in {
      val teamName = "CATO"

      serviceEndpoint(
        GET,
        "/api/teams_with_details/" + teamName,
        willRespondWith = (
          200,
          Some(
            s"""
              {
                "name":"$teamName",
                "repos": { "Library": [], "Service": [] },
                "ownedRepos": []
              }
            """
          ))
      )

      mockHttpApiCall(s"/v2/organisations/teams/$teamName", "/user-management-team-details-response.json")

      val response = WS.url(s"http://localhost:$port/teams/$teamName").get.futureValue

      response.status shouldBe 200
      val document = asDocument(response.body)

      document.select("#team-first-active").text() shouldBe "First Active: None"
      document.select("#team-last-active").text()  shouldBe "Last Active: None"

      document.select("#team-description").asScala.head.text()   shouldBe "Description: TEAM-A is a great team"
      document.select("#team-documentation").asScala.head.text() shouldBe "Documentation: Go to Confluence space"
      document.select("#team-documentation").asScala.head.toString() should include(
        """<a href="https://some.documentation.url" target="_blank">Go to Confluence space<span class="glyphicon glyphicon-new-window"""")

      document.select("#team-organisation").text() shouldBe "Organisation: ORGA"

      document.select("#team-slack-channels").toString() should include(
        """<a href="https://slack.host/messages/team-A" target="_blank">Go to team channel<span class="glyphicon glyphicon-new-window"""")

      document.select("#team-slack-channels").toString() should include(
        """<a href="https://slack.host/messages/team-A-NOTIFICATION" target="_blank">Go to notification channel<span class="glyphicon glyphicon-new-window"""")

      document.select("#team-location").text() shouldBe "Location: STLPD"
    }
  }

  def verifyTeamOwnerIndicatorLabel(document: Document): Unit = {
    val serviceOwnersLiLabels = document.select("#team_members li .label-success")
    serviceOwnersLiLabels.size()                                 shouldBe 2
    serviceOwnersLiLabels.iterator().asScala.toSeq.map(_.text()) shouldBe Seq("Service Owner", "Service Owner")
  }

  def verifyTeamMemberHrefLinks(document: Document): Boolean = {
    val hrefs = document.select("#team_members [href]").iterator().asScala.toList

    hrefs.size shouldBe 5
    hrefs(0).attributes().get("href") == "http://example.com/profile/m.q"
    hrefs(1).attributes().get("href") == "http://example.com/profile/s.m"
    hrefs(2).attributes().get("href") == "http://example.com/profile/k.s"
    hrefs(3).attributes().get("href") == "http://example.com/profile/mx.p"
    hrefs(4).attributes().get("href") == "http://example.com/profile/ma.b"
  }

  def verifyTeamMemberElementsText(document: Document): Unit = {
    val teamMembersLiElements = document.select("#team_members li").iterator().asScala.toList

    teamMembersLiElements.length shouldBe 5

    teamMembersLiElements(0).text() should include("M Q Service Owner")
    teamMembersLiElements(1).text() should include("S M Service Owner")
    teamMembersLiElements(2).text() should include("K S")
    teamMembersLiElements(3).text() should include("Ma B")
    teamMembersLiElements(4).text() should include("Mx P")
  }

  def mockHttpApiCall(url: String, jsonResponseFile: String, httpCodeToBeReturned: Int = 200): String = {
    val json = readFile(jsonResponseFile)
    serviceEndpoint(method = GET, url = url, willRespondWith = (httpCodeToBeReturned, Some(json)))
    json
  }

  def readFile(jsonFilePath: String): String =
    Source.fromURL(getClass.getResource(jsonFilePath)).getLines().mkString("\n")

  def extractMembers(jsonString: String): Seq[TeamMember] =
    (Json.parse(jsonString) \\ "members").headOption
      .map(js => js.as[Seq[TeamMember]])
      .getOrElse(throw new RuntimeException(s"not able to extract team members from json: $jsonString"))
}
