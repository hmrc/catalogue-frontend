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

package uk.gov.hmrc.cataloguefrontend.connector

import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.UmpUserId
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.{DisplayName, TeamMember, UMPError}
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.UserManagementPortalConfig
import uk.gov.hmrc.http.{BadGatewayException, HeaderCarrier, HttpClient}
import uk.gov.hmrc.http.test.WireMockSupport

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import org.scalatest.concurrent.IntegrationPatience

class UserManagementConnectorSpec
  extends AnyWordSpec
     with Matchers
     with BeforeAndAfterEach
     with GuiceOneServerPerSuite
     with ScalaFutures
     with IntegrationPatience
     with EitherValues
     with OptionValues
     with WireMockSupport
     with MockitoSugar {

  import ExecutionContext.Implicits.global

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.user-management.url"        -> wireMockUrl,
        "microservice.services.user-management.myTeamsUrl" -> "http://some.ump.com/myTeams",
        "play.http.requestHandler"                         -> "play.api.http.DefaultHttpRequestHandler",
        "metrics.jvm"                                      -> false
      )
      .build()

  private lazy val userManagementConnector: UserManagementConnector =
    app.injector.instanceOf[UserManagementConnector]

  private lazy val userManagementPortalConfig: UserManagementPortalConfig =
    app.injector.instanceOf[UserManagementPortalConfig]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "User management connector" should {
    "should get the team members from the user-management service" in {
      val teamMembers: Seq[TeamMember] =
        callExternalMockedService(TeamName("team-chicken"), Some("/user-management-response.json")).futureValue.right.value

      teamMembers should have length 2

      teamMembers.headOption.value shouldBe TeamMember(
        displayName     = Some("Jim Willman"),
        familyName      = Some("Willman"),
        givenName       = Some("Jim"),
        primaryEmail    = Some("jim.willman@digital.hmrc.gov.uk"),
        serviceOwnerFor = Some(Seq("MATO")),
        username        = Some("jim.willman")
      )

      teamMembers(1) shouldBe TeamMember(
        displayName     = Some("Karl GoJarvis"),
        familyName      = Some("GoJarvis"),
        givenName       = Some("Karl"),
        primaryEmail    = Some("karl.gojarvis@hmrc.gsi.gov.uk"),
        serviceOwnerFor = Some(Seq("CATO", "SOME-SERVICE")),
        username        = Some("karl.gojarvis")
      )
    }

    "has an empty members array in json" in {
      val res =
        callExternalMockedService(TeamName("team-chicken"), Some("/user-management-empty-members.json")).futureValue
      res shouldBe Right(Seq.empty)
    }

    "no members field in json" in {
      val res =
        callExternalMockedService(TeamName("team-chicken"), Some("/user-management-no-members.json")).futureValue
      res.left.get.isInstanceOf[UMPError.ConnectionError] shouldBe true
    }

    "api returns an error code" in {
      val error: UMPError = callExternalMockedService(TeamName("team-chicken"), None, 500).futureValue.left.value

      error shouldBe UMPError.HTTPError(500)
    }

    "api returns not found" in {
      val error: UMPError = callExternalMockedService(TeamName("team-chicken"), None, 404).futureValue.left.value

      error shouldBe UMPError.UnknownTeam
    }

    "api returns a connection error" in {
      val mockedHttpGet = mock[HttpClient]

      val userManagementConnector = new UserManagementConnector(
        mockedHttpGet,
        userManagementPortalConfig
      )

      when(mockedHttpGet.GET(any())(any(), any(), any()))
        .thenReturn(Future.failed(new RuntimeException("some error")))

      val error: UMPError = userManagementConnector
        .getTeamMembersFromUMP(TeamName("teamName"))
        .futureValue
        .left
        .value
      error.isInstanceOf[UMPError.ConnectionError] shouldBe true
    }

    "should get the team details from the user-management service" in {
      stubFor(
          get(urlEqualTo(s"/v2/organisations/teams/TEAM-A"))
            .willReturn(aResponse().withStatus(200).withBody(readFile("/user-management-team-details-response.json")))
        )

      val teamDetails = userManagementConnector
        .getTeamDetails(TeamName("TEAM-A"))
        .futureValue
        .right
        .value

      teamDetails.description.value       shouldBe "TEAM-A is a great team"
      teamDetails.location.value          shouldBe "STLPD"
      teamDetails.organisation.value      shouldBe "ORGA"
      teamDetails.slack.value             shouldBe "https://slack.host/messages/team-A"
      teamDetails.slackNotification.value shouldBe "https://slack.host/messages/team-A-NOTIFICATION"
      teamDetails.documentation.value     shouldBe "https://some.documentation.url"
    }

    "no organization/data field in json for team details" in {
      stubFor(
          get(urlEqualTo(s"/v2/organisations/teams/TEAM-A"))
            .willReturn(aResponse().withStatus(200).withBody(readFile("/user-management-team-details-nodata-response.json")))
        )

      val res = userManagementConnector
        .getTeamDetails(TeamName("TEAM-A")).futureValue

      res.left.get.isInstanceOf[UMPError.ConnectionError] shouldBe true
    }

    "api returns an error code for team details" in {
      stubFor(
          get(urlEqualTo(s"/v2/organisations/teams/TEAM-A"))
            .willReturn(aResponse().withStatus(500))
        )

      val teamDetails = userManagementConnector
        .getTeamDetails(TeamName("TEAM-A"))
        .futureValue
        .left
        .value

      teamDetails shouldBe UMPError.HTTPError(500)
    }

    "api returns a connection error for team details" in {
      val mockedHttpGet = mock[HttpClient]

      val userManagementConnector = new UserManagementConnector(
        mockedHttpGet,
        userManagementPortalConfig
      )

      when(mockedHttpGet.GET(any())(any(), any(), any()))
        .thenReturn(Future.failed(new RuntimeException("some error")))

      val error: UMPError = userManagementConnector
        .getTeamDetails(TeamName("TEAM-A"))
        .futureValue
        .left
        .value
      error.isInstanceOf[UMPError.ConnectionError] shouldBe true
    }

    "getTeamMembersForTeams" should {
      "should get the team members for multiple teams" in {
        val teamNames = Seq(TeamName("Team1"), TeamName("Team2"))

        stubFor(
          get(urlEqualTo("/v2/organisations/teams/Team1/members"))
            .willReturn(aResponse().withStatus(200).withBody(readFile("/user-management-response-team1.json")))
        )

        stubFor(
          get(urlEqualTo("/v2/organisations/teams/Team2/members"))
            .willReturn(aResponse().withStatus(200).withBody(readFile("/user-management-response-team2.json")))
        )

        val teamsAndMembers = userManagementConnector
          .getTeamMembersForTeams(teamNames)
          .futureValue

        teamsAndMembers.keys should contain theSameElementsAs teamNames

        def getMembersDetails(extractor: TeamMember => String): Iterable[String] =
          teamsAndMembers.values.flatMap(_.right.value).map(extractor)

        getMembersDetails(_.displayName.value)  shouldBe Seq("Joe Black", "James Roger", "Casey Binge", "Marc Palazzo")
        getMembersDetails(_.username.value)     shouldBe Seq("joe.black", "james.roger", "casey.binge", "marc.palazzo")
        getMembersDetails(_.primaryEmail.value) shouldBe Seq(
          "joe.black@digital.hmrc.gov.uk",
          "james.roger@hmrc.gsi.gov.uk",
          "casey.binge@digital.hmrc.gov.uk",
          "marc.palazzo@hmrc.gsi.gov.uk"
        )
      }

      "should return an Http error for calls with non-200 status codes" in {
        val teamNames = Seq(TeamName("Team1"), TeamName("Team2"))

        stubFor(
          get(urlEqualTo("/v2/organisations/teams/Team1/members"))
            .willReturn(aResponse().withStatus(200).withBody(readFile("/user-management-response-team1.json")))
        )

        stubFor(
          get(urlEqualTo("/v2/organisations/teams/Team2/members"))
            .willReturn(aResponse().withStatus(404))
        )

        val teamsAndMembers: Map[TeamName, Either[UMPError, Seq[TeamMember]]] = userManagementConnector
          .getTeamMembersForTeams(teamNames)
          .futureValue

        teamsAndMembers.keys should contain theSameElementsAs teamNames

        val team1Result = teamsAndMembers.get(TeamName("Team1")).value
        val team2Result = teamsAndMembers.get(TeamName("Team2")).value

        team2Result shouldBe Left(UMPError.UnknownTeam)

        def getMembersDetails(extractor: TeamMember => String): Iterable[String] =
          team1Result.right.value.map(extractor)

        getMembersDetails(_.displayName.value)  shouldBe Seq("Joe Black", "James Roger")
        getMembersDetails(_.username.value)     shouldBe Seq("joe.black", "james.roger")
        getMembersDetails(_.primaryEmail.value) shouldBe Seq(
          "joe.black@digital.hmrc.gov.uk",
          "james.roger@hmrc.gsi.gov.uk")
      }

      "should return a connection error for calls which fail with an exception" in {
        val mockedHttpGet = mock[HttpClient]

        val userManagementConnector = new UserManagementConnector(
          mockedHttpGet,
          userManagementPortalConfig
        )

        val teamNames = Seq(TeamName("Team1"), TeamName("Team2"))

        when(mockedHttpGet.GET(any())(any(), any(), any()))
          .thenReturn(Future.failed(new RuntimeException("Boooom!")))

        val teamsAndMembers: Map[TeamName, Either[UMPError, Seq[TeamMember]]] = userManagementConnector
          .getTeamMembersForTeams(teamNames)
          .futureValue

        teamsAndMembers.keys should contain theSameElementsAs teamNames

        val team1Result = teamsAndMembers.get(TeamName("Team1")).value
        val team2Result = teamsAndMembers.get(TeamName("Team2")).value

        team1Result.left.get.isInstanceOf[UMPError.ConnectionError] shouldBe true
        team2Result.left.get.isInstanceOf[UMPError.ConnectionError] shouldBe true
      }

      "should return a no data error if the json from UMP doesn't conform to the expected shape" in {
        val teamNames = Seq(TeamName("Team1"), TeamName("Team2"))

        stubFor(
          get(urlEqualTo("/v2/organisations/teams/Team1/members"))
            .willReturn(aResponse().withStatus(200).withBody(readFile("/user-management-response-team1.json")))
        )

        stubFor(
          get(urlEqualTo("/v2/organisations/teams/Team2/members"))
            .willReturn(aResponse().withStatus(200).withBody(readFile("/user-management-team-details-nodata-response.json")))
        )

        val teamsAndMembers: Map[TeamName, Either[UMPError, Seq[TeamMember]]] = userManagementConnector
          .getTeamMembersForTeams(teamNames)
          .futureValue

        teamsAndMembers.keys should contain theSameElementsAs teamNames

        val team1Result = teamsAndMembers.get(TeamName("Team1")).value
        val team2Result = teamsAndMembers.get(TeamName("Team2")).value

        team2Result.left.get.isInstanceOf[UMPError.ConnectionError] shouldBe true

        def getMembersDetails(extractor: TeamMember => String): Iterable[String] =
          team1Result.right.value.map(extractor)

        getMembersDetails(_.displayName.value)  shouldBe Seq("Joe Black", "James Roger")
        getMembersDetails(_.username.value)     shouldBe Seq("joe.black", "james.roger")
        getMembersDetails(_.primaryEmail.value) shouldBe Seq(
          "joe.black@digital.hmrc.gov.uk",
          "james.roger@hmrc.gsi.gov.uk")
      }
    }

    "getAllUsersFromUMP" should {
      "should get the users" in {
        stubFor(
          get(urlEqualTo("/v2/organisations/users"))
            .willReturn(aResponse().withStatus(200).withBody(readFile("/all-users.json")))
        )

        val allUsers = userManagementConnector.getAllUsersFromUMP.futureValue

        allUsers.right.value.size shouldBe 3

        def getMembersDetails(extractor: TeamMember => String): Iterable[String] =
          allUsers.right.value.map(extractor)

        getMembersDetails(_.displayName.value)  shouldBe Seq("Ricky Micky", "Aleks Malkes", "Anand Manand")
        getMembersDetails(_.username.value)     shouldBe Seq("ricky.micky", "aleks.malkes", "anand.manand")
        getMembersDetails(_.primaryEmail.value) shouldBe Seq(
          "ricky.micky@gov.uk",
          "aleks.malkes@gov.uk",
          "anand.manand@gov.uk")
      }
    }
  }

  "getDisplayName" should {
    val userId = UmpUserId("ricky.micky")

    "should return user's displayName if exists in UMP" in {
      stubFor(
        get(urlEqualTo(s"/v2/organisations/users/$userId"))
          .willReturn(aResponse().withStatus(200).withBody(readFile("/single-user.json")))
      )

      val displayName = userManagementConnector.getDisplayName(userId).futureValue

      displayName shouldBe Some(DisplayName("Ricky Micky"))
    }

    "should return None if UMP doesn't know about a given user" in {
      stubFor(
        get(urlEqualTo(s"/v2/organisations/users/$userId"))
          .willReturn(aResponse().withStatus(404))
      )

      val displayName = userManagementConnector.getDisplayName(userId).futureValue

      displayName shouldBe None
    }

    "should throw BadGatewayException if UMP returns sth different than 200 or 404" in {
      val unexpectedStatusCode = 500
      val relativeUrl          = s"/v2/organisations/users/$userId"
      stubFor(
        get(urlEqualTo(relativeUrl))
          .willReturn(aResponse().withStatus(unexpectedStatusCode))
      )

      val e = userManagementConnector.getDisplayName(userId).failed.futureValue
      e shouldBe an[BadGatewayException]
      e.getMessage shouldBe s"Received status: $unexpectedStatusCode from GET to $wireMockUrl$relativeUrl"
    }
  }

  private def readFile(fileName: String): String =
    Source.fromURL(getClass.getResource(fileName)).getLines().mkString("\n")

  def callExternalMockedService(
    teamName       : TeamName,
    jsonFileNameOpt: Option[String],
    httpCode       : Int = 200
  ): Future[Either[UMPError, Seq[TeamMember]]] = {

    val optJson: Option[String] =
      jsonFileNameOpt.map(readFile)

    stubFor(
      get(urlEqualTo(s"/v2/organisations/teams/${teamName.asString}/members"))
        .willReturn(
          optJson.foldLeft(aResponse().withStatus(httpCode))(_ withBody _)
        )
    )

    userManagementConnector
      .getTeamMembersFromUMP(teamName)
  }
}
