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

package uk.gov.hmrc.cataloguefrontend

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.jsoup.Jsoup
import org.scalatest.BeforeAndAfter
import play.api.libs.ws._
import uk.gov.hmrc.cataloguefrontend.JsonData._
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class LibraryPageSpec extends UnitSpec with BeforeAndAfter with FakeApplicationBuilder {

  private[this] lazy val WS = app.injector.instanceOf[WSClient]

  override def beforeEach(): Unit = {
    super.beforeEach()
    serviceEndpoint(GET, "/reports/repositories", willRespondWith = (200, Some("[]")))
  }

  "A library page" should {
    "show the teams owning the service with github and ci links and info box" in {
      serviceEndpoint(GET, "/api/repositories/lib", willRespondWith = (200, Some(libraryData)))
      serviceEndpoint(GET, "/api/jenkins-url/lib", willRespondWith = (200, Some(jenkinsData)))

      val response = WS.url(s"http://localhost:$port/repositories/lib").get.futureValue
      response.status shouldBe 200
      response.body   should include(s"links on this page are automatically generated")
      response.body   should include(s"teamA")
      response.body   should include(s"teamB")
      response.body   should include(s"lib")
      response.body   should include(s"github.com")
      response.body   should include(s"http://jenkins/lib/")
      response.body   should not include "service1"
      response.body   should not include "service1"
      response.body   should not include "http://ser1/serv"
      response.body   should not include "http://ser2/serv"
    }

    "render dependencies with red, green, amber and grey colours" in {
      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith                      = (200, Some(libraryData)))
      serviceEndpoint(GET, "/api/service-dependencies/dependencies/service-name", willRespondWith = (200, None))

      val response = WS.url(s"http://localhost:$port/repositories/service-name").get.futureValue
      response.status shouldBe 200

      val document = Jsoup.parse(response.body)

      document.select("#platform-dependencies").size() shouldBe 1
    }
  }
}
