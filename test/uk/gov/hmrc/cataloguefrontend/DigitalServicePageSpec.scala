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
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.Configuration
import play.api.i18n.MessagesApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WS
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.cataloguefrontend.TeamsAndRepositoriesConnector.HTTPError
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.TeamMember
import uk.gov.hmrc.cataloguefrontend.connector.{IndicatorsConnector, ServiceDependenciesConnector}
import uk.gov.hmrc.cataloguefrontend.events.{EventService, ReadModelService}
import uk.gov.hmrc.cataloguefrontend.service.DeploymentsService
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.io.Source

class DigitalServicePageSpec extends UnitSpec with BeforeAndAfter with OneServerPerSuite with WireMockEndpoints with MockitoSugar with ScalaFutures {

  def asDocument(html: String): Document = Jsoup.parse(html)

  val umpFrontPageUrl = "http://some.ump.fontpage.com"

  implicit override lazy val app = new GuiceApplicationBuilder().configure (
    "microservice.services.teams-and-services.host" -> host,
    "microservice.services.teams-and-services.port" -> endpointPort,
    "microservice.services.indicators.port" -> endpointPort,
    "microservice.services.indicators.host" -> host,
    "microservice.services.user-management.url" -> endpointMockUrl,
    "usermanagement.portal.url" -> "http://usermanagement/link",
    "microservice.services.user-management.frontPageUrl" -> umpFrontPageUrl,
    "play.ws.ssl.loose.acceptAnyCertificate" -> true,
    "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler").build()


  val digitalServiceName = "digital-service-a"

  val serviceOwner = TeamMember(Some("Jack Low"), None, None, None, None, Some("jack.low"))
  val mockedModelService = mock[ReadModelService]
  when(mockedModelService.getDigitalServiceOwner(any())).thenReturn(Some(serviceOwner))

  "DigitalService page" should {

    "show a list of libraries, services, prototypes and repositories" in {
      serviceEndpoint(GET, s"/api/digital-services/$digitalServiceName", willRespondWith = (200, Some(
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
      )))

      val response = await(WS.url(s"http://localhost:$port/digital-service/$digitalServiceName").get)

      response.status shouldBe 200

      response.body should include("""<a href="/service/A">A</a>""")
      response.body should include("""<a href="/prototype/B">B</a>""")
      response.body should include("""<a href="/library/C">C</a>""")
      response.body should include("""<a href="/repositories/D">D</a>""")

    }


    "show a message if no services are found" in {

      serviceEndpoint(GET, s"/api/digital-services/$digitalServiceName", willRespondWith = (200, Some(
        s"""{
           |   "name":"$digitalServiceName",
           |   "lastUpdatedAt":12345,
           |   "repositories":[]
           | }""".stripMargin

      )))

      val response = await(WS.url(s"http://localhost:$port/digital-service/$digitalServiceName").get)

      response.status shouldBe 200
      response.body should include(ViewMessages.noRepoOfTypeForDigitalService("service"))
      response.body should include(ViewMessages.noRepoOfTypeForDigitalService("library"))
      response.body should include(ViewMessages.noRepoOfTypeForDigitalService("prototype"))
      response.body should include(ViewMessages.noRepoOfTypeForDigitalService("other"))
    }

    "show 'Not specified' if service owner is not set" in {


      val digitalServiceName = "digital-service-123"
      val response = await(WS.url(s"http://localhost:$port/digital-service/${digitalServiceName}").get)

      val document = asDocument(response.body)

      val serviceOwnerO = document.select("#service_owner_edit_input").iterator().toList.headOption

      serviceOwnerO.isDefined shouldBe true
      serviceOwnerO.get.attr("value") shouldBe "Not specified"
    }

    "show service owner" in {



      val mockedConnector = mock[TeamsAndRepositoriesConnector]
      when(mockedConnector.digitalServiceInfo(any())(any())).thenReturn(Future.successful(Left(HTTPError(999))))



      val catalogueController =         new CatalogueController(
        mock[UserManagementConnector],
        mockedConnector,
        mock[ServiceDependenciesConnector],
        mock[IndicatorsConnector],
        mock[DeploymentsService],
        mock[EventService],
        mockedModelService,
        mock[play.api.Environment],
        app.configuration,
        mock[MessagesApi])

      val digitalServiceName = "digital-service-123"

      val responseF = catalogueController.digitalService(digitalServiceName)(FakeRequest())

      val response = responseF.futureValue

      import play.api.test.Helpers._
      val document = asDocument(contentAsString(response))

      val serviceOwnerO = document.select("#service_owner_edit_input").iterator().toList.headOption

      serviceOwnerO.isDefined shouldBe true
      serviceOwnerO.get.attr("value") shouldBe serviceOwner.getDisplayName
    }


    "show team members for teams correctly" in {
      val team1 = "Team1"
      val team2 = "Team2"

      val teamNames = Seq(team1, team2)
      val digitalServiceName = "digital-service-123"


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
      serviceEndpoint(GET, s"/api/digital-services/$digitalServiceName", willRespondWith = (200, Some(
        json
      )))


      mockHttpApiCall(s"/v2/organisations/teams/$team1/members", "/user-management-response-team1.json")
      mockHttpApiCall(s"/v2/organisations/teams/$team2/members", "/user-management-response-team2.json")

      val response = await(WS.url(s"http://localhost:$port/digital-service/${digitalServiceName}").get)

      response.status shouldBe 200
      val document = asDocument(response.body)


      verifyTeamMemberElementsText(document)

      verifyTeamMemberHrefLinks(document)

    }

    "show the right error message when unable to connect to teams-and-repositories" in {

      val digitalServiceName = "digital-service-123"

      val teamsAndRepositoriesConnectorMock = mock[TeamsAndRepositoriesConnector]

      val catalogueController =         new CatalogueController(
        mock[UserManagementConnector],
        teamsAndRepositoriesConnectorMock,
        mock[ServiceDependenciesConnector],
        mock[IndicatorsConnector],
        mock[DeploymentsService],
        mock[EventService],
        mockedModelService,
        mock[play.api.Environment],
        app.configuration,
        mock[MessagesApi])

      val exception = new RuntimeException("Boooom!")

      when(teamsAndRepositoriesConnectorMock.digitalServiceInfo(any())(any())).thenReturn(
        Future.successful(
          Left(TeamsAndRepositoriesConnector.ConnectionError(exception))
        )
      )

      val response: Result = catalogueController.digitalService(digitalServiceName)(FakeRequest()).futureValue

      response.header.status shouldBe 200

      import play.api.test.Helpers._
      val document: Document = asDocument(contentAsString(response))

      document.select("#teams-and-repositories-error").text() should === (s"""A connection error to the teams-and-repositories microservice occurred while retrieving digital service ($digitalServiceName)""")
    }

    "show the right error message when unable to connect to ump" in {

      val digitalServiceName = "digital-service-123"

      val teamsAndRepositoriesConnectorMock = mock[TeamsAndRepositoriesConnector]
      val umpConnectorMock = mock[UserManagementConnector]

      val catalogueController =         new CatalogueController(
        umpConnectorMock,
        teamsAndRepositoriesConnectorMock,
        mock[ServiceDependenciesConnector],
        mock[IndicatorsConnector],
        mock[DeploymentsService],
        mock[EventService],
        mockedModelService,
        mock[play.api.Environment],
        app.configuration,
        mock[MessagesApi])

      val teamName = "Team1"

      val exception = new RuntimeException("Boooom!")
      when(teamsAndRepositoriesConnectorMock.digitalServiceInfo(any())(any())).thenReturn(Future.successful(Right(DigitalService(digitalServiceName, 1, Nil))))
      when(umpConnectorMock.getTeamMembersForTeams(any())(any())).thenReturn(
        Future.successful(
          Map(teamName -> Left(UserManagementConnector.ConnectionError(exception)))
        )
      )


      val response: Result = catalogueController.digitalService(digitalServiceName)(FakeRequest()).futureValue
      response.header.status shouldBe 200

      import play.api.test.Helpers._

      val document: Document = asDocument(contentAsString(response))

      println(contentAsString(response))
      document.select(s"#ump-error-$teamName").text() should === ("Sorry, the User Management Portal is not available")
    }

    "show the right error message the ump send no data" in {

      val digitalServiceName = "digital-service-123"

      val teamsAndRepositoriesConnectorMock = mock[TeamsAndRepositoriesConnector]
      val umpConnectorMock = mock[UserManagementConnector]

      val catalogueController =         new CatalogueController(
        umpConnectorMock,
        teamsAndRepositoriesConnectorMock,
        mock[ServiceDependenciesConnector],
        mock[IndicatorsConnector],
        mock[DeploymentsService],
        mock[EventService],
        mockedModelService,
        mock[play.api.Environment],
        app.configuration,
        mock[MessagesApi])

      val teamName = "Team1"

      val exception = new RuntimeException("Boooom!")

      when(teamsAndRepositoriesConnectorMock.digitalServiceInfo(any())(any())).thenReturn(Future.successful(Right(DigitalService(digitalServiceName, 1, Nil))))
      when(umpConnectorMock.getTeamMembersForTeams(any())(any())).thenReturn(
        Future.successful(
          Map(teamName -> Left(UserManagementConnector.NoData("https://some-link-to-rectify")))
        )
      )


      val response: Result = catalogueController.digitalService(digitalServiceName)(FakeRequest()).futureValue
      response.header.status shouldBe 200

      import play.api.test.Helpers._

      val document: Document = asDocument(contentAsString(response))

      println(contentAsString(response))
      document.select(s"#ump-error-$teamName").text() should === (s"$teamName is unknown to the User Management Portal. To add the team, please raise a TSR")
    }

    "show the right error message the ump gives an Http error" in {

      val digitalServiceName = "digital-service-123"

      val teamsAndRepositoriesConnectorMock = mock[TeamsAndRepositoriesConnector]
      val umpConnectorMock = mock[UserManagementConnector]

      val catalogueController =         new CatalogueController(
        umpConnectorMock,
        teamsAndRepositoriesConnectorMock,
        mock[ServiceDependenciesConnector],
        mock[IndicatorsConnector],
        mock[DeploymentsService],
        mock[EventService],
        mockedModelService,
        mock[play.api.Environment],
        app.configuration,
        mock[MessagesApi])

      val teamName = "Team1"

      val exception = new RuntimeException("Boooom!")

      when(teamsAndRepositoriesConnectorMock.digitalServiceInfo(any())(any())).thenReturn(Future.successful(Right(DigitalService(digitalServiceName, 1, Nil))))
      when(umpConnectorMock.getTeamMembersForTeams(any())(any())).thenReturn(
        Future.successful(
          Map(teamName -> Left(UserManagementConnector.HTTPError(404)))
        )
      )


      val response: Result = catalogueController.digitalService(digitalServiceName)(FakeRequest()).futureValue
      response.header.status shouldBe 200

      import play.api.test.Helpers._

      val document: Document = asDocument(contentAsString(response))

      println(contentAsString(response))
      document.select(s"#ump-error-$teamName").text() should === (s"Sorry, the User Management Portal is not available")
    }

  }

  def mockHttpApiCall(url: String, jsonResponseFile: String, httpCodeToBeReturned: Int = 200): String = {

    val json = readFile(jsonResponseFile)

    serviceEndpoint(
      method = GET,
      url = url,
      willRespondWith = (httpCodeToBeReturned, Some(json)))

    json
  }

  def readFile(jsonFilePath: String): String = {
    Source.fromURL(getClass.getResource(jsonFilePath)).getLines().mkString("\n")
  }

  def verifyTeamMemberHrefLinks(document: Document): Boolean = {
    val hrefs = document.select("#team_members [href]").iterator().toList

    hrefs.size shouldBe 4
    hrefs(0).attributes().get("href") == "http://example.com/profile/joe.black"
    hrefs(1).attributes().get("href") == "http://example.com/profile/james.roger"
    hrefs(2).attributes().get("href") == "http://example.com/profile/casey.binge"
    hrefs(3).attributes().get("href") == "http://example.com/profile/marc.pallazo"
  }

  def verifyTeamMemberElementsText(document: Document): Unit = {
    val teamMembersLiElements = document.select("#team_members li").iterator().toList

    teamMembersLiElements.length shouldBe 4

    teamMembersLiElements(0).text() should include("Joe Black")
    teamMembersLiElements(1).text() should include("James Roger")
    teamMembersLiElements(2).text() should include("Casey Binge")
    teamMembersLiElements(3).text() should include("Marc Palazzo")
  }

}