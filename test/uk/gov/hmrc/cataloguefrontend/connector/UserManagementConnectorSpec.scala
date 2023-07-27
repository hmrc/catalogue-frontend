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
import uk.gov.hmrc.cataloguefrontend.users.{LdapTeam, Member, TeamMembership, User}
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
      val team = "test-team"

      stubFor(
        get(urlPathEqualTo(s"/user-management/team/$team"))
          .willReturn(
            aResponse()
              .withBody(
                s"""
                   |{
                   |  "members": [],
                   |  "teamName": "$team"
                   |}
                   |""".stripMargin
              )
          )
      )

      connector.getTeam(team).futureValue shouldBe
        Some(
          LdapTeam(Seq.empty[Member], team, None, None, None, None)
        )
    }

    "return None when not found" in {
      val team = "non-existent-team"

      stubFor(
        get(urlPathEqualTo(s"/user-management/team/$team"))
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
              .withBody(
                """
                  |[
                  |  {
                  |	   "displayName" : "Joe Bloggs",
                  |	   "familyName" : "Bloggs",
                  |	   "givenName" : "Joe",
                  |	   "organisation" : "MDTP",
                  |	   "primaryEmail" : "joe.bloggs@digital.hmrc.gov.uk",
                  |	   "username" : "joe.bloggs",
                  |	   "github" : "https://github.com/joebloggs",
                  |	   "phoneNumber" : "07123456789",
                  |	   "teamsAndRoles" : [
                  |	   	 {
                  |	   	   "teamName" : "Test Team",
                  |	   	   "role" : "user"
                  |	   	 }
                  |	   ]
                  |  }
                  |]
                  |""".stripMargin)
          )
      )

      connector.getAllUsers.futureValue should contain theSameElementsAs
        Seq(
          User(
            displayName = Some("Joe Bloggs"),
            familyName = "Bloggs",
            givenName = Some("Joe"),
            organisation = Some("MDTP"),
            primaryEmail = "joe.bloggs@digital.hmrc.gov.uk",
            username = "joe.bloggs",
            github = Some("https://github.com/joebloggs"),
            phoneNumber = Some("07123456789"),
            teamsAndRoles = Some(Seq(TeamMembership(teamName = "Test Team", role = "user")))
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

      connector.getAllUsers.futureValue.isEmpty shouldBe true
    }
  }

  "getUser" should {
    "return user when found" in {
      val username = "joe.bloggs"

      stubFor(
        get(urlPathEqualTo(s"/user-management/user/$username"))
          .willReturn(
            aResponse()
              .withBody(
                """
                  |{
                  |  "displayName" : "Joe Bloggs",
                  |  "familyName" : "Bloggs",
                  |  "givenName" : "Joe",
                  |  "organisation" : "MDTP",
                  |  "primaryEmail" : "joe.bloggs@digital.hmrc.gov.uk",
                  |  "username" : "joe.bloggs",
                  |  "github" : "https://github.com/joebloggs",
                  |  "phoneNumber" : "07123456789",
                  |  "teamsAndRoles" : [
                  |  	 {
                  |  	   "teamName" : "Test Team",
                  |  	   "role" : "user"
                  |  	 }
                  |  ]
                  |}
                  |""".stripMargin)
          )
      )

      connector.getUser(username).futureValue shouldBe
        Some(
          User(
            displayName = Some("Joe Bloggs"),
            familyName = "Bloggs",
            givenName = Some("Joe"),
            organisation = Some("MDTP"),
            primaryEmail = "joe.bloggs@digital.hmrc.gov.uk",
            username = "joe.bloggs",
            github = Some("https://github.com/joebloggs"),
            phoneNumber = Some("07123456789"),
            teamsAndRoles = Some(Seq(TeamMembership(teamName = "Test Team", role = "user")))
          )
        )
    }

    "return None when not found" in {
      val username = "non.existent"

      stubFor(
        get(urlPathEqualTo(s"/user-management/user/$username"))
          .willReturn(
            aResponse()
              .withStatus(404)
          )
      )

      connector.getUser(username).futureValue shouldBe None
    }
  }


}
