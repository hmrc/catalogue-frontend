/*
 * Copyright 2018 HM Revenue & Customs
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
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET => _, _}
import uk.gov.hmrc.cataloguefrontend.actions.{ActionsSupport, UmpVerifiedRequest}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.{TeamMember, UMPError}
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.events.{EventService, ReadModelService}
import uk.gov.hmrc.cataloguefrontend.service.{ConfigService, DeploymentsService, LeakDetectionService}
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import uk.gov.hmrc.play.test.UnitSpec
import views.html._

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

class DigitalServicePageSpec
    extends UnitSpec
    with GuiceOneServerPerSuite
    with WireMockEndpoints
    with MockitoSugar
    with ActionsSupport {

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.teams-and-repositories.host"  -> host,
        "microservice.services.teams-and-repositories.port"  -> endpointPort,
        "microservice.services.indicators.port"              -> endpointPort,
        "microservice.services.indicators.host"              -> host,
        "microservice.services.user-management.url"          -> endpointMockUrl,
        "usermanagement.portal.url"                          -> "http://usermanagement/link",
        "microservice.services.user-management.frontPageUrl" -> "http://some.ump.fontpage.com",
        "play.ws.ssl.loose.acceptAnyCertificate"             -> true,
        "play.http.requestHandler"                           -> "play.api.http.DefaultHttpRequestHandler"
      )
      .build()

  private[this] lazy val WS                     = app.injector.instanceOf[WSClient]
  private[this] lazy val viewMessages           = app.injector.instanceOf[ViewMessages]
  private[this] lazy val digitalServiceInfoPage = app.injector.instanceOf[DigitalServiceInfoPage]

  private[this] val digitalServiceName = "digital-service-a"

  "DigitalService page" should {

    "show a list of libraries, services, prototypes and repositories" in {
      serviceEndpoint(
        GET,
        s"/api/digital-services/$digitalServiceName",
        willRespondWith = (
          200,
          Some(
            s"""{
           |  "name": "$digitalServiceName",
           |  "lastUpdatedAt": 1494240869000,
           |  "repositories": [
           |    {
           |      "name": "A",
           |      "createdAt": 1456326530000,
           |      "lastUpdatedAt": 1494240809000,
           |      "repoType": "Service",
           |      "teamNames": ["Team1", "Team2"]
           |    },
           |    {
           |      "name": "B",
           |      "createdAt": 1491916469000,
           |      "lastUpdatedAt": 1494240869000,
           |      "repoType": "Prototype",
           |      "teamNames": ["Team1"]
           |    },
           |    {
           |      "name": "C",
           |      "createdAt": 1454669716000,
           |      "lastUpdatedAt": 1494240838000,
           |      "repoType": "Library",
           |      "teamNames": ["Team2"]
           |    },
           |    {
           |      "name": "D",
           |      "createdAt": 1454669716000,
           |      "lastUpdatedAt": 1494240838000,
           |      "repoType": "Other",
           |      "teamNames": ["Team3"]
           |    }
           |  ]
           |}""".stripMargin
          ))
      )

      val response = await(WS.url(s"http://localhost:$port/digital-service/$digitalServiceName").get)

      response.status shouldBe 200

      response.body should include("""<a href="/prototype/B">B</a>""")
      response.body should include("""<a href="/library/C">C</a>""")
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
           |   "name":"$digitalServiceName",
           |   "lastUpdatedAt":12345,
           |   "repositories":[]
           | }""".stripMargin
          ))
      )

      val response = await(WS.url(s"http://localhost:$port/digital-service/$digitalServiceName").get)

      response.status shouldBe 200
      response.body   should include(viewMessages.noRepoOfTypeForDigitalService("service"))
      response.body   should include(viewMessages.noRepoOfTypeForDigitalService("library"))
      response.body   should include(viewMessages.noRepoOfTypeForDigitalService("prototype"))
      response.body   should include(viewMessages.noRepoOfTypeForDigitalService("other"))
    }

    "show 'Not specified' if service owner is not set" in {

      val digitalServiceName = "digital-service-123"

      serviceEndpoint(
        GET,
        s"/api/digital-services/$digitalServiceName",
        willRespondWith = (
          200,
          Some(
            s"""{
           |   "name":"$digitalServiceName",
           |   "lastUpdatedAt":12345,
           |   "repositories":[]
           | }""".stripMargin
          ))
      )
      val response = await(WS.url(s"http://localhost:$port/digital-service/$digitalServiceName").get)

      val document = asDocument(response.body)

      val serviceOwnerO = document.select("#service_owner_edit_input").iterator().toList.headOption

      serviceOwnerO.isDefined         shouldBe true
      serviceOwnerO.get.attr("value") shouldBe "Not specified"
    }

    "show service owner" in new MockedCatalogueFrontendSetup {

      when(teamsAndRepositoriesConnectorMock.digitalServiceInfo(any())(any()))
        .thenReturn(Future(Some(DigitalService(digitalServiceName, 0L, Seq.empty))))

      when(userManagementConnectorMock.getTeamMembersForTeams(any())(any()))
        .thenReturn(Future(Map.empty[String, Either[UMPError, Seq[TeamMember]]]))

      val response = catalogueController.digitalService(digitalServiceName)(FakeRequest())

      val document = asDocument(contentAsString(response))

      val serviceOwnerO = document.select("#service_owner_edit_input").iterator().toList.headOption

      serviceOwnerO.isDefined         shouldBe true
      serviceOwnerO.get.attr("value") shouldBe serviceOwner.getDisplayName
    }

    "show edit button if user is singed-in" in {
      val digitalServiceDetails = DigitalServiceDetails("", Map.empty, Map.empty)
      val request               = UmpVerifiedRequest(FakeRequest(), stubMessagesApi(), isSignedIn = true)

      val document =
        Jsoup.parse(new DigitalServiceInfoPage(mock[ViewMessages])(digitalServiceDetails, None)(request).toString)

      document.select("#edit-button").isEmpty shouldBe false
    }

    "don't show edit button if user is NOT singed-in" in {
      val digitalServiceDetails = DigitalServiceDetails("", Map.empty, Map.empty)
      val request               = UmpVerifiedRequest(FakeRequest(), stubMessagesApi(), isSignedIn = false)

      val document =
        Jsoup.parse(new DigitalServiceInfoPage(mock[ViewMessages])(digitalServiceDetails, None)(request).toString)

      document.select("#edit-button").isEmpty shouldBe true
    }

    "show team members for teams correctly" in {

      val digitalServiceName = "digital-service-123"
      val team1              = "Team1"
      val team2              = "Team2"

      val json =
        s"""
           |{
           |  "name": "Catalogue",
           |  "lastUpdatedAt": 1494864193000,
           |  "repositories": [
           |    {
           |      "name": "catalogue-frontend",
           |      "createdAt": 1456326530000,
           |      "lastUpdatedAt": 1494864193000,
           |      "repoType": "Service",
           |      "teamNames": [ "$team1", "$team2"]
           |    }
           |  ]
           |}
        """.stripMargin

      serviceEndpoint(
        GET,
        s"/api/digital-services/$digitalServiceName",
        willRespondWith = (
          200,
          Some(
            json
          )))

      mockHttpApiCall(s"/v2/organisations/teams/$team1/members", "/user-management-response-team1.json")
      mockHttpApiCall(s"/v2/organisations/teams/$team2/members", "/user-management-response-team2.json")

      val response = await(WS.url(s"http://localhost:$port/digital-service/$digitalServiceName").get)

      response.status shouldBe 200

      val document = asDocument(response.body)
      verifyTeamMemberElementsText(document)
      verifyTeamMemberHrefLinks(document)
    }

    "show the right error message when unable to connect to ump" in new MockedCatalogueFrontendSetup {

      val teamName = "Team1"

      when(teamsAndRepositoriesConnectorMock.digitalServiceInfo(any())(any()))
        .thenReturn(Future.successful(Some(DigitalService(digitalServiceName, 1, Nil))))
      when(userManagementConnectorMock.getTeamMembersForTeams(any())(any())).thenReturn(
        Future.successful(
          Map(teamName -> Left(UserManagementConnector.ConnectionError(new RuntimeException("Boooom!"))))
        )
      )

      val response = catalogueController.digitalService(digitalServiceName)(FakeRequest())

      status(response) shouldBe 200

      val document = asDocument(contentAsString(response))

      document.select(s"#ump-error-$teamName").text() should ===("Sorry, the User Management Portal is not available")
    }

    "show the right error message the ump send no data" in new MockedCatalogueFrontendSetup {

      val teamName = "Team1"

      when(teamsAndRepositoriesConnectorMock.digitalServiceInfo(any())(any()))
        .thenReturn(Future.successful(Some(DigitalService(digitalServiceName, 1, Nil))))
      when(userManagementConnectorMock.getTeamMembersForTeams(any())(any())).thenReturn(
        Future.successful(
          Map(teamName -> Left(UserManagementConnector.NoData("https://some-link-to-rectify")))
        )
      )

      val response = catalogueController.digitalService(digitalServiceName)(FakeRequest())

      status(response) shouldBe 200

      val document = asDocument(contentAsString(response))

      document.select(s"#ump-error-$teamName").text() should ===(
        s"$teamName is unknown to the User Management Portal. To add the team, please raise a TSR")
    }

    "show the right error message the ump gives an Http error" in new MockedCatalogueFrontendSetup {

      val teamName = "Team1"

      when(teamsAndRepositoriesConnectorMock.digitalServiceInfo(any())(any()))
        .thenReturn(Future.successful(Some(DigitalService(digitalServiceName, 1, Nil))))
      when(userManagementConnectorMock.getTeamMembersForTeams(any())(any())).thenReturn(
        Future.successful(
          Map(teamName -> Left(UserManagementConnector.HTTPError(404)))
        )
      )

      val response = catalogueController.digitalService(digitalServiceName)(FakeRequest())

      status(response) shouldBe 200

      val document = asDocument(contentAsString(response))

      document.select(s"#ump-error-$teamName").text() should ===(s"Sorry, the User Management Portal is not available")
    }
  }

  private trait MockedCatalogueFrontendSetup {
    val digitalServiceName = "digital-service-123"
    val serviceOwner       = TeamMember(Some("Jack Low"), None, None, None, None, Some("jack.low"))

    val teamsAndRepositoriesConnectorMock   = mock[TeamsAndRepositoriesConnector]
    val userManagementConnectorMock         = mock[UserManagementConnector]
    private val userManagementPortalConfig  = mock[UserManagementPortalConfig]
    private val mockedModelService          = mock[ReadModelService]
    private val userManagementAuthConnector = mock[UserManagementAuthConnector]
    private val controllerComponents        = stubMessagesControllerComponents()
    private val verifySignInStatusPassThrough =
      new VerifySignInStatusPassThrough(userManagementAuthConnector, controllerComponents)
    private val umpAuthenticatedPassThrough =
      new UmpAuthenticatedPassThrough(userManagementAuthConnector, controllerComponents)

    when(mockedModelService.getDigitalServiceOwner(any())).thenReturn(Some(serviceOwner))
    when(userManagementPortalConfig.userManagementProfileBaseUrl) thenReturn "http://things.things.com"

    val catalogueController = new CatalogueController(
      userManagementConnectorMock,
      teamsAndRepositoriesConnectorMock,
      mock[ConfigService],
      mock[ServiceDependenciesConnector],
      mock[IndicatorsConnector],
      mock[LeakDetectionService],
      mock[DeploymentsService],
      mock[EventService],
      mockedModelService,
      verifySignInStatusPassThrough,
      umpAuthenticatedPassThrough,
      userManagementPortalConfig,
      stubMessagesControllerComponents(),
      digitalServiceInfoPage,
      mock[IndexPage],
      mock[TeamInfoPage],
      mock[ServiceInfoPage],
      mock[ServiceConfigPage],
      mock[ServiceConfigRawPage],
      mock[LibraryInfoPage],
      mock[PrototypeInfoPage],
      mock[RepositoryInfoPage],
      mock[RepositoriesListPage]
    )
  }

  private def asDocument(html: String): Document =
    Jsoup.parse(html)

  private def mockHttpApiCall(url: String, jsonResponseFile: String, httpCodeToBeReturned: Int = 200): String = {

    val json = readFile(jsonResponseFile)

    serviceEndpoint(method = GET, url = url, willRespondWith = (httpCodeToBeReturned, Some(json)))

    json
  }

  private def readFile(jsonFilePath: String): String =
    Source.fromURL(getClass.getResource(jsonFilePath)).getLines().mkString("\n")

  private def verifyTeamMemberHrefLinks(document: Document): Boolean = {
    val hrefs = document.select("#team_members [href]").iterator().toList

    hrefs.size shouldBe 4
    hrefs(0).attributes().get("href") == "http://example.com/profile/joe.black"
    hrefs(1).attributes().get("href") == "http://example.com/profile/james.roger"
    hrefs(2).attributes().get("href") == "http://example.com/profile/casey.binge"
    hrefs(3).attributes().get("href") == "http://example.com/profile/marc.pallazo"
  }

  private def verifyTeamMemberElementsText(document: Document): Unit = {
    val teamMembersLiElements = document.select("#team_members li").iterator().toList

    teamMembersLiElements.length shouldBe 4

    teamMembersLiElements(0).text() should include("Joe Black")
    teamMembersLiElements(1).text() should include("James Roger")
    teamMembersLiElements(2).text() should include("Casey Binge")
    teamMembersLiElements(3).text() should include("Marc Palazzo")
  }
}
