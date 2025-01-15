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
import uk.gov.hmrc.cataloguefrontend.model.{TeamName, UserName}
import uk.gov.hmrc.cataloguefrontend.users.*
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

      connector.getTeam(team).futureValue shouldBe UmpTeam(Seq.empty[Member], team, None, None, None, None)
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
                  ]
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
            teamNames      = Seq(TeamName("TestTeam"))
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
                   ]
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
                   ]
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
            teamNames      = Seq(TeamName("TestTeam"))
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
            teamNames      = Seq(TeamName("TestTeam"))
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
                ]
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
            teamNames      = Seq(TeamName("TestTeam"))
          )
        )
    }

    "return None when not found" in {
      val username = UserName("non.existent")

      stubFor(
        get(urlPathEqualTo(s"/user-management/users/$username"))
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
        get(urlPathEqualTo(s"/user-management/users/$username/access"))
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
        post(urlPathEqualTo(s"user-management/create-service-user"))
          .willReturn(
            aResponse()
              .withStatus(500)
          )
      )

      an[RuntimeException] shouldBe thrownBy {
        connector.createUser(createUserRequest.copy(isServiceAccount = true)).futureValue
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
          post(urlPathEqualTo(s"user-management/reset-ldap-password"))
            .willReturn(
              aResponse()
                .withStatus(500)
            )
        )

        an[RuntimeException] shouldBe thrownBy {
          connector.resetLdapPassword(resetLdapPassword).futureValue
        }
