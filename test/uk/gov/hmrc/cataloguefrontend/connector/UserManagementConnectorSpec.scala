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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.users.{LdapTeam, Member, User, Role}
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class UserManagementConnectorSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with WireMockSupport
     with HttpClientV2Support {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val connector: UserManagementConnector =
    new UserManagementConnector(
      httpClientV2,
      new ServicesConfig(Configuration(
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

      connector.getTeam(team).futureValue shouldBe
        Some(
          LdapTeam(Seq.empty[Member], team, None, None, None, None)
        )
    }

    "return None when not found" in {
      val team = TeamName("non-existent-team")

      stubFor(
        get(urlPathEqualTo(s"/user-management/teams/${team.asString}"))
          .willReturn(
            aResponse()
              .withStatus(404)
          )
      )

      connector.getTeam(team).futureValue shouldBe None
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
            username       = "joe.bloggs",
            githubUsername = Some("joebloggs-github"),
            phoneNumber    = Some("07123456789"),
            role           = Role("user"),
            teamNames      = Seq(new TeamName("TestTeam"))
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
            username       = "joe.bloggs",
            githubUsername = None,
            phoneNumber    = Some("07123456789"),
            role           = Role("user"),
            teamNames      = Seq(new TeamName("TestTeam"))
          ),
          User(
            displayName    = Some("Jane Doe"),
            familyName     = "Doe",
            givenName      = Some("Jane"),
            organisation   = Some("MDTP"),
            primaryEmail   = "jane.doe@digital.hmrc.gov.uk",
            username       = "jane.doe",
            githubUsername = None,
            phoneNumber    = Some("07123456789"),
            role           = Role("user"),
            teamNames      = Seq(new TeamName("TestTeam"))
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
      val username = "joe.bloggs"

      stubFor(
        get(urlPathEqualTo(s"/user-management/users/$username"))
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
            username       = "joe.bloggs",
            githubUsername = Some("joebloggs-github"),
            phoneNumber    = Some("07123456789"),
            role           = Role("user"),
            teamNames      = Seq(new TeamName("TestTeam"))
          )
        )
    }

    "return None when not found" in {
      val username = "non.existent"

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

  "createUser" should {

    val createUserRequest =
      CreateUserRequest(
        givenName        = "joe",
        familyName       = "bloggs",
        organisation     = "MDTP",
        contactEmail     = "email@test.gov.uk",
        contactComments  = "test",
        team             = "Test",
        isReturningUser  = false,
        isTransitoryUser = false,
        vpn              = true,
        jira             = true,
        confluence       = true,
        googleApps       = true,
        environments     = true
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
        |  "access": {
        |    "vpn": true,
        |    "jira": true,
        |    "confluence": true,
        |    "googleApps": true,
        |    "environments": true,
        |    "ldap": true
        |  },
        |  "username": "joe.bloggs",
        |  "displayName": "Joe Bloggs",
        |  "isServiceAccount": false,
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
        |  "access": {
        |    "vpn": true,
        |    "jira": true,
        |    "confluence": true,
        |    "googleApps": true,
        |    "environments": true,
        |    "ldap": true
        |  },
        |  "username": "service_joe.bloggs",
        |  "displayName": "service_joe bloggs",
        |  "isServiceAccount": true,
        |  "isExistingLDAPUser": false
        |}
        |""".stripMargin

    "return Unit when UMP response is 200 for human user" in {
      stubFor(
        post(urlPathEqualTo(s"/user-management/create-user"))
          .withRequestBody(
            equalToJson(Json.toJson(createUserRequest)(CreateUserRequest.humanUserWrites).toString())
          )
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.createUser(createUserRequest, isServiceAccount = false).futureValue shouldBe ()

      verify(
        postRequestedFor(urlPathEqualTo("/user-management/create-user"))
          .withRequestBody(equalToJson(actualUserRequest))
      )
    }

    "return Unit when UMP response is 200 for non human user" in {
      stubFor(
        post(urlPathEqualTo(s"/user-management/create-user"))
          .withRequestBody(
            equalToJson(Json.toJson(createUserRequest.copy(givenName = "service_joe"))(CreateUserRequest.serviceUserWrites).toString())
          )
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      connector.createUser(createUserRequest.copy(givenName = "service_joe"), isServiceAccount = true).futureValue shouldBe ()

      verify(
        postRequestedFor(urlPathEqualTo("/user-management/create-user"))
          .withRequestBody(equalToJson(actualNonHumanUserRequest))
      )
    }

    "throw a RuntimeException when UMP response is an UpStreamErrorResponse" in {
      stubFor(
        post(urlPathEqualTo(s"/user-management/create-user"))
          .withRequestBody(
            equalToJson(Json.toJson(createUserRequest)(CreateUserRequest.humanUserWrites).toString())
          )
          .willReturn(
            aResponse()
              .withStatus(500)
          )
      )

      an[RuntimeException] shouldBe thrownBy {
        connector.createUser(createUserRequest, isServiceAccount = false).futureValue
      }
    }
  }
}
