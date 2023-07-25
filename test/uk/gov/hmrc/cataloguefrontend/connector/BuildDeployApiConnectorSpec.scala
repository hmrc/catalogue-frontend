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
import play.api.Configuration
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import uk.gov.hmrc.cataloguefrontend.ChangePrototypePassword.PrototypePassword
import uk.gov.hmrc.cataloguefrontend.config.BuildDeployApiConfig
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.{AsyncRequestId, PrototypeStatus, PrototypeDetails}
import uk.gov.hmrc.cataloguefrontend.createappconfigs.CreateAppConfigsRequest
import uk.gov.hmrc.cataloguefrontend.createarepository.CreateRepoForm
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}

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

      val requestJson = """{ "repository_name": "test", "password": "newpassword" }"""

      stubFor(
        post("/v1/SetHerokuPrototypePassword")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(200).withBody(
            """{ "success": true, "message": "password changed" }"""
          ))
      )

      val result = connector.changePrototypePassword("test", PrototypePassword("newpassword")).futureValue

      result shouldBe Right("password changed")
    }

    "return success=false when Build & Deploy respond with 400" in {
      val requestJson = """{ "repository_name": "test", "password": "p4$$w02d" }"""

      stubFor(
        post("/v1/SetHerokuPrototypePassword")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(400).withBody(
            """{ "code": "INVALID_PASSWORD", "message": "Password was empty OR contained invalid characters. Valid characters: Alphanumeric and underscores." }"""
          ))
      )

      val result = connector.changePrototypePassword("test", PrototypePassword("p4$$w02d")).futureValue

      result shouldBe Left("Password was empty OR contained invalid characters. Valid characters: Alphanumeric and underscores.")
    }

    "return UpstreamErrorResponse when Build & Deploy respond with 400 without json/message" in {
      val requestJson = """{ "repository_name": "test", "password": "p4$$w02d" }"""

      stubFor(
        post("/v1/SetHerokuPrototypePassword")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(400).withBody(""))
      )

      val result = connector.changePrototypePassword("test", PrototypePassword("p4$$w02d")).failed.futureValue

      result shouldBe a [UpstreamErrorResponse]
    }

    "return UpstreamErrorResponse when Build & Deploy respond with 500" in {
      val requestJson = """{ "repository_name": "test", "password": "p4$$w02d" }"""

      stubFor(
        post("/v1/SetHerokuPrototypePassword")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(500))
      )

      val result = connector.changePrototypePassword("test", PrototypePassword("p4$$w02d")).failed.futureValue

      result shouldBe a [UpstreamErrorResponse]
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
              |    "status": "running",
              |    "prototype_url": "https://test-prototype.herokuapp.com"
              |  }
              |}""".stripMargin
          ))
      )

      val result = connector.getPrototypeDetails("test-prototype").futureValue

      result shouldBe PrototypeDetails(Some("https://test-prototype.herokuapp.com"), PrototypeStatus.Running)
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

      val result = connector.getPrototypeDetails("test-prototype").futureValue

      result shouldBe PrototypeDetails(None, PrototypeStatus.Undetermined)
    }
  }

  "setPrototypeStatus" should {
    "return the new status of the prototype when 200" in {
      val requestJson = """{ "prototype": "test-prototype", "status": "running" }"""

      stubFor(
        post("/v1/SetPrototypeStatus")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(200).withBody(
            """
              |{
              |  "success": true,
              |  "message": "Successfully running test-prototype",
              |  "details": {
              |    "prototype": "test-prototype",
              |    "status": "running"
              |  }
              |}""".stripMargin
          ))
      )

      val result = connector.setPrototypeStatus("test-prototype", PrototypeStatus.Running).futureValue

      result shouldBe Right(())
    }

    "return a new status of Undetermined when non 200" in {
      val requestJson = """{ "prototype": "test-prototype", "status": "running" }"""

      stubFor(
        post("/v1/SetPrototypeStatus")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(400).withBody(
            """
              |{
              |  "code": 500,
              |  "success": false,
              |  "message": "Some downstream error",
              |  "details": {
              |    "prototype": "test-prototype",
              |    "status": "undetermined"
              |  }
              |}""".stripMargin
          ))
      )

      val result = connector.setPrototypeStatus("test-prototype", PrototypeStatus.Running).futureValue

      result shouldBe Left("Some downstream error")
    }
  }

  "createARepository" should {
      "return the Async request id when the request is accepted by the B&D async api" in {
        val payload = CreateRepoForm(
          repositoryName = "test-repo", makePrivate = true, teamName = "team1", repoType = "Empty"
        )

        val expectedBody = s"""
                              |{
                              |   "repository_name": "${payload.repositoryName}",
                              |   "make_private": ${payload.makePrivate},
                              |   "allow_auto_merge": true,
                              |   "delete_branch_on_merge": true,
                              |   "team_name": "${payload.teamName}",
                              |   "repository_type": "${payload.repoType}",
                              |   "bootstrap_tag": "",
                              |   "init_webhook_version": "2.2.0",
                              |   "default_branch_name": "main"
                              |}""".stripMargin

        stubFor(
          post("/v1/CreateRepository")
            .withRequestBody(equalToJson(expectedBody))
            .willReturn(aResponse().withStatus(202).withBody(
              """
                |{
                |  "message": "Your request has been queued for processing. You can call /GetRequestState with the contents of get_request_state_payload to track the progress of your request..",
                |  "details": {
                |    "get_request_state_payload": {
                |       "bnd_api_request_id": "1234",
                |       "start_timestamp_milliseconds": "1687852118708"
                |    }
                |  }
                |}""".stripMargin
            ))
        )

        val result = connector.createARepository(payload = payload).futureValue

        result shouldBe Right(AsyncRequestId(id = "1234"))
      }

    "return an UpstreamErrorResponse when the B&D async api returns a 5XX code" in {
      val payload = CreateRepoForm(
        repositoryName = "test-repo", makePrivate = true, teamName = "team1", repoType = "Empty"
      )

      val expectedBody = s"""
                            |{
                            |   "repository_name": "${payload.repositoryName}",
                            |   "make_private": ${payload.makePrivate},
                            |   "allow_auto_merge": true,
                            |   "delete_branch_on_merge": true,
                            |   "team_name": "${payload.teamName}",
                            |   "repository_type": "${payload.repoType}",
                            |   "bootstrap_tag": "",
                            |   "init_webhook_version": "2.2.0",
                            |   "default_branch_name": "main"
                            |}""".stripMargin

      stubFor(
        post("/v1/CreateRepository")
          .withRequestBody(equalToJson(expectedBody))
          .willReturn(aResponse().withStatus(500)
            .withBody(
              """{ "code": "INTERNAL_SERVER_ERROR", "message": "Some server error" }"""
            ))
      )

      val result = connector.createARepository(payload = payload).failed.futureValue

      result shouldBe a [UpstreamErrorResponse]
    }

    //NOTE: Currently the B&D async API will return a BuildDeployResponse when the client inputs are invalid, as it does not do any JSON validation/input validation up front prior to calling the lambda.
    //This may however change in the future, so we are testing the desired future behaviour below.
    "return an error message when the B&D async api returns a 4XX code" in {
        val payload = CreateRepoForm(
          repositoryName = "test-repo", makePrivate = true, teamName = "team1", repoType = "Empty"
        )

        val expectedBody = s"""
                              |{
                              |   "repository_name": "${payload.repositoryName}",
                              |   "make_private": ${payload.makePrivate},
                              |   "allow_auto_merge": true,
                              |   "delete_branch_on_merge": true,
                              |   "team_name": "${payload.teamName}",
                              |   "repository_type": "${payload.repoType}",
                              |   "bootstrap_tag": "",
                              |   "init_webhook_version": "2.2.0",
                              |   "default_branch_name": "main"
                              |}""".stripMargin

        stubFor(
          post("/v1/CreateRepository")
            .withRequestBody(equalToJson(expectedBody))
            .willReturn(aResponse().withStatus(400)
            .withBody(
              """{ "code": "CLIENT_ERROR", "message": "Some client error" }"""
            ))
        )

        val result = connector.createARepository(payload = payload).futureValue

        result shouldBe Left("Some client error")
      }
    }

  "CreateAppConfigs" should {
     "return the Async request id when the request is accepted by the B&D async api" in {
       val payload = CreateAppConfigsRequest(appConfigBase = false, appConfigDevelopment = false, appConfigQA = false, appConfigStaging = false, appConfigProduction = false
       )

       val expectedBody = s"""
                             |{
                             |   "microservice_name": "test-service",
                             |   "microservice_type": "Backend microservice",
                             |   "microservice_requires_mongo": true,
                             |   "app_config_base": ${payload.appConfigBase},
                             |   "app_config_development": ${payload.appConfigDevelopment},
                             |   "app_config_qa": ${payload.appConfigQA},
                             |   "app_config_staging": ${payload.appConfigStaging},
                             |   "app_config_production": ${payload.appConfigProduction},
                             |   "zone": "protected"
                             |}""".stripMargin

       stubFor(
         post("/v1/CreateAppConfigs")
           .withRequestBody(equalToJson(expectedBody))
           .willReturn(aResponse().withStatus(202).withBody(
             """
               |{
               |  "message": "Your request has been queued for processing. You can call /GetRequestState with the contents of get_request_state_payload to track the progress of your request..",
               |  "details": {
               |    "get_request_state_payload": {
               |       "bnd_api_request_id": "1234",
               |       "start_timestamp_milliseconds": "1687852118708"
               |    }
               |  }
               |}""".stripMargin
           ))
       )

       val result =
         connector.createAppConfigs(payload = payload, serviceName = "test-service", serviceType = ServiceType.Backend, requiresMongo = true, isApi = false)
           .futureValue

       result shouldBe Right(AsyncRequestId(id = "1234"))
     }


    "return an UpstreamErrorResponse when the B&D async api returns a 5XX code" in {
      val payload = CreateAppConfigsRequest(
       appConfigBase = false, appConfigDevelopment = false, appConfigQA = false, appConfigStaging = false, appConfigProduction = false
      )

      val expectedBody = s"""
                            |{
                            |   "microservice_name": "test-service",
                            |   "microservice_type": "Frontend microservice",
                            |   "microservice_requires_mongo": true,
                            |   "app_config_base": ${payload.appConfigBase},
                            |   "app_config_development": ${payload.appConfigDevelopment},
                            |   "app_config_qa": ${payload.appConfigQA},
                            |   "app_config_staging": ${payload.appConfigStaging},
                            |   "app_config_production": ${payload.appConfigProduction},
                            |   "zone": "public"
                            |}""".stripMargin

      stubFor(
        post("/v1/CreateAppConfigs")
          .withRequestBody(equalToJson(expectedBody))
          .willReturn(aResponse().withStatus(500).withBody(
            """{ "code": "INTERNAL_SERVER_ERROR", "message": "Some server error" }"""
          ))
      )

      val result =
        connector.createAppConfigs(payload = payload, serviceName = "test-service", serviceType = ServiceType.Backend, requiresMongo = true, isApi = false)
          .failed
          .futureValue

      result shouldBe a [UpstreamErrorResponse]
    }

    //NOTE: Currently the B&D async API will return a BuildDeployResponse when the client inputs are invalid, as it does not do any JSON validation/input validation up front prior to calling the lambda.
    //This may however change in the future, so we are testing the desired future behaviour below.
    "return an error message when the B&D async api returns a 4XX code" in {
       val payload = CreateAppConfigsRequest(
         appConfigBase = false, appConfigDevelopment = false, appConfigQA = false, appConfigStaging = false, appConfigProduction = false
       )

       val expectedBody = s"""
                             |{
                             |   "microservice_name": "test-service",
                             |   "microservice_type": "Backend microservice",
                             |   "microservice_requires_mongo": true,
                             |   "app_config_base": ${payload.appConfigBase},
                             |   "app_config_development": ${payload.appConfigDevelopment},
                             |   "app_config_qa": ${payload.appConfigQA},
                             |   "app_config_staging": ${payload.appConfigStaging},
                             |   "app_config_production": ${payload.appConfigProduction},
                             |   "zone": "protected"
                             |}""".stripMargin

       stubFor(
         post("/v1/CreateAppConfigs")
           .withRequestBody(equalToJson(expectedBody))
           .willReturn(aResponse().withStatus(400)
             .withBody(
               """{ "code": "CLIENT_ERROR", "message": "Some client error" }"""
             ))
       )

       val result =
         connector.createAppConfigs(payload = payload, serviceName = "test-service", serviceType = ServiceType.Backend, requiresMongo = true, isApi = false)
           .futureValue

       result shouldBe Left("Some client error")
    }

  }
}
