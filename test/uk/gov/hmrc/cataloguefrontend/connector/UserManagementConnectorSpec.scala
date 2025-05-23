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

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.model.{EditTeamDetails, TeamName, UserName}
import uk.gov.hmrc.cataloguefrontend.teams.CreateTeamRequest
import uk.gov.hmrc.cataloguefrontend.users.*
import uk.gov.hmrc.cataloguefrontend.users.UserRole.*
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class UserManagementConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with WireMockSupport
     with HttpClientV2Support:

  given HeaderCarrier = HeaderCarrier()

  private val connector: UserManagementConnector =
    UserManagementConnector(
      httpClientV2,
      ServicesConfig(Configuration(
        "microservice.services.user-management.port" -> wireMockPort,
        "microservice.services.user-management.host" -> wireMockHost
      ))
    )

  "getTeam" should {
    "return team when found" in {
      val team = TeamName("test-team")

      stubFor(
        get(urlPathEqualTo(s"/user-management/teams/${team.asString}"))
          .willReturn(
            aResponse()
              .withBody(s"""{
                "members": [],
                "teamName": "${team.asString}"
              }""")
          )
      )

      connector.getTeam(team).futureValue shouldBe Some(UmpTeam(Seq.empty[Member], team, None, None, None, None))
    }
  }

  "getAllUsers" should {
    "return a list of users" in {
      stubFor(
        get(urlPathEqualTo("/user-management/users"))
          .willReturn(
            aResponse()
              .withBody("""[
                {
                  "displayName" : "Joe Bloggs",
                  "familyName" : "Bloggs",
                  "givenName" : "Joe",
                  "organisation" : "MDTP",
                  "primaryEmail" : "joe.bloggs@digital.hmrc.gov.uk",
                  "username" : "joe.bloggs",
                  "githubUsername" : "joebloggs-github",
                  "phoneNumber" : "07123456789",
                  "role" : "user",
                  "teamNames" : [
                       "TestTeam"
                  ],
                  "isDeleted": false,
                  "isNonHuman": false
                }
              ]""")
          )
      )

      connector.getAllUsers().futureValue should contain theSameElementsAs
        Seq(
          User(
            displayName    = Some("Joe Bloggs"),
            familyName     = "Bloggs",
            givenName      = Some("Joe"),
            organisation   = Some("MDTP"),
            primaryEmail   = "joe.bloggs@digital.hmrc.gov.uk",
            username       = UserName("joe.bloggs"),
            githubUsername = Some("joebloggs-github"),
            phoneNumber    = Some("07123456789"),
            role           = Role("user"),
            teamNames      = Seq(TeamName("TestTeam")),
            isDeleted      = false,
            isNonHuman     = false
          )
        )
    }

    "return a list of users in a team" in {
      stubFor(
        get(urlEqualTo("/user-management/users?team=TestTeam"))
          .willReturn(
            aResponse()
              .withBody("""[
                {
                   "displayName" : "Joe Bloggs",
                   "familyName" : "Bloggs",
                   "givenName" : "Joe",
                   "organisation" : "MDTP",
                   "primaryEmail" : "joe.bloggs@digital.hmrc.gov.uk",
                   "username" : "joe.bloggs",
                   "github" : "https://github.com/joebloggs",
                   "phoneNumber" : "07123456789",
                   "role" : "user",
                   "teamNames" : [
                        "TestTeam"
                   ],
                   "isDeleted": false,
                   "isNonHuman": false
                },
                {
                   "displayName" : "Jane Doe",
                   "familyName" : "Doe",
                   "givenName" : "Jane",
                   "organisation" : "MDTP",
                   "primaryEmail" : "jane.doe@digital.hmrc.gov.uk",
                   "username" : "jane.doe",
                   "github" : "https://github.com/janedoe",
                   "phoneNumber" : "07123456789",
                   "role" : "user",
                   "teamNames" : [
                        "TestTeam"
                   ],
                   "isDeleted": false,
                   "isNonHuman": false
                }
              ]""".stripMargin)
          )
      )

      connector.getAllUsers(team = Some(TeamName("TestTeam"))).futureValue should contain theSameElementsAs
        Seq(
          User(
            displayName    = Some("Joe Bloggs"),
            familyName     = "Bloggs",
            givenName      = Some("Joe"),
            organisation   = Some("MDTP"),
            primaryEmail   = "joe.bloggs@digital.hmrc.gov.uk",
            username       = UserName("joe.bloggs"),
            githubUsername = None,
            phoneNumber    = Some("07123456789"),
            role           = Role("user"),
            teamNames      = Seq(TeamName("TestTeam")),
            isDeleted      = false,
            isNonHuman     = false
          ),
          User(
            displayName    = Some("Jane Doe"),
            familyName     = "Doe",
            givenName      = Some("Jane"),
            organisation   = Some("MDTP"),
            primaryEmail   = "jane.doe@digital.hmrc.gov.uk",
            username       = UserName("jane.doe"),
            githubUsername = None,
            phoneNumber    = Some("07123456789"),
            role           = Role("user"),
            teamNames      = Seq(TeamName("TestTeam")),
            isDeleted      = false,
            isNonHuman     = false
          )
        )
    }

    "return empty list when encountered an error" in {
      stubFor(
        get(urlPathEqualTo("/user-management/users"))
          .willReturn(
            aResponse()
              .withStatus(500)
          )
      )

      connector.getAllUsers().futureValue.isEmpty shouldBe true
    }
  }

  "getUser" should {
    "return user when found" in {
      val username = UserName("joe.bloggs")

      stubFor(
        get(urlPathEqualTo(s"/user-management/users/${username.asString}"))
          .willReturn(
            aResponse()
              .withBody("""{
                "displayName" : "Joe Bloggs",
                "familyName" : "Bloggs",
                "givenName" : "Joe",
                "organisation" : "MDTP",
                "primaryEmail" : "joe.bloggs@digital.hmrc.gov.uk",
                "username" : "joe.bloggs",
                "githubUsername" : "joebloggs-github",
                "phoneNumber" : "07123456789",
                "role" : "user",
                "teamNames" : [
                    "TestTeam"
                ],
                "isDeleted": false,
                "isNonHuman": false
              }""".stripMargin)
          )
      )

      connector.getUser(username).futureValue shouldBe
        Some(
          User(
            displayName    = Some("Joe Bloggs"),
            familyName     = "Bloggs",
            givenName      = Some("Joe"),
            organisation   = Some("MDTP"),
            primaryEmail   = "joe.bloggs@digital.hmrc.gov.uk",
            username       = UserName("joe.bloggs"),
            githubUsername = Some("joebloggs-github"),
            phoneNumber    = Some("07123456789"),
            role           = Role("user"),
            teamNames      = Seq(TeamName("TestTeam")),
            isDeleted      = false,
            isNonHuman     = false
          )
        )
    }

    "return None when not found" in {
      val username = UserName("non.existent")

      stubFor(
        get(urlPathEqualTo(s"/user-management/users/${username.asString}"))
          .willReturn(
            aResponse()
              .withStatus(404)
          )
      )

      connector.getUser(username).futureValue shouldBe None
    }
  }

  "getUserAccess" should {
    "return userAccess when found" in {
      val username = UserName("joe.bloggs")

      stubFor(
        get(urlPathEqualTo(s"/user-management/users/${username.asString}/access"))
          .willReturn(
            aResponse()
              .withBody(
                """{
                  "vpn": true,
                  "jira": false,
                  "confluence": false,
                  "googleApps": true,
                  "devTools": true
                }""".stripMargin)
          )
      )

      connector.getUserAccess(username).futureValue shouldBe
        UserAccess(
          vpn              = true,
          jira             = false,
          confluence       = false,
          googleApps       = true,
          devTools         = true
        )
    }

    "return empty UserAccess when not found" in {
      val username = UserName("non.existent")

      stubFor(
        get(urlPathEqualTo(s"/user-management/users/${username.asString}/access"))
          .willReturn(
            aResponse()
              .withStatus(404)
          )
      )

      connector.getUserAccess(username).futureValue shouldBe UserAccess.empty
    }
  }

  "editUserAccess" should:

    val editUserAccessRequest =
      EditUserAccessRequest(
        username = "joe.bloggs",
        organisation = "MDTP",
        vpn = false,
        jira = false,
        confluence = true,
        googleApps = true,
        environments = true,
        bitwarden = true
      )

    val actualEditUserAccessRequest =
      """{
        |  "username": "joe.bloggs",
        |  "organisation": "MDTP",
        |  "access": {
        |    "vpn": false,
        |    "jira": false,
        |    "confluence": true,
        |    "googleApps": true,
        |    "environments": true,
        |    "bitwarden": true
        |  },
        |  "isExistingLDAPUser": true
        |}
        |""".stripMargin

    "return Unit when UMP response is 200 for human user" in:
      stubFor(
        post(urlPathEqualTo(s"/user-management/edit-user-access"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.editUserAccess(editUserAccessRequest).futureValue shouldBe()

      verify(
        postRequestedFor(urlPathEqualTo("/user-management/edit-user-access"))
          .withRequestBody(equalToJson(actualEditUserAccessRequest))
      )

  "editUserDetails" should :

    val editUserDetailsRequest =
      EditUserDetailsRequest(
        username = "joe.bloggs",
        attribute = UserAttribute.DisplayName,
        value = "Joe Bloggs"
      )

    val actualEditUserDetailsRequest =
      """{
        |  "username" : "joe.bloggs",
        |  "attribute" : "displayName",
        |  "value" : "Joe Bloggs"
        |}
        |""".stripMargin

    "return Unit when UMP response is 200" in :
      stubFor(
        put(urlPathEqualTo(s"/user-management/edit-user-details"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.editUserDetails(editUserDetailsRequest).futureValue shouldBe()

      verify(
        putRequestedFor(urlPathEqualTo("/user-management/edit-user-details"))
          .withRequestBody(equalToJson(actualEditUserDetailsRequest))
      )

  "getUserRoles" should {
    "return a list of roles for user" in {
      stubFor(
        get(urlPathEqualTo("/user-management/users/joe.bloggs/roles"))
          .willReturn(
            aResponse()
              .withBody(
                """{
                  "roles": [
                    "team_admin",
                    "location_authoriser",
                    "experimental_features"
                  ]
                }""")
          )
      )

      connector.getUserRoles(UserName("joe.bloggs")).futureValue should be
        UserRoles(Seq(TeamAdmin, LocationAuthoriser, ExperimentalFeatures))
    }

    "return empty list when encountered an error" in {
      stubFor(
        get(urlPathEqualTo("/user-management/users/joe.bloggs/roles"))
          .willReturn(
            aResponse()
              .withStatus(500)
          )
      )

      connector.getUserRoles(UserName("joe.bloggs")).futureValue.roles.isEmpty shouldBe true
    }
  }

  "editUserRoles" should {
    "return Unit when UMP response is 200 for a given user" in {
      stubFor(
        post(urlPathEqualTo("/user-management/users/joe.bloggs/roles"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      val userRolesJson = """{"roles": ["team_admin"]}"""

      connector.editUserRoles(UserName("joe.bloggs"), UserRoles(Seq(TeamAdmin))).futureValue shouldBe()

      verify(
        postRequestedFor(urlPathEqualTo("/user-management/users/joe.bloggs/roles"))
          .withRequestBody(equalToJson(userRolesJson))
      )
    }
  }

  "createUser" should:

    val createUserRequest =
      CreateUserRequest(
        givenName        = "joe",
        familyName       = "bloggs",
        organisation     = "MDTP",
        contactEmail     = "email@test.gov.uk",
        contactComments  = "test",
        team             = TeamName("Test"),
        isReturningUser  = false,
        isTransitoryUser = false,
        isServiceAccount = false,
        vpn              = true,
        jira             = true,
        confluence       = true,
        googleApps       = true,
        environments     = true,
        bitwarden        = true
      )

    val actualUserRequest =
      """{
        |  "givenName": "joe",
        |  "familyName": "bloggs",
        |  "organisation": "MDTP",
        |  "contactEmail": "email@test.gov.uk",
        |  "contactComments": "test",
        |  "team": "Test",
        |  "isReturningUser": false,
        |  "isTransitoryUser": false,
        |  "isServiceAccount": false,
        |  "access": {
        |    "vpn": true,
        |    "jira": true,
        |    "confluence": true,
        |    "googleApps": true,
        |    "environments": true,
        |    "bitwarden": true,
        |    "ldap": true
        |  },
        |  "userDisplayName": "Joe Bloggs",
        |  "isExistingLDAPUser": false
        |}
        |""".stripMargin

    val actualNonHumanUserRequest =
      """{
        |  "givenName": "service_joe",
        |  "familyName": "bloggs",
        |  "organisation": "MDTP",
        |  "contactEmail": "email@test.gov.uk",
        |  "contactComments": "test",
        |  "team": "Test",
        |  "isReturningUser": false,
        |  "isTransitoryUser": false,
        |  "isServiceAccount": true,
        |  "access": {
        |    "vpn": true,
        |    "jira": true,
        |    "confluence": true,
        |    "googleApps": true,
        |    "environments": true,
        |    "bitwarden": true,
        |    "ldap": true
        |  },
        |  "userDisplayName": "service_joe bloggs",
        |  "isExistingLDAPUser": false
        |}
        |""".stripMargin

    "return Unit when UMP response is 200 for human user" in:
      stubFor(
        post(urlPathEqualTo(s"/user-management/create-user"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.createUser(createUserRequest).futureValue shouldBe ()

      verify(
        postRequestedFor(urlPathEqualTo("/user-management/create-user"))
          .withRequestBody(equalToJson(actualUserRequest))
      )

    "return JSON when UMP response is 200 for non human user" in:
      stubFor(
        post(urlPathEqualTo("/user-management/create-user"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.createUser(createUserRequest.copy(isServiceAccount = true)).futureValue shouldBe ()

      verify(
        postRequestedFor(urlPathEqualTo("/user-management/create-user"))
          .withRequestBody(equalToJson(actualNonHumanUserRequest))
      )

    "throw a RuntimeException when UMP response is an UpStreamErrorResponse" in:
      stubFor(
        post(urlPathEqualTo("/user-management/create-service-user"))
          .willReturn(
            aResponse()
              .withStatus(500)
          )
      )

      an[RuntimeException] shouldBe thrownBy {
        connector.createUser(createUserRequest.copy(isServiceAccount = true)).futureValue
      }

  "createTeam" should :

    val createTeamRequest =
      CreateTeamRequest(
        organisation = "MDTP",
        team         = "Test"
      )

    val actualTeamRequest =
      """{
        |  "organisation": "MDTP",
        |  "team": "Test"
        |}
        |""".stripMargin

    "return Unit when UMP response is 200" in :
      stubFor(
        post(urlPathEqualTo(s"/user-management/create-team"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.createTeam(createTeamRequest).futureValue shouldBe()

      verify(
        postRequestedFor(urlPathEqualTo("/user-management/create-team"))
          .withRequestBody(equalToJson(actualTeamRequest))
      )

    "throw a RuntimeException when UMP response is an UpStreamErrorResponse" in :
      stubFor(
        post(urlPathEqualTo("/user-management/create-team"))
          .willReturn(
            aResponse()
              .withStatus(500)
          )
      )

      an[RuntimeException] shouldBe thrownBy {
        connector.createTeam(createTeamRequest).futureValue
      }

  "deleteTeam" should :

    "return Unit when UMP response is 200" in :
      stubFor(
        delete(urlPathEqualTo(s"/user-management/delete-team/TestTeam"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.deleteTeam(TeamName("TestTeam")).futureValue shouldBe()

      verify(
        deleteRequestedFor(urlPathEqualTo("/user-management/delete-team/TestTeam"))
      )

    "throw a RuntimeException when UMP response is an UpStreamErrorResponse" in :
      stubFor(
        post(urlPathEqualTo("/user-management/delete-team/TestTeam"))
          .willReturn(
            aResponse()
              .withStatus(500)
          )
      )

      an[RuntimeException] shouldBe thrownBy {
        connector.deleteTeam(TeamName("TestTeam")).futureValue
      }

  "manageVpnAccess" should :
    "return Unit when UMP response is 200" in :
      stubFor(
        post(urlPathEqualTo("/user-management/users/joe.bloggs/vpn/true"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.manageVpnAccess(UserName("joe.bloggs"), enableVpn = true).futureValue shouldBe ()

      verify(
        postRequestedFor(urlPathEqualTo("/user-management/users/joe.bloggs/vpn/true"))
      )

    "throw a RuntimeException when UMP response is an UpStreamErrorResponse" in :
      stubFor(
        post(urlPathEqualTo("/user-management/users/joe.bloggs/vpn/true"))
          .willReturn(
            aResponse()
              .withStatus(500)
          )
      )

      an[RuntimeException] shouldBe thrownBy {
        connector.manageVpnAccess(UserName("joe.bloggs"), enableVpn = true).futureValue
      }

  "manageDevToolsAccess" should :
    "return Unit when UMP response is 200" in :
      stubFor(
        post(urlPathEqualTo("/user-management/users/joe.bloggs/dev-tools/true"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.manageDevToolsAccess(UserName("joe.bloggs"), enableDevTools = true).futureValue shouldBe()

      verify(
        postRequestedFor(urlPathEqualTo("/user-management/users/joe.bloggs/dev-tools/true"))
      )

    "throw a RuntimeException when UMP response is an UpStreamErrorResponse" in :
      stubFor(
        post(urlPathEqualTo("/user-management/users/joe.bloggs/dev-tools/true"))
          .willReturn(
            aResponse()
              .withStatus(500)
          )
      )

      an[RuntimeException] shouldBe thrownBy {
        connector.manageDevToolsAccess(UserName("joe.bloggs"), enableDevTools = true).futureValue
      }

  "restLdapPassword" should:
    val resetLdapPassword =
      ResetLdapPassword("joe.bloggs", "tes@email.com")

    "return JSON with a ticket ID when UMP response is 200" in :
      stubFor(
        post(urlPathEqualTo("/user-management/reset-ldap-password"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""{"ticket_number": "some-unique-ticket-id"}""")
          )
      )

      connector.resetLdapPassword(resetLdapPassword).futureValue shouldBe Some("some-unique-ticket-id")

      verify(
        postRequestedFor(urlPathEqualTo("/user-management/reset-ldap-password"))
      )

    "throw a RuntimeException when UMP response is an UpStreamErrorResponse" in :
      stubFor(
        post(urlPathEqualTo("/user-management/reset-ldap-password"))
          .willReturn(
            aResponse()
              .withStatus(500)
          )
      )

      an[RuntimeException] shouldBe thrownBy {
        connector.resetLdapPassword(resetLdapPassword).futureValue
      }

  "resetGooglePassword" should :

    val resetGooglePassword =
      ResetGooglePassword(
        username = "joe.bloggs",
        password = SensitiveString("password")
      )

    val actualResetGooglePassword =
      """{
        |  "username" : "joe.bloggs",
        |  "password" : "password"
        |}
        |""".stripMargin

    "return Unit when UMP response is 200" in :
      stubFor(
        put(urlPathEqualTo(s"/user-management/reset-google-password"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.resetGooglePassword(resetGooglePassword).futureValue shouldBe()

      verify(
        putRequestedFor(urlPathEqualTo("/user-management/reset-google-password"))
          .withRequestBody(equalToJson(actualResetGooglePassword))
      )

    "throw a RuntimeException when UMP response is an UpStreamErrorResponse" in :
      stubFor(
        post(urlPathEqualTo("/user-management/reset-google-password"))
          .willReturn(
            aResponse()
              .withStatus(500)
          )
      )

      an[RuntimeException] shouldBe thrownBy {
        connector.resetGooglePassword(resetGooglePassword).futureValue
      }

  "editTeamDetails" should :
    "return Unit when sending all details and UMP response is 200" in:
      val editTeamDetailsRequest =
        EditTeamDetails(
          team              = "team-name",
          description       = Some("description"),
          documentation     = Some("https://documentation.url"),
          slack             = Some("team-name"),
          slackNotification = Some("team-name-alerts")
        )

      val actualEditTeamDetailsRequest =
        """{
          |  "team" : "team-name",
          |  "description" : "description",
          |  "documentation" : "https://documentation.url",
          |  "slack" : "https://hmrcdigital.slack.com/messages/team-name",
          |  "slackNotification" : "https://hmrcdigital.slack.com/messages/team-name-alerts"
          |}
          |""".stripMargin


      stubFor(
        patch(urlPathEqualTo(s"/user-management/edit-team-details"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.editTeamDetails(editTeamDetailsRequest).futureValue shouldBe()

      verify(
        patchRequestedFor(urlPathEqualTo("/user-management/edit-team-details"))
          .withRequestBody(equalToJson(actualEditTeamDetailsRequest))
      )

    "return Unit when sending a single field and UMP response is 200" in :
      val editTeamDetailsRequest =
        EditTeamDetails(
          team = "team-name",
          description = Some("description"),
          documentation = None,
          slack = None,
          slackNotification = None
        )

      val actualEditTeamDetailsRequest =
        """{
          |  "team" : "team-name",
          |  "description" : "description"
          |}
          |""".stripMargin


      stubFor(
        patch(urlPathEqualTo(s"/user-management/edit-team-details"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.editTeamDetails(editTeamDetailsRequest).futureValue shouldBe()

      verify(
        patchRequestedFor(urlPathEqualTo("/user-management/edit-team-details"))
          .withRequestBody(equalToJson(actualEditTeamDetailsRequest))
      )

  "offboardUsers" should:
    "return Unit when sending offboard users request and UMP response is 200" in:
      val offboardUsersRequest =
        OffBoardUsers(
          usernames = Set("user.one", "user.two")
        )

      val actualOffboardUsersRequest =
        """{
          |  "usernames" : [ "user.one", "user.two" ]
          |}
          |""".stripMargin

      stubFor(
        post(urlPathEqualTo("/user-management/offboard-users"))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.offBoardUsers(offboardUsersRequest).futureValue shouldBe()

      verify(
        postRequestedFor(urlPathEqualTo("/user-management/offboard-users"))
          .withRequestBody(equalToJson(actualOffboardUsersRequest))
      )
