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

import play.api.Configuration
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import uk.gov.hmrc.cataloguefrontend.config.BuildDeployApiConfig
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.cataloguefrontend.ChangePrototypePassword.PrototypePassword
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.{ChangePrototypePasswordRequest, ChangePrototypePasswordResponse, PrototypeStatus}

import scala.concurrent.ExecutionContext.Implicits.global

class BuildDeployApiConnectorSpec extends UnitSpec with HttpClientV2Support with WireMockSupport {

  private val awsCredentialsProvider =
    StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))

  private val config =
    new BuildDeployApiConfig(
      Configuration(
        "build-deploy-api.url" -> wireMockUrl,
        "build-deploy-api.host" -> wireMockHost,
        "build-deploy-api.aws-region" -> "eu-west-2",
      )
    )

  private val connector = new BuildDeployApiConnector(httpClientV2, awsCredentialsProvider, config)

  "changePrototypePassword" should {
    "return success=true when Build & Deploy respond with 200" in {
      val payload = ChangePrototypePasswordRequest("test", PrototypePassword("newpassword"))

      val requestJson = """{ "repository_name": "test", "password": "newpassword" }"""

      stubFor(
        post("/v1/SetHerokuPrototypePassword")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(200).withBody(
            """{ "success": true, "message": "password changed" }"""
          ))
      )

      val expected = ChangePrototypePasswordResponse(success = true, "password changed")
      val result = connector.changePrototypePassword(payload).futureValue

      result shouldBe expected
    }

    "return success=false when Build & Deploy respond with 400" in {
      val payload = ChangePrototypePasswordRequest("test", PrototypePassword("p4$$w02d"))

      val requestJson = """{ "repository_name": "test", "password": "p4$$w02d" }"""

      stubFor(
        post("/v1/SetHerokuPrototypePassword")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(400).withBody(
            """{ "code": "INVALID_PASSWORD", "message": "Password was empty OR contained invalid characters. Valid characters: Alphanumeric and underscores." }"""
          ))
      )

      val result = connector.changePrototypePassword(payload).futureValue

      result.success shouldBe false
    }
  }

  "getPrototypeStatus" should {
    "return the status of the prototype when 200" in {
      val requestJson = """{ "prototype": "test-prototype" }"""

      stubFor(
        post("/v1/GetPrototypeStatus")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(200).withBody(
            """
              |{
              |  "success": true,
              |  "message": "Successfully retrieved status",
              |  "details": {
              |    "prototype": "test-prototype",
              |    "status": "running"
              |  }
              |}""".stripMargin
          ))
      )

      val result = connector.getPrototypeStatus("test-prototype").futureValue

      result shouldBe PrototypeStatus.Running
    }

    "return a status of Undetermined when non 200" in {
      val requestJson = """{ "prototype": "test-prototype" }"""

      stubFor(
        post("/v1/GetPrototypeStatus")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(400).withBody(
            """
              |{
              |  "code": 403,
              |  "success": false,
              |  "message": "Some downstream error",
              |  "details": {
              |    "prototype": "test-prototype",
              |    "status": "undetermined"
              |  }
              |}""".stripMargin
          ))
      )

      val result = connector.getPrototypeStatus("test-prototype").futureValue

      result shouldBe PrototypeStatus.Undetermined
    }
  }
}
