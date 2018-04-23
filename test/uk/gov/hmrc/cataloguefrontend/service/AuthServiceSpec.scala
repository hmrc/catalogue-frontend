/*
 * Copyright 2018 HM Revenue & Customs
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
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector
import uk.gov.hmrc.cataloguefrontend.service.AuthService.UmpToken
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class AuthServiceSpec extends WordSpec with MockitoSugar with ScalaFutures {

  "authenticate" should {

    "return token if user management auth service returns one" in new Setup {
      val token = UmpToken(UUID.randomUUID().toString)
      when(userManagementAuthConnector.authenticate(username, password))
        .thenReturn(Future.successful(Right(token)))

      service.authenticate(username, password).futureValue shouldBe Right(token)
    }
  }

  private trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

    val username = "username"
    val password = "password"

    val userManagementAuthConnector = mock[UserManagementAuthConnector]
    val service                     = new AuthService(userManagementAuthConnector)
  }
}
