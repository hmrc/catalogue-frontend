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

package uk.gov.hmrc.cataloguefrontend.teams

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.scalatest.BeforeAndAfter
import play.api.libs.ws.readableAsString
import uk.gov.hmrc.cataloguefrontend.jsondata.TeamsAndRepositoriesJsonData
import uk.gov.hmrc.cataloguefrontend.test.{FakeApplicationBuilder, UnitSpec}
import uk.gov.hmrc.cataloguefrontend.view.ViewMessages

import scala.io.Source
import scala.jdk.CollectionConverters._

class TeamsControllerSpec extends UnitSpec with BeforeAndAfter with FakeApplicationBuilder {

  def asDocument(html: String): Document = Jsoup.parse(html)

  val umpFrontPageUrl = "http://some.ump.fontpage.com"

  private[this] lazy val viewMessages = app.injector.instanceOf[ViewMessages]

  override def beforeEach(): Unit =
    super.beforeEach()
    serviceEndpoint(POST, "/internal-auth/auth"                                                             , willRespondWith = (200, Some("""{"retrievals": [[]]}""")))
    serviceEndpoint(GET , "/releases-api/whats-running-where?profileName=teamA&profileType=team"            , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/service-commissioning-status/cached-status?teamName=teamA"                      , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/pr-commenter/reports?teamName=teamA"                                            , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/api/repositories/summary?team=teamA&excludeNonIssues=true&includeBranches=false", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/vulnerabilities/api/reports/production/counts?team=teamA"                       , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/vulnerabilities/api/reports/latest/counts?team=teamA"                           , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/user-management/users?team=teamA"                                               , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/api/open-pull-requests?reposOwnedByTeam=teamA"                                  , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/api/open-pull-requests?raisedByMembersOfTeam=teamA"                             , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/api/test-jobs?teamName=teamA"                                                   , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/shutter-api/production/frontend/states?teamName=teamA"                          , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/shutter-api/production/api/states?teamName=teamA"                               , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/api/bobbyReports?team=teamA&flag=production"                                    , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/api/bobbyReports?team=teamA&flag=latest"                                        , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET , "/platform-initiatives/teams/teamA/initiatives"                                   , willRespondWith = (200, Some("[]")))

    import com.github.tomakehurst.wiremock.client.WireMock
    wireMockServer.stubFor(
      WireMock.get(WireMock.urlMatching("/service-metrics/log-metrics.*"))
        .withQueryParam("team"       , WireMock.equalTo("teamA"))
        .withQueryParam("environment", WireMock.equalTo("production"))
        .withQueryParam("from"       , WireMock.matching(".*"))
        .willReturn(
          WireMock
            .aResponse()
            .withStatus(200)
            .withHeader("Content-type", "Application/json")
            .withBody("[]")
        )
    )

  "Team services page" should {
    "show a list of libraries, services, prototypes and repositories" in {
      val teamName = "teamA"

      serviceEndpoint(GET, s"/user-management/teams/$teamName?includeNonHuman=true", willRespondWith = (200, Some(readFile("user-management-team-details-response.json"))))
      serviceEndpoint(GET, "/api/v2/teams?name=teamA"         , willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.teams)))
      serviceEndpoint(GET, "/api/v2/repositories"             , queryParameters = Seq("owningTeam" -> teamName, "archived" -> "false"),  willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.repositoriesTeamAData)))

      val response = wsClient.url(s"http://localhost:$port/teams/teamA").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200

      val anchorTags =  asDocument(response.body).getElementsByTag("a").asScala.toList
      findAnchor(anchorTags, "/repositories/teamA-library", "teamA-library") shouldBe defined
      findAnchor(anchorTags, "/repositories/teamA-serv"   , "teamA-serv"   ) shouldBe defined
      findAnchor(anchorTags, "/repositories/teamA-proto"  , "teamA-proto"  ) shouldBe defined
      findAnchor(anchorTags, "/repositories/teamA-other"  , "teamA-other"  ) shouldBe defined
    }

    "not show a list of libraries, services, prototypes and repositories, if ump team does not have github" in {
      val teamName = "teamA"

      serviceEndpoint(GET, s"/user-management/teams/$teamName?includeNonHuman=true", willRespondWith = (200, Some(readFile("user-management-team-details-response.json"))))
      serviceEndpoint(GET, "/api/v2/teams?name=teamA", willRespondWith = (200, Some("[]")))
      serviceEndpoint(GET, "/api/v2/repositories"    , queryParameters = Seq("owningTeam" -> teamName, "archived" -> "false"),  willRespondWith = (200, Some("[]")))

      val response = wsClient.url(s"http://localhost:$port/teams/teamA").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200

      val anchorTags =  asDocument(response.body).getElementsByTag("a").asScala.toList
      findAnchor(anchorTags, "/repositories/teamA-library", "teamA-library") shouldBe empty
      findAnchor(anchorTags, "/repositories/teamA-serv"   , "teamA-serv"   ) shouldBe empty
      findAnchor(anchorTags, "/repositories/teamA-proto"  , "teamA-proto"  ) shouldBe empty
      findAnchor(anchorTags, "/repositories/teamA-other"  , "teamA-other"  ) shouldBe empty
    }

    "show a message if no services are found" in {
      val teamName = "teamA"

      serviceEndpoint(GET, s"/user-management/teams/$teamName?includeNonHuman=true", willRespondWith = (200, Some(readFile("user-management-team-details-response.json"))))
      serviceEndpoint(GET, "/api/v2/teams?name=teamA"         , willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.teams)))
      serviceEndpoint(GET, "/api/v2/repositories"             , queryParameters = Seq("owningTeam" -> teamName, "archived" -> "false"), willRespondWith = (200, Some("[]")))

      val response = wsClient.url(s"http://localhost:$port/teams/teamA").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200
      response.body   should include(viewMessages.noRepoOfTypeForTeam("Service"))
      response.body   should include(viewMessages.noRepoOfTypeForTeam("Library"))
    }

    "show team members correctly" in {
      val teamName = "teamA"

      serviceEndpoint(GET, s"/user-management/teams/$teamName?includeNonHuman=true", willRespondWith = (200, Some(readFile("user-management-five-members.json"))))
      serviceEndpoint(GET, "/api/v2/teams?name=teamA"         , willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.teams)))
      serviceEndpoint(GET, s"/api/v2/repositories"            , queryParameters = Seq("owningTeam" -> teamName, "archived" -> "false"), willRespondWith = (200, Some("[]")))

      val response = wsClient.url(s"http://localhost:$port/teams/$teamName").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200

      val document = asDocument(response.body)
      verifyTeamMemberElementsText(document)
      verifyTeamMemberHrefLinks(document)
    }

    "show team details correctly" in {
      val teamName = "teamA"

      serviceEndpoint(GET, s"/user-management/teams/$teamName?includeNonHuman=true", willRespondWith = (200, Some(readFile("user-management-team-details-response.json"))))
      serviceEndpoint(GET, "/api/v2/teams?name=teamA"         , willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.teams)))
      serviceEndpoint(GET, s"/api/v2/repositories"            , queryParameters = Seq("owningTeam" -> teamName, "archived" -> "false"), willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.repositoriesData)))

      val response = wsClient.url(s"http://localhost:$port/teams/$teamName").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200

      val document = asDocument(response.body)
      document.select("#team-description").asScala.head.text()   shouldBe "Description: TEAM-A is a great team"
      document.select("#team-documentation").asScala.head.text() shouldBe "Documentation: Go to Confluence space"
      document.select("#team-documentation").asScala.head.toString() should include(
        """href="https://some.documentation.url" target="_blank" rel="noreferrer noopener">Go to Confluence space<span class="glyphicon glyphicon-new-window""""
      )

      document.select("#slack-team").toString() should include(
        """href="https://slack.host/messages/team-A" target="_blank" rel="noreferrer noopener">#team-A<span class="glyphicon glyphicon-new-window"></span></a>"""
      )

      document.select("#slack-notification").toString() should include(
        """href="https://slack.host/messages/team-A-NOTIFICATION" target="_blank" rel="noreferrer noopener">#team-A-NOTIFICATION<span class="glyphicon glyphicon-new-window"></span></a>"""
      )

      document.select("#github").toString() should include(
        """href="https://github.com/orgs/hmrc/teams/teama"""
      )
    }
  }

  def verifyTeamMemberHrefLinks(document: Document): Unit =
    val hrefs = document.select("#team_members [href]").iterator().asScala.toList
    hrefs.size shouldBe 5
    hrefs.map(_.attributes().get("href")) should contain theSameElementsAs
      List(
        "/users/m.q",
        "/users/s.m",
        "/users/k.s",
        "/users/mx.p",
        "/users/ma.b"
      )

  def verifyTeamMemberElementsText(document: Document): Unit =
    val teamMembersLiElements = document.select("#team_members li").iterator().asScala.toList
    teamMembersLiElements.length shouldBe 5
    teamMembersLiElements.map(_.text()) should contain theSameElementsAs List("K S", "M Q", "Ma B", "Mx P", "S M")

  def readFile(jsonFilePath: String): String =
    val path = "__files/" + jsonFilePath
    try {
      Source.fromResource(path).getLines().mkString("\n")
    } catch {
      case _: NullPointerException => sys.error(s"Could not find file $path")
    }

  def findAnchor(anchorTags: Seq[Element], href: String, text: String): Option[Element] =
    anchorTags.find(e => e.text == text && e.attr("href") == href)
}
