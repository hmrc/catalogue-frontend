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

package uk.gov.hmrc.cataloguefrontend

import com.github.tomakehurst.wiremock.client.WireMock.{postRequestedFor, urlPathEqualTo}
import com.github.tomakehurst.wiremock.http.RequestMethod._
import play.api.libs.ws.{readableAsString, writeableOf_urlEncodedForm}
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.PrototypeStatus
import uk.gov.hmrc.cataloguefrontend.jsondata.{JsonData, TeamsAndRepositoriesJsonData}
import uk.gov.hmrc.cataloguefrontend.util.DateHelper._
import uk.gov.hmrc.cataloguefrontend.test.{FakeApplicationBuilder, UnitSpec}

class PrototypePageSpec
  extends UnitSpec
  with FakeApplicationBuilder {

  override def beforeEach(): Unit =
    super.beforeEach()
    setupAuthEndpoint()
    serviceEndpoint(GET, "/reports/repositories", willRespondWith = (200, Some("[]")))

  private def setupPrototypeStatusEndpoint(prototype: String, responseCode: Int, status: PrototypeStatus) =
    serviceEndpoint(
      POST,
      "/get-prototype-details",
      willRespondWith = (responseCode, Some(s"""{ "success": true, "message": "Successfully retrieved status", "details": { "prototype": "$prototype", "status": "${status.asString}" } }""")),
      givenJsonBody = Some(s"""{ "prototype": "$prototype" }""")
    )

  "A prototype page" should {
    "show the teams owning the prototype" in {
      setupEnableBranchProtectionAuthEndpoint()
      setupPrototypeStatusEndpoint("2fa-prototype", 200, PrototypeStatus.Running)
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype"             , willRespondWith = (200, Some(JsonData.prototypeDetailsData)))
      serviceEndpoint(GET, "/api/repository"                                , willRespondWith = (200, Some(JsonData.emptyList)))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype/jenkins-jobs", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.jenkinsBuildData)))

      val response = wsClient.url(s"http://localhost:$port/repositories/2fa-prototype").withAuthToken("Token token").get().futureValue

      response.status shouldBe 200
      response.body   should include("links on this page are automatically generated")
      response.body   should include("Designers")
      response.body   should include("CATO")
      response.body   should include("GitHub")
      response.body   should include("https://github.com/HMRC/2fa-prototype")
      response.body   should include("some description")

      response.body should include(JsonData.createdAt.displayFormat)
      response.body should include(JsonData.lastActiveAt.displayFormat)
    }

    "show the reset password form and stop prototype button when logged in user has permission" in {
      setupChangePrototypePasswordAuthEndpoint(hasAuth = true)
      setupPrototypeStatusEndpoint("2fa-prototype", 200, PrototypeStatus.Running)
      serviceEndpoint(GET, "/api/v2/repositories"                           , willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.repositoryData("2fa-prototype"))))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype"             , willRespondWith = (200, Some(JsonData.prototypeDetailsData)))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype/jenkins-jobs", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.jenkinsBuildData)))
      serviceEndpoint(GET, "/pr-commenter/repositories/2fa-prototype/report", willRespondWith = (404, Some("")))

      val response = wsClient
        .url(s"http://localhost:$port/repositories/2fa-prototype")
        .withAuthToken("Token token")
        .get()
        .futureValue

      response.status shouldBe 200
      response.body   should include("reset-password-enabled")
      response.body   should include("stop-prototype-enabled")
    }

    "not show the reset password form when user does not have permission" in {
      setupChangePrototypePasswordAuthEndpoint(hasAuth = false)
      setupPrototypeStatusEndpoint("2fa-prototype", 200, PrototypeStatus.Running)
      serviceEndpoint(GET, "/api/v2/repositories"                           , willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.repositoryData("2fa-prototype"))))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype"             , willRespondWith = (200, Some(JsonData.prototypeDetailsData)))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype/jenkins-jobs", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.jenkinsBuildData)))
      serviceEndpoint(GET, "/pr-commenter/repositories/2fa-prototype/report", willRespondWith = (404, Some("")))

      val response = wsClient
        .url(s"http://localhost:$port/repositories/2fa-prototype")
        .withAuthToken("Token token")
        .get()
        .futureValue

      response.status shouldBe 200
      response.body should include("reset-password-disabled")
      response.body should include("stop-prototype-disabled")
    }

    "not show the reset password form when the prototype does not have a status of Running" in {
      setupChangePrototypePasswordAuthEndpoint(hasAuth = true)
      setupPrototypeStatusEndpoint("2fa-prototype", 200, PrototypeStatus.Stopped)
      serviceEndpoint(GET, "/api/v2/repositories"                           , willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.repositoryData("2fa-prototype"))))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype"             , willRespondWith = (200, Some(JsonData.prototypeDetailsData)))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype/jenkins-jobs", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.jenkinsBuildData)))
      serviceEndpoint(GET, "/pr-commenter/repositories/2fa-prototype/report", willRespondWith = (404, Some("")))

      val response = wsClient
        .url(s"http://localhost:$port/repositories/2fa-prototype")
        .withAuthToken("Token token")
        .get()
        .futureValue

      response.status shouldBe 200
      response.body should include("reset-password-disabled")
      response.body should include("To reset the password, the prototype must have a status of Running.")
    }

    "display success message when password changed successfully" in {
      setupChangePrototypePasswordAuthEndpoint(hasAuth = true)
      setupPrototypeStatusEndpoint("2fa-prototype", 200, PrototypeStatus.Running)
      serviceEndpoint(GET, "/api/v2/repositories"                           , willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.repositoryData("2fa-prototype"))))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype"             , willRespondWith = (200, Some(JsonData.prototypeDetailsData)))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype/jenkins-jobs", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.jenkinsBuildData)))
      serviceEndpoint(GET, "/pr-commenter/repositories/2fa-prototype/report", willRespondWith = (404, Some("")))

      val expectedMsg: String = "A password reset has been requested. This may take some time to take effect."

      serviceEndpoint(
        POST,
        "/change-prototype-password",
        willRespondWith = (200, Some(s"""{ "success": true, "message": "$expectedMsg" }""")),
        givenJsonBody = Some("""{ "repositoryName": "2fa-prototype", "password": "password" }""")
      )

      val response = wsClient
        .url(s"http://localhost:$port/prototype/2fa-prototype/change-password")
        .withAuthToken("Token token")
        .withHttpHeaders("Csrf-Token" -> "nocheck", "Content-Type" -> "application/x-www-form-urlencoded")
        .post(Map("password" -> Seq("password")))
        .futureValue

      response.status shouldBe 200
      response.body should include("password-change-success-msg")
      response.body should include(expectedMsg)

      wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/change-prototype-password")))
    }

    "display error message when password change failed downstream" in {
      setupChangePrototypePasswordAuthEndpoint(hasAuth = true)
      setupPrototypeStatusEndpoint("2fa-prototype", 200, PrototypeStatus.Running)
      serviceEndpoint(GET, "/api/v2/repositories"                           , willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.repositoryData("2fa-prototype"))))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype"             , willRespondWith = (200, Some(JsonData.prototypeDetailsData)))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype/jenkins-jobs", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.jenkinsBuildData)))
      serviceEndpoint(GET, "/pr-commenter/repositories/2fa-prototype/report", willRespondWith = (404, Some("")))

      val expectedError: String = "generic password change error"

      serviceEndpoint(
        POST,
        "/change-prototype-password",
        willRespondWith = (400, Some(s"""{ "code": "INVALID_PASSWORD", "message": "$expectedError" }""")),
        givenJsonBody = Some("""{ "repositoryName": "2fa-prototype", "password": "password" }""")
      )

      val response = wsClient
        .url(s"http://localhost:$port/prototype/2fa-prototype/change-password")
        .withAuthToken("Token token")
        .withHttpHeaders("Csrf-Token" -> "nocheck", "Content-Type" -> "application/x-www-form-urlencoded")
        .post(Map("password" -> Seq("password")))
        .futureValue

      response.status shouldBe 400
      response.body should include(expectedError)
    }
  }
}
