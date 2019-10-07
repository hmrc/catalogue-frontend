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

import org.mockito.Mockito._
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Millis, Span}
import uk.gov.hmrc.cataloguefrontend.connector.{UserManagementAuthConnector, UserManagementConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.DisplayName
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.{TokenAndUserId, UmpToken, UmpUnauthorized, UmpUserId}
import uk.gov.hmrc.cataloguefrontend.service.AuthService.TokenAndDisplayName
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AuthServiceSpec extends WordSpec with MockitoSugar with ScalaFutures {
  import ExecutionContext.Implicits.global

  implicit val defaultPatienceConfig = PatienceConfig(Span(500, Millis), Span(15, Millis))

  "authenticate" should {

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

  private trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val username = "username"
    val password = "password"

    val userManagementAuthConnector   = mock[UserManagementAuthConnector]
    val userManagementConnector       = mock[UserManagementConnector]
    val teamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    val service                       = new AuthService(userManagementAuthConnector, userManagementConnector, teamsAndRepositoriesConnector)
  }
}
