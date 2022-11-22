package uk.gov.hmrc.cataloguefrontend.connector

import play.api.Configuration
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import uk.gov.hmrc.cataloguefrontend.config.BuildDeployApiConfig
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.{ChangePrototypePasswordRequest, ChangePrototypePasswordResponse}

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
      val payload = ChangePrototypePasswordRequest("test", "newpassword")
      val requestJson = Json.toJson(payload).toString()

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
      val payload = ChangePrototypePasswordRequest("test", "p4$$w02d")
      val requestJson = Json.toJson(payload).toString()

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
}
