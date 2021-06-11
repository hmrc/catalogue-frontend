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

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Configuration
import play.api.libs.json.{JsArray, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector._
import uk.gov.hmrc.cataloguefrontend.connector.model.Username
import uk.gov.hmrc.http.{BadGatewayException, HeaderCarrier}
import uk.gov.hmrc.http.test.{HttpClientSupport, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext

class UserManagementAuthConnectorSpec
  extends AnyWordSpec
     with Matchers
     with WireMockSupport
     with HttpClientSupport
     with ScalaFutures
     with BeforeAndAfterEach
     with ScalaCheckDrivenPropertyChecks {
  import ExecutionContext.Implicits.global

  import org.scalacheck.Shrink.shrinkAny // disable shrinking here

  "authenticate" should {
    "return authorization token when UMP auth service returns OK containing it" in new Setup {
      val token  = UUID.randomUUID().toString
      val userId = "john.smith"

      stubFor(
        post(urlEqualTo("/v1/login"))
          .willReturn(aResponse().withBody(Json.obj("Token" -> token, "uid" -> userId).toString))
      )

      connector.authenticate(username, password).futureValue shouldBe Right(
        TokenAndUserId(UmpToken(token), UmpUserId(userId)))

      verify(postRequestedFor(urlEqualTo("/v1/login"))
        .withRequestBody(equalTo(Json.obj("username" -> username, "password" -> password).toString))
      )
    }

    List(UNAUTHORIZED, FORBIDDEN).foreach { status =>
      s"return unauthorized when UMP auth service returns $status" in new Setup {
        stubFor(
          post(urlEqualTo("/v1/login"))
            .willReturn(aResponse().withStatus(status))
        )

        connector.authenticate(username, password).futureValue shouldBe Left(UmpUnauthorized)

        verify(postRequestedFor(urlEqualTo("/v1/login"))
          .withRequestBody(equalTo(Json.obj("username" -> username, "password" -> password).toString))
        )
      }
    }

    List(CREATED, NOT_FOUND, INTERNAL_SERVER_ERROR).foreach { status =>
      s"throw BadGatewayException for unrecognized $status status" in new Setup {
        stubFor(
          post(urlEqualTo("/v1/login"))
            .willReturn(aResponse().withStatus(status))
        )

        val e = connector.authenticate(username, password).failed.futureValue
        e shouldBe an[BadGatewayException]
        e.getMessage shouldBe s"Received $status from POST to $wireMockUrl/v1/login"

        verify(postRequestedFor(urlEqualTo("/v1/login"))
          .withRequestBody(equalTo(Json.obj("username" -> username, "password" -> password).toString))
        )
      }
    }
  }

  "isValid" should {
    "return User if UMP Auth service returns 200 OK" in new Setup {
      stubFor(
        get(urlEqualTo("/v1/login"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.obj("uid" -> username, "groups" -> JsArray(Seq.empty)).toString)
          )
      )

      val umpToken = UmpToken("value")
      connector.getUser(umpToken).futureValue shouldBe Some(User(Username("username"), groups = List.empty))

      verify(getRequestedFor(urlEqualTo("/v1/login"))
        .withHeader("Token", equalTo(umpToken.value))
      )
    }

    List(UNAUTHORIZED, FORBIDDEN).foreach { status =>
      s"return false if UMP returns $status status" in new Setup {
        stubFor(
          get(urlEqualTo("/v1/login"))
            .willReturn(aResponse().withStatus(status))
        )

        val umpToken = UmpToken("value")
        connector.getUser(umpToken).futureValue shouldBe None

        verify(getRequestedFor(urlEqualTo("/v1/login"))
          .withHeader("Token", equalTo(umpToken.value))
        )
      }
    }

    "throw BadGatewayException if UMP Auth Service returns other unexpected status" in new Setup {
      val umpToken = UmpToken("value")
      forAll(Gen.choose(201, 599).suchThat(_ != UNAUTHORIZED).suchThat(_ != FORBIDDEN)) { unsupportedStatus =>
        stubFor(
          get(urlEqualTo("/v1/login"))
            .willReturn(aResponse().withStatus(unsupportedStatus))
        )

        val e = connector.getUser(umpToken).failed.futureValue
        e shouldBe an[BadGatewayException]
        e.getMessage shouldBe s"Received $unsupportedStatus from GET to $wireMockUrl/v1/login"

        verify(getRequestedFor(urlEqualTo("/v1/login"))
          .withHeader("Token", equalTo(umpToken.value))
        )
      }
    }
  }

  private trait Setup {
    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    val username                              = "username"
    val password                              = "password"
    val userManagementAuthConfig              = new UserManagementAuthConfig(
      new ServicesConfig(
        Configuration(
          "microservice.services.user-management-auth.url" -> wireMockUrl
        )
      )
    )

    val connector = new UserManagementAuthConnector(httpClient, userManagementAuthConfig)
  }
}
