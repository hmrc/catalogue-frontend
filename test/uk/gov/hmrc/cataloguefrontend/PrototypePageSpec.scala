/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.DateHelper._
import uk.gov.hmrc.cataloguefrontend.JsonData._
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class PrototypePageSpec
  extends UnitSpec
  with FakeApplicationBuilder {

  override def beforeEach(): Unit = {
    super.beforeEach()
    setupAuthEndpoint()
    serviceEndpoint(GET, "/reports/repositories", willRespondWith = (200, Some("[]")))
  }

  "A prototype page" should {
    "show the teams owning the prototype" in {
      setupEnableBranchProtectionAuthEndpoint()
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype", willRespondWith = (200, Some(prototypeDetailsData)))
      serviceEndpoint(GET, "/api/jenkins-url/2fa-prototype"    , willRespondWith = (200, Some(jenkinsData)))

      val response = wsClient.url(s"http://localhost:$port/repositories/2fa-prototype").withAuthToken("Token token").get().futureValue

      response.status shouldBe 200
      response.body   should include("links on this page are automatically generated")
      response.body   should include("Designers")
      response.body   should include("CATO")
      response.body   should include("GitHub")
      response.body   should include("https://github.com/HMRC/2fa-prototype")
      response.body   should include("some description")

      response.body should include(createdAt.displayFormat)
      response.body should include(lastActiveAt.displayFormat)
    }

    "show the reset password form when logged in user has permission" in {
      setupChangePrototypePasswordAuthEndpoint(hasAuth = true)
      serviceEndpoint(GET, "/api/v2/repositories", willRespondWith = (200, Some(JsonData.repositoryData("2fa-prototype"))))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype", willRespondWith = (200, Some(prototypeDetailsData)))
      serviceEndpoint(GET, "/api/jenkins-jobs/2fa-prototype", willRespondWith = (200, Some(jenkinsBuildData)))
      serviceEndpoint(GET, "/pr-commenter/repositories/2fa-prototype/report", willRespondWith = (404, Some("")))

      val response = wsClient
        .url(s"http://localhost:$port/repositories/2fa-prototype")
        .withAuthToken("Token token")
        .get()
        .futureValue

      response.status shouldBe 200
      response.body   should include("reset-password-enabled")
    }

    "not show the reset password form when user does not have permission" in {
      setupChangePrototypePasswordAuthEndpoint(hasAuth = false)
      serviceEndpoint(GET, "/api/v2/repositories", willRespondWith = (200, Some(JsonData.repositoryData("2fa-prototype"))))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype", willRespondWith = (200, Some(prototypeDetailsData)))
      serviceEndpoint(GET, "/api/jenkins-jobs/2fa-prototype", willRespondWith = (200, Some(jenkinsBuildData)))
      serviceEndpoint(GET, "/pr-commenter/repositories/2fa-prototype/report", willRespondWith = (404, Some("")))

      val response = wsClient
        .url(s"http://localhost:$port/repositories/2fa-prototype")
        .withAuthToken("Token token")
        .get()
        .futureValue

      response.status shouldBe 200
      response.body should include("reset-password-disabled")
    }

    "display success message when password changed successfully" in {
      setupChangePrototypePasswordAuthEndpoint(hasAuth = true)
      serviceEndpoint(GET, "/api/v2/repositories", willRespondWith = (200, Some(JsonData.repositoryData("2fa-prototype"))))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype", willRespondWith = (200, Some(prototypeDetailsData)))
      serviceEndpoint(GET, "/api/jenkins-jobs/2fa-prototype", willRespondWith = (200, Some(jenkinsBuildData)))
      serviceEndpoint(GET, "/pr-commenter/repositories/2fa-prototype/report", willRespondWith = (404, Some("")))

      val expectedMsg: String = "password change success"

      serviceEndpoint(
        POST,
        "/v1/SetHerokuPrototypePassword",
        willRespondWith = (200, Some(s"""{ "success": true, "message": "$expectedMsg" }""")),
        givenJsonBody = Some("""{ "app_name": "2fa-prototype", "password": "password" }""")
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

      wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/v1/SetHerokuPrototypePassword")))
    }

    "display error message when password change failed downstream" in {
      setupChangePrototypePasswordAuthEndpoint(hasAuth = true)
      serviceEndpoint(GET, "/api/v2/repositories", willRespondWith = (200, Some(JsonData.repositoryData("2fa-prototype"))))
      serviceEndpoint(GET, "/api/v2/repositories/2fa-prototype", willRespondWith = (200, Some(prototypeDetailsData)))
      serviceEndpoint(GET, "/api/jenkins-jobs/2fa-prototype", willRespondWith = (200, Some(jenkinsBuildData)))
      serviceEndpoint(GET, "/pr-commenter/repositories/2fa-prototype/report", willRespondWith = (404, Some("")))

      val expectedError: String = "generic password change error"

      serviceEndpoint(
        POST,
        "/v1/SetHerokuPrototypePassword",
        willRespondWith = (400, Some(s"""{ "code": "INVALID_PASSWORD", "message": "$expectedError" }""")),
        givenJsonBody = Some("""{ "app_name": "2fa-prototype", "password": "password" }""")
      )

      val response = wsClient
        .url(s"http://localhost:$port/prototype/2fa-prototype/change-password")
        .withAuthToken("Token token")
        .withHttpHeaders("Csrf-Token" -> "nocheck", "Content-Type" -> "application/x-www-form-urlencoded")
        .post(Map("password" -> Seq("password")))
        .futureValue

      response.status shouldBe 400
      response.body should include("password-change-error-msg")
      response.body should include(expectedError)
    }
  }
}
