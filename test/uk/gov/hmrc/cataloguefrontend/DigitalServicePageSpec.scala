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

import java.time.LocalDateTime
import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers._
import org.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.ws._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET => _, _}
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.{TeamMember, UMPError}
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.service._
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterService
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereService
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import views.html._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

class DigitalServicePageSpec extends UnitSpec with FakeApplicationBuilder with MockitoSugar {

  private[this] lazy val WS                     = app.injector.instanceOf[WSClient]
  private[this] lazy val viewMessages           = app.injector.instanceOf[ViewMessages]
  private[this] lazy val digitalServiceInfoPage = app.injector.instanceOf[DigitalServiceInfoPage]

  private[this] val digitalServiceName = "digital-service-a"

  "DigitalService page" should {
    "show a list of libraries, services, prototypes and repositories" in new MockedCatalogueFrontendSetup {
      serviceEndpoint(
        GET,
        s"/api/digital-services/$digitalServiceName",
        willRespondWith = (
          200,
          Some(
            s"""{
             "name": "$digitalServiceName",
             "lastUpdatedAt": "2017-05-08T10:54:29Z",
             "repositories": [
               {
                 "name": "A",
                 "createdAt": "2016-02-24T15:08:50Z",
                 "lastUpdatedAt": "2017-05-08T10:53:29Z",
                 "repoType": "Service",
                 "teamNames": ["Team1", "Team2"]
               },
               {
                 "name": "B",
                 "createdAt": "2017-04-11T13:14:29Z",
                 "lastUpdatedAt": "2017-05-08T10:54:29Z",
                 "repoType": "Prototype",
                 "teamNames": ["Team1"]
               },
               {
                 "name": "C",
                 "createdAt": "2016-02-05T10:55:16Z",
                 "lastUpdatedAt": "2017-05-08T10:53:58Z",
                 "repoType": "Library",
                 "teamNames": ["Team2"]
               },
               {
                 "name": "D",
                 "createdAt": "2016-02-05T10:55:16Z",
                 "lastUpdatedAt": "2017-05-08T10:53:58Z",
                 "repoType": "Other",
                 "teamNames": ["Team3"]
               }
             ]
           }"""
          ))
      )

      val response = WS.url(s"http://localhost:$port/digital-service/$digitalServiceName").get.futureValue

      response.status shouldBe 200

      response.body should include("""<a href="/repositories/B">B</a>""")
      response.body should include("""<a href="/repositories/C">C</a>""")
      response.body should include("""<a href="/repositories/D">D</a>""")
    }

    "show a message if no services are found" in {
      serviceEndpoint(
        GET,
        s"/api/digital-services/$digitalServiceName",
        willRespondWith = (
          200,
          Some(
            s"""{
              "name":"$digitalServiceName",
              "lastUpdatedAt": "2017-05-08T10:53:58Z",
              "repositories":[]
            }"""
          ))
      )

      val response = WS.url(s"http://localhost:$port/digital-service/$digitalServiceName").get.futureValue

      response.status shouldBe 200
      response.body   should include(viewMessages.noRepoOfTypeForDigitalService("service"))
      response.body   should include(viewMessages.noRepoOfTypeForDigitalService("library"))
      response.body   should include(viewMessages.noRepoOfTypeForDigitalService("prototype"))
      response.body   should include(viewMessages.noRepoOfTypeForDigitalService("other"))
    }

    "show team members for teams correctly" in {
      val digitalServiceName = "digital-service-123"
      val team1              = "Team1"
      val team2              = "Team2"

      val json =
        s"""
           {
             "name": "Catalogue",
             "lastUpdatedAt": "2017-05-15T16:03:13Z",
             "repositories": [
               {
                 "name": "catalogue-frontend",
                 "createdAt": "2016-02-24T15:08:50Z",
                 "lastUpdatedAt": "2017-05-15T16:03:13Z",
                 "repoType": "Service",
                 "teamNames": [ "$team1", "$team2"]
               }
             ]
           }
        """

      serviceEndpoint(
        GET,
        s"/api/digital-services/$digitalServiceName",
        willRespondWith = (
          200,
          Some(
            json
          )))

      serviceEndpoint(method = GET, url = s"/v2/organisations/teams/$team1/members", willRespondWith = (200, Some(readFile("user-management-response-team1.json"))))
      serviceEndpoint(method = GET, url = s"/v2/organisations/teams/$team2/members", willRespondWith = (200, Some(readFile("user-management-response-team2.json"))))

      val response = WS.url(s"http://localhost:$port/digital-service/$digitalServiceName").get.futureValue

      response.status shouldBe 200

      val document = asDocument(response.body)
      verifyTeamMemberElementsText(document)
      verifyTeamMemberHrefLinks(document)
    }

    "show the right error message when unable to connect to ump" in new MockedCatalogueFrontendSetup {
      val teamName = TeamName("Team1")

      when(teamsAndRepositoriesConnectorMock.digitalServiceInfo(any())(any()))
        .thenReturn(Future.successful(Some(DigitalService(digitalServiceName, LocalDateTime.now(), Nil))))
      when(userManagementConnectorMock.getTeamMembersForTeams(any())(any()))
        .thenReturn(
          Future.successful(
            Map(teamName -> Left(UMPError.ConnectionError("Boooom!")))
          )
        )

      val response = catalogueController.digitalService(digitalServiceName)(FakeRequest())

      status(response) shouldBe 200

      val document = asDocument(contentAsString(response))

      document.select(s"#ump-error-${teamName.asString}").text() should ===("Sorry, the User Management Portal is not available")
    }

    "show the right error message the ump send no data" in new MockedCatalogueFrontendSetup {
      val teamName = TeamName("Team1")

      when(teamsAndRepositoriesConnectorMock.digitalServiceInfo(any())(any()))
        .thenReturn(Future.successful(Some(DigitalService(digitalServiceName, LocalDateTime.now(), Nil))))
      when(userManagementConnectorMock.getTeamMembersForTeams(any())(any()))
        .thenReturn(
          Future.successful(
            Map(teamName -> Left(UMPError.UnknownTeam))
          )
        )

      val response = catalogueController.digitalService(digitalServiceName)(FakeRequest())

      status(response) shouldBe 200

      val document = asDocument(contentAsString(response))

      document.select(s"#ump-error-${teamName.asString}").text() should ===(s"${teamName.asString} is unknown to the User Management Portal. To add the team, please raise a TSR")
    }

    "show the right error message the ump gives an Http error" in new MockedCatalogueFrontendSetup {
      val teamName = TeamName("Team1")

      when(teamsAndRepositoriesConnectorMock.digitalServiceInfo(any())(any()))
        .thenReturn(Future.successful(Some(DigitalService(digitalServiceName, LocalDateTime.now(), Nil))))
      when(userManagementConnectorMock.getTeamMembersForTeams(any())(any()))
        .thenReturn(
          Future.successful(
            Map(teamName -> Left(UMPError.HTTPError(500)))
          )
        )

      val response = catalogueController.digitalService(digitalServiceName)(FakeRequest())

      status(response) shouldBe 200

      val document = asDocument(contentAsString(response))

      document.select(s"#ump-error-${teamName.asString}").text() should ===(s"Sorry, the User Management Portal is not available")
    }
  }

  private trait MockedCatalogueFrontendSetup {
    val digitalServiceName = "digital-service-123"
    val serviceOwner       = TeamMember(Some("Jack Low"), None, None, None, None, Some("jack.low"))

    val teamsAndRepositoriesConnectorMock   = mock[TeamsAndRepositoriesConnector]
    val userManagementConnectorMock         = mock[UserManagementConnector]
    private val userManagementPortalConfig  = mock[UserManagementPortalConfig]

    when(userManagementPortalConfig.userManagementProfileBaseUrl)
      .thenReturn("http://things.things.com")

    val catalogueController = new CatalogueController(
      userManagementConnector       = userManagementConnectorMock,
      teamsAndRepositoriesConnector = teamsAndRepositoriesConnectorMock,
      configService                 = mock[ConfigService],
      routeRulesService             = mock[RouteRulesService],
      serviceDependencyConnector    = mock[ServiceDependenciesConnector],
      leakDetectionService          = mock[LeakDetectionService],
      shutterService                = mock[ShutterService],
      defaultBranchesService        = mock[DefaultBranchesService],
      auth                          = mock[FrontendAuthComponents],
      userManagementPortalConfig    = userManagementPortalConfig,
      configuration                 = Configuration.empty,
      mcc                           = stubMessagesControllerComponents(),
      digitalServiceInfoPage        = digitalServiceInfoPage,
      whatsRunningWhereService      = mock[WhatsRunningWhereService],
      indexPage                     = mock[IndexPage],
      teamInfoPage                  = mock[TeamInfoPage],
      serviceInfoPage               = mock[ServiceInfoPage],
      serviceConfigPage             = mock[ServiceConfigPage],
      serviceConfigRawPage          = mock[ServiceConfigRawPage],
      libraryInfoPage               = mock[LibraryInfoPage],
      prototypeInfoPage             = mock[PrototypeInfoPage],
      repositoryInfoPage            = mock[RepositoryInfoPage],
      repositoriesListPage          = mock[RepositoriesListPage],
      defaultBranchListPage         = mock[DefaultBranchListPage],
      outOfDateTeamDependenciesPage = mock[OutOfDateTeamDependenciesPage]
    )
  }

  private def asDocument(html: String): Document =
    Jsoup.parse(html)

  def readFile(jsonFilePath: String): String = {
    val path = "__files/" + jsonFilePath
    try {
      Source.fromResource(path).getLines.mkString("\n")
    } catch {
      case _: NullPointerException => sys.error(s"Could not find file $path")
    }
  }

  private def verifyTeamMemberHrefLinks(document: Document): Boolean = {
    val hrefs = document.select("#team_members [href]").iterator.asScala.toList

    hrefs.size shouldBe 4
    hrefs(0).attributes().get("href") == "http://example.com/profile/joe.black"
    hrefs(1).attributes().get("href") == "http://example.com/profile/james.roger"
    hrefs(2).attributes().get("href") == "http://example.com/profile/casey.binge"
    hrefs(3).attributes().get("href") == "http://example.com/profile/marc.pallazo"
  }

  private def verifyTeamMemberElementsText(document: Document): Unit = {
    val teamMembersLiElements = document.select("#team_members li").iterator.asScala.toList

    teamMembersLiElements.length shouldBe 4

    teamMembersLiElements(0).text() should include("Joe Black")
    teamMembersLiElements(1).text() should include("James Roger")
    teamMembersLiElements(2).text() should include("Casey Binge")
    teamMembersLiElements(3).text() should include("Marc Palazzo")
  }
}
