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
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.ChangePrototypePassword.PrototypePassword
import uk.gov.hmrc.cataloguefrontend.config.BuildDeployApiConfig
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.{AsyncRequestId, PrototypeStatus, PrototypeDetails}
import uk.gov.hmrc.cataloguefrontend.createappconfigs.CreateAppConfigsForm
import uk.gov.hmrc.cataloguefrontend.createrepository.CreateServiceRepoForm
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class BuildDeployApiConnectorSpec extends UnitSpec with HttpClientV2Support with WireMockSupport {

  private val underlyingConfig = Configuration(
        "build-deploy-api.url"                       -> wireMockUrl,
        "build-deploy-api.host"                      -> wireMockHost,
        "build-deploy-api.aws-region"                -> "eu-west-2",
        "microservice.services.platops-bnd-api.port" -> wireMockPort,
        "microservice.services.platops-bnd-api.host" -> wireMockHost,
      )

  private val config =
    new BuildDeployApiConfig(
      underlyingConfig,
      new ServicesConfig(underlyingConfig)
    )

  private val connector = new BuildDeployApiConnector(httpClientV2, config)

  "changePrototypePassword" should {
    "return success=true when Build & Deploy respond with 200" in {

      val requestJson = """{ "repositoryName": "test", "password": "newpassword" }"""

      stubFor(
        post("/change-prototype-password")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(200).withBody(
            """{ "success": true, "message": "password changed" }"""
          ))
      )

      val result = connector.changePrototypePassword("test", PrototypePassword("newpassword")).futureValue

      result shouldBe Right("password changed")
    }

    "return success=false when Build & Deploy respond with 400" in {
      val requestJson = """{ "repositoryName": "test", "password": "p4$$w02d" }"""

      stubFor(
        post("/change-prototype-password")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(400).withBody(
            """{ "code": "INVALID_PASSWORD", "message": "Password was empty OR contained invalid characters. Valid characters: Alphanumeric and underscores." }"""
          ))
      )

      val result = connector.changePrototypePassword("test", PrototypePassword("p4$$w02d")).futureValue

      result shouldBe Left("Password was empty OR contained invalid characters. Valid characters: Alphanumeric and underscores.")
    }

    "return UpstreamErrorResponse when Build & Deploy respond with 400 without json/message" in {
      val requestJson = """{ "repositoryName": "test", "password": "p4$$w02d" }"""

      stubFor(
        post("/change-prototype-password")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(400).withBody(""))
      )

      val result = connector.changePrototypePassword("test", PrototypePassword("p4$$w02d")).failed.futureValue

      result shouldBe a [UpstreamErrorResponse]
    }

    "return UpstreamErrorResponse when Build & Deploy respond with 500" in {
      val requestJson = """{ "repositoryName": "test", "password": "p4$$w02d" }"""

      stubFor(
        post("change-prototype-password")
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
        post("/get-prototype-details")
          .withRequestBody(equalToJson(requestJson))
          .willReturn(aResponse().withStatus(200).withBody(
            """
              |{
              |  "success": true,
              |  "message": "Successfully retrieved status",
              |  "details": {
              |    "prototype": "test-prototype",
              |    "status": "running",
              |    "prototypeUrl": "https://test-prototype.herokuapp.com"
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
        post("/get-prototype-details")
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
        post("/set-prototype-status")
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
        post("/set-prototype-status")
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
        val payload = CreateServiceRepoForm(
          repositoryName = "test-repo", makePrivate = true, teamName = "team1", repoType = "Empty"
        )

        val expectedBody = s"""
                              |{
                              |   "repositoryName": "${payload.repositoryName}",
                              |   "makePrivate": ${payload.makePrivate},
                              |   "teamName": "${payload.teamName}",
                              |   "repositoryType": "${payload.repoType}",
                              |   "slackNotificationChannels" : ""
                              |}""".stripMargin

        stubFor(
          post("/create-service-repository")
            .withRequestBody(equalToJson(expectedBody))
            .willReturn(aResponse().withStatus(202).withBody(
              """
                |{
                |  "message": "Your request has been queued for processing. You can call /GetRequestState with the contents of get_request_state_payload to track the progress of your request..",
                |  "details": {
                |     "bnd_api_request_id": "1234",
                |     "start_timestamp_milliseconds": 1687852118708
                |  }
                |}""".stripMargin
            ))
        )

        val result = connector.createServiceRepository(payload = payload).futureValue

        result shouldBe Right(AsyncRequestId(JsObject(Seq(
          "bnd_api_request_id"           -> JsString("1234"),
          "start_timestamp_milliseconds" -> JsNumber(1687852118708L)
        ))))
      }

    "return an UpstreamErrorResponse when the B&D async api returns a 5XX code" in {
      val payload = CreateServiceRepoForm(
        repositoryName = "test-repo", makePrivate = true, teamName = "team1", repoType = "Empty"
      )

      val expectedBody = s"""
                            |{
                            |   "repositoryName": "${payload.repositoryName}",
                            |   "makePrivate": ${payload.makePrivate},
                            |   "teamName": "${payload.teamName}",
                            |   "repositoryType": "${payload.repoType}"
                            |}""".stripMargin

      stubFor(
        post("/create-service-repository")
          .withRequestBody(equalToJson(expectedBody))
          .willReturn(aResponse().withStatus(500)
            .withBody(
              """{ "code": "INTERNAL_SERVER_ERROR", "message": "Some server error" }"""
            ))
      )

      val result = connector.createServiceRepository(payload = payload).failed.futureValue

      result shouldBe a [UpstreamErrorResponse]
    }

    //NOTE: Currently the B&D async API will return a BuildDeployResponse when the client inputs are invalid, as it does not do any JSON validation/input validation up front prior to calling the lambda.
    //This may however change in the future, so we are testing the desired future behaviour below.
    "return an error message when the B&D async api returns a 4XX code" in {
        val payload = CreateServiceRepoForm(
          repositoryName = "test-repo", makePrivate = true, teamName = "team1", repoType = "Empty"
        )

        val expectedBody = s"""
                              |{
                              |   "repositoryName": "${payload.repositoryName}",
                              |   "makePrivate": ${payload.makePrivate},
                              |   "teamName": "${payload.teamName}",
                              |   "repositoryType": "${payload.repoType}",
                              |   "slackNotificationChannels" : ""
                              |}""".stripMargin

        stubFor(
          post("/create-service-repository")
            .withRequestBody(equalToJson(expectedBody))
            .willReturn(aResponse().withStatus(400)
            .withBody(
              """{ "code": "CLIENT_ERROR", "message": "Some client error" }"""
            ))
        )

        val result = connector.createServiceRepository(payload = payload).futureValue

        result shouldBe Left("Some client error")
      }
    }

  "CreateAppConfigs" should {
     "return the Async request id when the request is accepted by the B&D async api" in {
        val payload = CreateAppConfigsForm(appConfigBase = false, appConfigDevelopment = false, appConfigQA = false, appConfigStaging = false, appConfigProduction = false
        )

        val expectedBody = s"""
                              |{
                              |   "microserviceName": "test-service",
                              |   "microserviceType": "Backend microservice",
                              |   "hasMongo": true,
                              |   "environments": [],
                              |   "zone": "protected"
                              |}""".stripMargin

        stubFor(
          post("/create-app-configs")
            .withRequestBody(equalToJson(expectedBody))
            .willReturn(aResponse().withStatus(202).withBody(
              """
                |{
                |  "message": "Your request has been queued for processing. You can call /GetRequestState with the contents of get_request_state_payload to track the progress of your request..",
                |  "details": {
                |     "bnd_api_request_id": "1234",
                |     "start_timestamp_milliseconds": 1687852118708
                |  }
                |}""".stripMargin
            ))
        )

        val result =
          connector.createAppConfigs(payload = payload, serviceName = "test-service", serviceType = ServiceType.Backend, requiresMongo = true, isApi = false)
            .futureValue

        result shouldBe Right(AsyncRequestId(JsObject(Seq(
          "start_timestamp_milliseconds" -> JsNumber(1687852118708L),
          "bnd_api_request_id"           -> JsString("1234")
        ))))
     }


    "return an UpstreamErrorResponse when the B&D async api returns a 5XX code" in {
      val payload = CreateAppConfigsForm(
       appConfigBase = false, appConfigDevelopment = false, appConfigQA = false, appConfigStaging = false, appConfigProduction = false
      )

      val expectedBody = s"""
                            |{
                            |   "microserviceName": "test-service",
                            |   "microserviceType": "Frontend microservice",
                            |   "hasMongo": true,
                            |   "environments": [],
                            |   "zone": "public"
                            |}""".stripMargin

      stubFor(
        post("/create-app-configs")
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
       val payload = CreateAppConfigsForm(
         appConfigBase = false, appConfigDevelopment = false, appConfigQA = false, appConfigStaging = false, appConfigProduction = false
       )

       val expectedBody = s"""
                             |{
                             |   "microserviceName": "test-service",
                             |   "microserviceType": "Backend microservice",
                             |   "hasMongo": true,
                             |   "environments": [],
                             |   "zone": "protected"
                             |}""".stripMargin

       stubFor(
         post("/create-app-configs")
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
