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
import org.jsoup.nodes.{Document, Element}
import org.scalatest.BeforeAndAfter
import play.api.libs.json.Json
import uk.gov.hmrc.cataloguefrontend.JsonData._
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.TeamMember
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

import scala.io.Source
import scala.jdk.CollectionConverters._

class TeamServicesSpec extends UnitSpec with BeforeAndAfter with FakeApplicationBuilder {

  def asDocument(html: String): Document = Jsoup.parse(html)

  val umpFrontPageUrl = "http://some.ump.fontpage.com"

  private[this] lazy val viewMessages = app.injector.instanceOf[ViewMessages]

  val teamName = "teamA"

  override def beforeEach(): Unit = {
    super.beforeEach()
    setupAuthEndpoint()
    serviceEndpoint(GET, "/reports/repositories", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/api/teams/teamA/dependencies", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/api/teams/CATO/dependencies", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/api/repositories?archived=true", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/api/teams/teamA/slug-dependencies?flag=production", willRespondWith = (200, Some("{}")))
    serviceEndpoint(GET, "/api/teams/CATO/slug-dependencies?flag=production", willRespondWith = (200, Some("{}")))
  }

  "Team services page" should {
    "show a list of libraries, services, prototypes and repositories" in {
      serviceEndpoint(GET, "/api/v2/repositories?team=teamA&archived=false", willRespondWith = (200, Some(JsonData.repositoriesTeamAData)))
      serviceEndpoint(GET, s"/v2/organisations/teams/$teamName", willRespondWith = (200, Some(readFile("user-management-team-details-response.json"))))
      serviceEndpoint(GET, s"/v2/organisations/teams/$teamName/members", willRespondWith = (200, Some(readFile("user-management-response.json"))))

      val response = wsClient.url(s"http://localhost:$port/teams/teamA").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200

      val anchorTags =  asDocument(response.body).getElementsByTag("a").asScala.toList
      findAnchor(anchorTags, "/repositories/teamA-library", "teamA-library") shouldBe defined
      findAnchor(anchorTags, "/repositories/teamA-serv"   , "teamA-serv"   ) shouldBe defined
      findAnchor(anchorTags, "/repositories/teamA-proto"  , "teamA-proto"  ) shouldBe defined
      findAnchor(anchorTags, "/repositories/teamA-other"  , "teamA-other"  ) shouldBe defined
    }


    "show a message if no services are found" in {
      serviceEndpoint(GET, "/api/v2/repositories?team=teamA&archived=false", willRespondWith = (200, Some("[]")))
      serviceEndpoint(GET, s"/v2/organisations/teams/$teamName", willRespondWith = (200, Some(readFile("user-management-team-details-response.json"))))
      serviceEndpoint(GET, s"/v2/organisations/teams/$teamName/members", willRespondWith = (200, Some(readFile("user-management-response.json"))))

      val response = wsClient.url(s"http://localhost:$port/teams/teamA").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200
      response.body   should include(viewMessages.noRepoOfTypeForTeam("service"))
      response.body   should include(viewMessages.noRepoOfTypeForTeam("library"))
    }

    "show team members correctly" in {
      val teamName = "CATO"

      serviceEndpoint(GET, s"/api/teams/$teamName?includeRepos=true", willRespondWith = (200, Some(
        s"""
          {
            "name":"$teamName",
            "createdDate": "2009-02-13T21:20:00Z",
            "lastActiveDate": "2009-02-13T21:36:40Z",
            "repos": { "Library": [], "Service": [] },
            "ownedRepos": []
          }
        """
      )))
      serviceEndpoint(GET, s"/v2/organisations/teams/$teamName/members", willRespondWith = (200, Some(readFile("large-user-management-response.json"))))

      val response = wsClient.url(s"http://localhost:$port/teams/$teamName").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200

      val document = asDocument(response.body)
      verifyTeamMemberElementsText(document)
      verifyTeamMemberHrefLinks(document)
      verifyTeamOwnerIndicatorLabel(document)
    }

    "show error message if UMP is not available" in {
      serviceEndpoint(GET, "/api/v2/repositories?team=teamA", willRespondWith = (200, Some(JsonData.repositoriesData)))
      serviceEndpoint(GET, s"/v2/organisations/teams/$teamName/members", willRespondWith = (404, Some(readFile("user-management-response.json"))))

      val response = wsClient.url(s"http://localhost:$port/teams/teamA").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200
      response.body should include("Sorry, the User Management Portal is not available")
    }

    "show team details correctly" in {
      val teamName = "teamA"
      serviceEndpoint(GET, s"/api/v2/repositories?team=$teamName", willRespondWith = (200, Some(repositoriesData)))
      serviceEndpoint(GET, s"/v2/organisations/teams/$teamName", willRespondWith = (200, Some(readFile("user-management-team-details-response.json"))))

      val response = wsClient.url(s"http://localhost:$port/teams/$teamName").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200

      val document = asDocument(response.body)
      document.select("#team-description").asScala.head.text()   shouldBe "Description: TEAM-A is a great team"
      document.select("#team-documentation").asScala.head.text() shouldBe "Documentation: Go to Confluence space"
      document.select("#team-documentation").asScala.head.toString() should include(
        """<a href="https://some.documentation.url" target="_blank" rel="noreferrer noopener">Go to Confluence space<span class="glyphicon glyphicon-new-window"""")

      document.select("#team-organisation").text() shouldBe "Organisation: ORGA"

      document.select("#team-slack-channels").toString() should include(
        """<a href="https://slack.host/messages/team-A" target="_blank" rel="noreferrer noopener">#team-A<span class="glyphicon glyphicon-new-window"></span></a>""")

      document.select("#team-slack-channels").toString() should include(
        """<a href="https://slack.host/messages/team-A-NOTIFICATION" target="_blank" rel="noreferrer noopener">#team-A-NOTIFICATION<span class="glyphicon glyphicon-new-window"></span></a>""")

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

  def readFile(jsonFilePath: String): String = {
    val path = "__files/" + jsonFilePath
    try {
      Source.fromResource(path).getLines().mkString("\n")
    } catch {
      case _: NullPointerException => sys.error(s"Could not find file $path")
    }
  }

  def extractMembers(jsonString: String): Seq[TeamMember] =
    (Json.parse(jsonString) \\ "members").headOption
      .map(_.as[Seq[TeamMember]])
      .getOrElse(sys.error(s"not able to extract team members from json: $jsonString"))

    def findAnchor(anchorTags: Seq[Element], href: String, text: String): Option[Element] =
      anchorTags.find(e => e.text == text && e.attr("href") == href)
}
