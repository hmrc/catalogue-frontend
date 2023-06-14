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

package uk.gov.hmrc.cataloguefrontend.connector

import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.UMPError.HTTPError
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.{SlackInfo, TeamDetails, TeamMember, UMPError}
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport

import scala.concurrent.Future

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

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.user-management.url"  -> wireMockUrl,
        "microservice.services.user-management.myTeamsUrl" -> "http://some.ump.com/myTeams",
        "play.http.requestHandler"                         -> "play.api.http.DefaultHttpRequestHandler",
        "metrics.jvm"                                      -> false,
        "ump.auth.enabled"                                 -> false
      )
      .build()

  private lazy val userManagementConnector: UserManagementConnector =
    app.injector.instanceOf[UserManagementConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "User management connector" should {
    "should get the team members from the user-management service" in {
      val teamMembers: Seq[TeamMember] =
        callExternalMockedService(TeamName("team-chicken"), Some("user-management-response.json")).futureValue.right.value

      teamMembers should have length 2

      teamMembers.headOption.value shouldBe TeamMember(
        displayName     = Some("Jim Willman"),
        familyName      = Some("Willman"),
        givenName       = Some("Jim"),
        primaryEmail    = Some("jim.willman@digital.hmrc.gov.uk"),
        username        = Some("jim.willman"),
        role            = Some("user")
      )

      teamMembers(1) shouldBe TeamMember(
        displayName     = Some("Karl GoJarvis"),
        familyName      = Some("GoJarvis"),
        givenName       = Some("Karl"),
        primaryEmail    = Some("karl.gojarvis@hmrc.gsi.gov.uk"),
        username        = Some("karl.gojarvis"),
        role            = Some("user")
      )
    }

    "have an empty members array in json" in {
      val teamName = TeamName("team-chicken")

      stubFor(
        get(urlEqualTo(s"/v2/organisations/teams/${teamName.asString}/members"))
          .willReturn(aResponse().withBodyFile("user-management-empty-members.json"))
      )

      val res = userManagementConnector.getTeamMembersFromUMP(teamName).futureValue
      res shouldBe Right(Seq.empty)
    }

    "have no members field in json" in {
      val res =
        callExternalMockedService(TeamName("team-chicken"), Some("user-management-no-members.json")).futureValue
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

    "should get the team details from the user-management service" in {
      stubFor(
          get(urlEqualTo("/v2/organisations/teams/TEAM-A"))
            .willReturn(aResponse().withBodyFile("user-management-team-details-response.json"))
        )

      val teamDetails = userManagementConnector
        .getTeamDetails(TeamName("TEAM-A"))
        .futureValue
        .right
        .value

      teamDetails.description.value       shouldBe "TEAM-A is a great team"
      teamDetails.location.value          shouldBe "STLPD"
      teamDetails.organisation.value      shouldBe "ORGA"
      teamDetails.slack.value             shouldBe SlackInfo("https://slack.host/messages/team-A")
      teamDetails.slackNotification.value shouldBe SlackInfo("https://slack.host/messages/team-A-NOTIFICATION")
      teamDetails.documentation.value     shouldBe "https://some.documentation.url"
    }

    "determine if SlackInfo hasValidURL is correct" in {
      SlackInfo("https://slack.host/messages/team-A").hasValidUrl shouldBe true
      SlackInfo("team-A").hasValidUrl shouldBe false
    }

    "determine if SlackInfo hasValidName is correct" in {
      SlackInfo("https://slack.host/messages/team-A").hasValidName shouldBe true
      SlackInfo("https://slack.host/messages/AAAAA").hasValidName shouldBe false
      SlackInfo("https://slack.host/messages/11111").hasValidName shouldBe false
      SlackInfo("https://slack.host/messages/ABC12345").hasValidName shouldBe false
    }

    "no organization/data field in json for team details" in {
      stubFor(
          get(urlEqualTo("/v2/organisations/teams/TEAM-A"))
            .willReturn(aResponse().withBodyFile("user-management-team-details-nodata-response.json"))
        )

      val res = userManagementConnector
        .getTeamDetails(TeamName("TEAM-A")).futureValue

      res.left.get.isInstanceOf[UMPError.ConnectionError] shouldBe true
    }

    "api returns an error code for team details" in {
      stubFor(
          get(urlEqualTo("/v2/organisations/teams/TEAM-A"))
            .willReturn(aResponse().withStatus(500))
        )

      val teamDetails = userManagementConnector
        .getTeamDetails(TeamName("TEAM-A"))
        .futureValue
        .left
        .value

      teamDetails shouldBe UMPError.HTTPError(500)
    }

    "getAllUsersFromUMP" should {
      "should get the users" in {
        stubFor(
          get(urlEqualTo("/v2/organisations/users"))
            .willReturn(aResponse().withBodyFile("all-users.json"))
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

    "getTeamsForUser" when {
      "ump responds with a list of teams for a user" should {
        "return a list of TeamDetails" in {
          stubFor(
            get(urlEqualTo("/v2/organisations/users/joe.bloggs/teams"))
              .willReturn(aResponse().withStatus(200).withBodyFile("all-teams-for-user.json"))
          )

          val teamsForUser = userManagementConnector.getTeamsForUser(ldapUsername = "joe.bloggs").futureValue
          val result = teamsForUser.right.value
          
          result.size shouldBe 3
          result should contain theSameElementsAs Seq(
            TeamDetails(description = Some("Does stuff"), location = None, organisation = None, documentation = Some("docs.com"), slack = Some(SlackInfo("slack.com")), slackNotification = Some(SlackInfo("slack.com/channel")), team = "Team1"),
            TeamDetails(description = Some("Does other stuff"), location = None, organisation = None, documentation = Some("docs.com"), slack = Some(SlackInfo("slack.com")), slackNotification = Some(SlackInfo("slack.com/channel")), team = "Team2"),
            TeamDetails(description = Some("Does additional stuff"), location = None, organisation = None, documentation = Some("docs.com"), slack = Some(SlackInfo("slack.com")), slackNotification = Some(SlackInfo("slack.com/channel")), team = "Team3")
          )
        }
      }

      "ump responds with a 404" should {
        "return an empty list of TeamDetails" in {
          stubFor(
            get(urlEqualTo("/v2/organisations/users/joe.bloggs/teams"))
              .willReturn(aResponse().withStatus(404))
          )

          val teamsForUser = userManagementConnector.getTeamsForUser(ldapUsername = "joe.bloggs").futureValue
          val result = teamsForUser.right.value

          result.size shouldBe 0
          result shouldBe List.empty[TeamDetails]
        }
      }

      "ump responds with any other status code" should {
        "return the expected status code exception" in {
          stubFor(
            get(urlEqualTo("/v2/organisations/users/joe.bloggs/teams"))
              .willReturn(aResponse().withStatus(500))
          )

          val result = userManagementConnector.getTeamsForUser(ldapUsername = "joe.bloggs").futureValue
          result shouldBe Left(HTTPError(500))
        }
      }
    }


  }

  def callExternalMockedService(
    teamName       : TeamName,
    optJsonFileName: Option[String],
    httpCode       : Int = 200
  ): Future[Either[UMPError, Seq[TeamMember]]] = {

    stubFor(
      get(urlEqualTo(s"/v2/organisations/teams/${teamName.asString}/members"))
        .willReturn(
          optJsonFileName.foldLeft(aResponse().withStatus(httpCode))(_ withBodyFile _)
        )
    )

    userManagementConnector
      .getTeamMembersFromUMP(teamName)
  }
}
