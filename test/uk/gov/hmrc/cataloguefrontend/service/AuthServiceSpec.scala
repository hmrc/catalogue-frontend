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

package uk.gov.hmrc.cataloguefrontend.service

import java.util.UUID

import cats.data.NonEmptyList
import cats.implicits._
import org.mockito.Mockito._
import org.mockito.Matchers.{any, eq ⇒ mockEq}
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.cataloguefrontend.actions.UmpAuthenticatedRequest
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, Team}
import uk.gov.hmrc.cataloguefrontend.connector.model.Username
import uk.gov.hmrc.cataloguefrontend.connector.{UserManagementAuthConnector, UserManagementConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.{TokenAndUserId, UmpToken, UmpUnauthorized, UmpUserId, User}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.{DisplayName, TeamMember}
import uk.gov.hmrc.cataloguefrontend.service.AuthService.TokenAndDisplayName
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AuthServiceSpec extends WordSpec with MockitoSugar with ScalaFutures {
  import AuthService._

  import ExecutionContext.Implicits.global

  implicit val defaultPatienceConfig = PatienceConfig(Span(500, Millis), Span(15, Millis))

  "AuthService.authenticate" should {
    val username = "username"
    val password = "password"

    "return token and display name" in new Setup {
      val token       = UmpToken(UUID.randomUUID().toString)
      val userId      = UmpUserId("john.smith")
      val displayName = DisplayName("John Smith")

      when(userManagementAuthConnector.authenticate(username, password))
        .thenReturn(Future.successful(Right(TokenAndUserId(token, userId))))

      when(userManagementConnector.getDisplayName(userId))
        .thenReturn(Future.successful(Some(displayName)))

      service.authenticate(username, password).futureValue shouldBe Right(TokenAndDisplayName(token, displayName))
    }

    "return token and userId if UMP doesn't return display name" in new Setup {
      val token  = UmpToken(UUID.randomUUID().toString)
      val userId = UmpUserId("john.smith")

      when(userManagementAuthConnector.authenticate(username, password))
        .thenReturn(Future.successful(Right(TokenAndUserId(token, userId))))

      when(userManagementConnector.getDisplayName(userId))
        .thenReturn(Future.successful(None))

      service.authenticate(username, password).futureValue shouldBe Right(
        TokenAndDisplayName(token, DisplayName(userId.value)))
    }

    "return an error if credentials invalid" in new Setup {
      when(userManagementAuthConnector.authenticate(username, password))
        .thenReturn(Future.successful(Left(UmpUnauthorized)))

      service.authenticate(username, password).futureValue shouldBe Left(UmpUnauthorized)
    }
  }

  "AuthService.authorizeServices" should {

    implicit val request = UmpAuthenticatedRequest(
        request     = FakeRequest()
      , token       = UmpToken("token")
      , user        = User(username = Username("username"), groups = List.empty)
      , displayName = DisplayName("Username")
      )

    "allow service belonging to team containing user" in new Setup {
      when(teamsAndRepositoriesConnector.teamsWithRepositories(any()))
        .thenReturn(Future(List(team("team1", List("service1")))))

      when(userManagementConnector.getTeamMembersFromUMP(mockEq("team1"))(any()))
        .thenReturn(Future(Either.right[UserManagementConnector.UMPError, Seq[TeamMember]](Seq(teamMember(request.user.username.value)))))

      val res = service.authorizeServices(NonEmptyList.of("service1"))(request, hc).futureValue

      res shouldBe Right(())
    }

    "allow service belonging to multiple teams, one of which contains user" in new Setup {
      when(teamsAndRepositoriesConnector.teamsWithRepositories(any()))
        .thenReturn(Future(List(
            team("team1", List("service1"))
          , team("team2", List("service1"))
          )))

      when(userManagementConnector.getTeamMembersFromUMP(mockEq("team1"))(any()))
        .thenReturn(Future(Either.right[UserManagementConnector.UMPError, Seq[TeamMember]](Seq.empty)))

      when(userManagementConnector.getTeamMembersFromUMP(mockEq("team2"))(any()))
        .thenReturn(Future(Either.right[UserManagementConnector.UMPError, Seq[TeamMember]](Seq(teamMember(request.user.username.value)))))

      val res = service.authorizeServices(NonEmptyList.of("service1"))(request, hc).futureValue

      res shouldBe Right(())
    }

    "deny service which are not found in any team" in new Setup {
      when(teamsAndRepositoriesConnector.teamsWithRepositories(any()))
        .thenReturn(Future(List(team("team1", Seq.empty))))

      val res = service.authorizeServices(NonEmptyList.of("service1"))(request, hc).futureValue

      res shouldBe Left(ServiceForbidden(NonEmptyList.of("service1")))
    }

    "deny service which belong to teams not containing user" in new Setup {
      when(teamsAndRepositoriesConnector.teamsWithRepositories(any()))
        .thenReturn(Future(List(team("team1", List("service1")))))

      when(userManagementConnector.getTeamMembersFromUMP(mockEq("team1"))(any()))
        .thenReturn(Future(Either.right[UserManagementConnector.UMPError, Seq[TeamMember]](Seq(teamMember("another.user")))))

      val res = service.authorizeServices(NonEmptyList.of("service1"))(request, hc).futureValue

      res shouldBe Left(ServiceForbidden(NonEmptyList.of("service1")))
    }

    "only report service once if denied from multiple teams" in new Setup {
      when(teamsAndRepositoriesConnector.teamsWithRepositories(any()))
        .thenReturn(Future(List(
            team("team1", List("service1"))
          , team("team2", List("service1"))
          )))

      when(userManagementConnector.getTeamMembersFromUMP(mockEq("team1"))(any()))
        .thenReturn(Future(Either.right[UserManagementConnector.UMPError, Seq[TeamMember]](Seq.empty)))

      when(userManagementConnector.getTeamMembersFromUMP(mockEq("team2"))(any()))
        .thenReturn(Future(Either.right[UserManagementConnector.UMPError, Seq[TeamMember]](Seq.empty)))

      val res = service.authorizeServices(NonEmptyList.of("service1"))(request, hc).futureValue

      res shouldBe Left(ServiceForbidden(NonEmptyList.of("service1")))
    }
  }


  private trait Setup {
    implicit val hc = HeaderCarrier()

    val userManagementAuthConnector   = mock[UserManagementAuthConnector]
    val userManagementConnector       = mock[UserManagementConnector]
    val teamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    val service                       = new AuthService(userManagementAuthConnector, userManagementConnector, teamsAndRepositoriesConnector)
  }

  def team(name: String, services: Seq[String]) = Team(
      name                     = name
    , firstActiveDate          = None
    , lastActiveDate           = None
    , firstServiceCreationDate = None
    , repos                    = Some(Map(RepoType.Service.toString -> services))
    )

  def teamMember(username: String) = TeamMember(
      displayName     = None
    , familyName      = None
    , givenName       = None
    , primaryEmail    = None
    , serviceOwnerFor = None
    , username        = Some(username)
    )
}
