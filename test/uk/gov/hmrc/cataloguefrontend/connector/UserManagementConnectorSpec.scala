/*
 * Copyright 2019 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.http.RequestMethod
import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.mockito.Matchers.{any, anyString}
import org.mockito.Mockito.when
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeHeaders
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.UmpUserId
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.{ConnectionError, DisplayName, HTTPError, NoData, TeamMember, UMPError}
import uk.gov.hmrc.cataloguefrontend.{FutureHelpers, UserManagementPortalConfig, WireMockEndpoints}
import uk.gov.hmrc.http.{BadGatewayException, HeaderCarrier}
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.Future
import scala.io.Source

class UserManagementConnectorSpec
    extends FunSpec
    with Matchers
    with TypeCheckedTripleEquals
    with BeforeAndAfter
    with GuiceOneServerPerSuite
    with WireMockEndpoints
    with ScalaFutures
    with EitherValues
    with MockitoSugar
    with OptionValues {

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.user-management.url"        -> endpointMockUrl,
        "microservice.services.user-management.myTeamsUrl" -> "http://some.ump.com/myTeams",
        "play.http.requestHandler"                         -> "play.api.http.DefaultHttpRequestHandler",
        "metrics.jvm"                                      -> false
      )
      .build()

  private lazy val userManagementConnector: UserManagementConnector = app.injector.instanceOf[UserManagementConnector]

  describe("User management connector") {
    it("should get the team members from the user-management service") {
      val teamMembers: Seq[TeamMember] =
        callExternalMockedService("team-chicken", Some("/user-management-response.json")).right.value

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

    it("has an empty members array in json") {

      val error: UMPError =
        callExternalMockedService("team-chicken", Some("/user-management-empty-members.json")).left.value
      error should ===(NoData("http://some.ump.com/myTeams/team-chicken?edit"))
    }

    it("no members field in json") {
      val error: UMPError =
        callExternalMockedService("team-chicken", Some("/user-management-no-members.json")).left.value

      error should ===(NoData("http://some.ump.com/myTeams/team-chicken?edit"))
    }

    it("api returns an error code") {
      val error: UMPError = callExternalMockedService("team-chicken", None, 404).left.value

      error should ===(HTTPError(404))
    }

    it("api returns a connection error") {

      val mockedHttpGet = mock[HttpClient]

      val userManagementConnector = new UserManagementConnector(
        mockedHttpGet,
        mock[UserManagementPortalConfig],
        app.injector.instanceOf[FutureHelpers]
      )

      val expectedException = new RuntimeException("some error")

      when(mockedHttpGet.GET(anyString())(any(), any(), any())).thenReturn(Future.failed(expectedException))

      val error: UMPError = userManagementConnector
        .getTeamMembersFromUMP("teamName")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
        .futureValue
        .left
        .value
      error should ===(ConnectionError(expectedException))
    }

    it("should get the team details from the user-management service") {
      stubUserManagementEndPoint(
        url             = "/v2/organisations/teams/TEAM-A",
        jsonFileNameOpt = Some("/user-management-team-details-response.json")
      )

      val teamDetails = userManagementConnector
        .getTeamDetails("TEAM-A")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
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

    it("no organization/data field in json for team details") {
      stubUserManagementEndPoint(
        url             = "/v2/organisations/teams/TEAM-A",
        jsonFileNameOpt = Some("/user-management-team-details-nodata-response.json")
      )

      val teamDetails = userManagementConnector
        .getTeamDetails("TEAM-A")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
        .futureValue
        .left
        .value

      teamDetails should ===(NoData("http://some.ump.com/myTeams/TEAM-A?edit"))
    }

    it("api returns an error code for team details") {
      stubUserManagementEndPoint(
        url             = "/v2/organisations/teams/TEAM-A",
        jsonFileNameOpt = None,
        httpCode        = 404
      )
      val teamDetails = userManagementConnector
        .getTeamDetails("TEAM-A")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
        .futureValue
        .left
        .value

      teamDetails should ===(HTTPError(404))
    }

    it("api returns a connection error for team details") {

      val mockedHttpGet = mock[HttpClient]

      val userManagementConnector = new UserManagementConnector(
        mockedHttpGet,
        mock[UserManagementPortalConfig],
        app.injector.instanceOf[FutureHelpers]
      )

      val expectedException = new RuntimeException("some error")

      when(mockedHttpGet.GET(anyString())(any(), any(), any())).thenReturn(Future.failed(expectedException))

      val error: UMPError = userManagementConnector
        .getTeamDetails("TEAM-A")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
        .futureValue
        .left
        .value
      error should ===(ConnectionError(expectedException))
    }

    describe("getTeamMembersForTeams") {

      it("should get the team members for multiple teams") {
        val teamNames = Seq("Team1", "Team2")

        stubUserManagementEndPoint(
          url             = "/v2/organisations/teams/Team1/members",
          jsonFileNameOpt = Some("/user-management-response-team1.json")
        )

        stubUserManagementEndPoint(
          url             = "/v2/organisations/teams/Team2/members",
          jsonFileNameOpt = Some("/user-management-response-team2.json")
        )
        val teamsAndMembers = userManagementConnector
          .getTeamMembersForTeams(teamNames)(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
          .futureValue

        teamsAndMembers.keys should contain theSameElementsAs teamNames

        def getMembersDetails(extractor: TeamMember => String): Iterable[String] =
          teamsAndMembers.values.flatMap(_.right.value).map(extractor)

        getMembersDetails(_.displayName.value) shouldBe Seq("Joe Black", "James Roger", "Casey Binge", "Marc Palazzo")
        getMembersDetails(_.username.value)    shouldBe Seq("joe.black", "james.roger", "casey.binge", "marc.palazzo")
        getMembersDetails(_.primaryEmail.value) shouldBe Seq(
          "joe.black@digital.hmrc.gov.uk",
          "james.roger@hmrc.gsi.gov.uk",
          "casey.binge@digital.hmrc.gov.uk",
          "marc.palazzo@hmrc.gsi.gov.uk")
      }

      it("should return an Http error for calls with non-200 status codes") {
        val teamNames = Seq("Team1", "Team2")

        stubUserManagementEndPoint(
          url             = "/v2/organisations/teams/Team1/members",
          jsonFileNameOpt = Some("/user-management-response-team1.json")
        )

        stubUserManagementEndPoint(
          url             = "/v2/organisations/teams/Team2/members",
          jsonFileNameOpt = None,
          httpCode        = 404
        )

        val teamsAndMembers: Map[String, Either[UMPError, Seq[TeamMember]]] = userManagementConnector
          .getTeamMembersForTeams(teamNames)(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
          .futureValue

        teamsAndMembers.keys should contain theSameElementsAs teamNames

        val team1Result = teamsAndMembers.get("Team1").value
        val team2Result = teamsAndMembers.get("Team2").value

        team2Result should ===(Left(HTTPError(404)))

        def getMembersDetails(extractor: TeamMember => String): Iterable[String] =
          team1Result.right.value.map(extractor)

        getMembersDetails(_.displayName.value) shouldBe Seq("Joe Black", "James Roger")
        getMembersDetails(_.username.value)    shouldBe Seq("joe.black", "james.roger")
        getMembersDetails(_.primaryEmail.value) shouldBe Seq(
          "joe.black@digital.hmrc.gov.uk",
          "james.roger@hmrc.gsi.gov.uk")
      }

      it("should return a connection error for calls which fail with an exception") {

        val mockedHttpGet = mock[HttpClient]

        val userManagementConnector = new UserManagementConnector(
          mockedHttpGet,
          mock[UserManagementPortalConfig],
          app.injector.instanceOf[FutureHelpers]
        )

        val teamNames = Seq("Team1", "Team2")

        val exception = new RuntimeException("Boooom!")
        when(mockedHttpGet.GET(anyString())(any(), any(), any())).thenReturn(Future.failed(exception))

        val teamsAndMembers: Map[String, Either[UMPError, Seq[TeamMember]]] = userManagementConnector
          .getTeamMembersForTeams(teamNames)(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
          .futureValue

        teamsAndMembers.keys should contain theSameElementsAs teamNames

        val team1Result = teamsAndMembers.get("Team1").value
        val team2Result = teamsAndMembers.get("Team2").value

        team1Result should ===(Left(ConnectionError(exception)))
        team2Result should ===(Left(ConnectionError(exception)))
      }

      it("should return a no data error if the json from UMP doesn't conform to the expected shape") {
        val teamNames = Seq("Team1", "Team2")

        stubUserManagementEndPoint(
          url             = "/v2/organisations/teams/Team1/members",
          jsonFileNameOpt = Some("/user-management-response-team1.json")
        )

        stubUserManagementEndPoint(
          url             = "/v2/organisations/teams/Team2/members",
          jsonFileNameOpt = Some("/user-management-team-details-nodata-response.json")
        )

        val teamsAndMembers: Map[String, Either[UMPError, Seq[TeamMember]]] = userManagementConnector
          .getTeamMembersForTeams(teamNames)(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
          .futureValue

        teamsAndMembers.keys should contain theSameElementsAs teamNames

        val team1Result = teamsAndMembers.get("Team1").value
        val team2Result = teamsAndMembers.get("Team2").value

        team2Result should ===(Left(NoData("http://some.ump.com/myTeams/Team2?edit")))

        def getMembersDetails(extractor: TeamMember => String): Iterable[String] =
          team1Result.right.value.map(extractor)

        getMembersDetails(_.displayName.value) shouldBe Seq("Joe Black", "James Roger")
        getMembersDetails(_.username.value)    shouldBe Seq("joe.black", "james.roger")
        getMembersDetails(_.primaryEmail.value) shouldBe Seq(
          "joe.black@digital.hmrc.gov.uk",
          "james.roger@hmrc.gsi.gov.uk")
      }

    }

    describe("getAllUsersFromUMP") {

      it("should get the users ") {
        stubUserManagementEndPoint(
          url             = "/v2/organisations/users",
          jsonFileNameOpt = Some("/all-users.json")
        )

        val allUsers = userManagementConnector.getAllUsersFromUMP.futureValue

        allUsers.right.value.size shouldBe 3

        def getMembersDetails(extractor: TeamMember => String): Iterable[String] =
          allUsers.right.value.map(extractor)

        getMembersDetails(_.displayName.value) shouldBe Seq("Ricky Micky", "Aleks Malkes", "Anand Manand")
        getMembersDetails(_.username.value)    shouldBe Seq("ricky.micky", "aleks.malkes", "anand.manand")
        getMembersDetails(_.primaryEmail.value) shouldBe Seq(
          "ricky.micky@gov.uk",
          "aleks.malkes@gov.uk",
          "anand.manand@gov.uk")

      }
    }
  }

  describe("getDisplayName") {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val userId                     = UmpUserId("ricky.micky")

    it("should return user's displayName if exists in UMP") {
      stubUserManagementEndPoint(
        url             = s"/v2/organisations/users/$userId",
        jsonFileNameOpt = Some("/single-user.json")
      )

      val displayName = userManagementConnector.getDisplayName(userId).futureValue

      displayName shouldBe Some(DisplayName("Ricky Micky"))
    }

    it("should return None if UMP doesn't know about a given user") {
      stubUserManagementEndPoint(
        httpCode        = 404,
        url             = s"/v2/organisations/users/$userId",
        jsonFileNameOpt = None
      )

      val displayName = userManagementConnector.getDisplayName(userId).futureValue

      displayName shouldBe None
    }

    it("should throw BadGatewayException if UMP returns sth different than 200 or 404") {
      val unexpectedStatusCode = 500
      val relativeUrl          = s"/v2/organisations/users/$userId"
      stubUserManagementEndPoint(
        httpCode        = unexpectedStatusCode,
        url             = relativeUrl,
        jsonFileNameOpt = None
      )

      intercept[BadGatewayException] {
        await(userManagementConnector.getDisplayName(userId))
      }.message shouldBe s"Received status: $unexpectedStatusCode from GET to $endpointMockUrl$relativeUrl"

    }
  }

  def callExternalMockedService(
    teamName: String,
    jsonFileNameOpt: Option[String],
    httpCode: Int = 200): Either[UMPError, Seq[TeamMember]] = {

    stubUserManagementEndPoint(GET, httpCode, s"/v2/organisations/teams/$teamName/members", jsonFileNameOpt)

    val errorOrResponse: Either[UMPError, Seq[TeamMember]] = userManagementConnector
      .getTeamMembersFromUMP(teamName)(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
      .futureValue
    errorOrResponse

  }

  def stubUserManagementEndPoint(
    method: RequestMethod = GET,
    httpCode: Int         = 200,
    url: String,
    jsonFileNameOpt: Option[String]
  ): Unit = {

    val json: Option[String] =
      jsonFileNameOpt.map(x => Source.fromURL(getClass.getResource(x)).getLines().mkString("\n"))

    serviceEndpoint(
      method          = method,
      url             = url,
      requestHeaders  = Map("Token" -> "None", "requester" -> "None"),
      willRespondWith = (httpCode, json))
  }
}
