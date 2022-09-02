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
      response.body   should include("Github.com")
      response.body   should include("https://github.com/HMRC/2fa-prototype")
      response.body   should include("some description")

      response.body should include(createdAt.displayFormat)
      response.body should include(lastActiveAt.displayFormat)
    }
  }
}
