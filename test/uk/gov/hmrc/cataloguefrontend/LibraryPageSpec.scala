/*
 * Copyright 2019 HM Revenue & Customs
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
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws._
import uk.gov.hmrc.play.test.UnitSpec
import JsonData._

class LibraryPageSpec extends UnitSpec with BeforeAndAfter with GuiceOneServerPerSuite with WireMockEndpoints {

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.teams-and-repositories.host" -> host,
        "microservice.services.teams-and-repositories.port" -> endpointPort,
        "microservice.services.indicators.port"             -> endpointPort,
        "microservice.services.indicators.host"             -> host,
        "microservice.services.service-dependencies.host"   -> host,
        "microservice.services.service-dependencies.port"   -> endpointPort,
        "microservice.services.leak-detection.port"         -> endpointPort,
        "microservice.services.leak-detection.host"         -> host,
        "play.http.requestHandler"                          -> "play.api.http.DefaultHttpRequestHandler",
        "metrics.jvm"                                       -> false
      )
      .build()

  private[this] lazy val WS = app.injector.instanceOf[WSClient]

  override def beforeEach(): Unit = {
    super.beforeEach()
    serviceEndpoint(GET, "/reports/repositories", willRespondWith = (200, Some("[]")))
  }

  "A library page" should {

    "return a 404 when teams and services returns a 404" in {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (404, None))

      val response = await(WS.url(s"http://localhost:$port/services/serv").get)
      response.status shouldBe 404
    }

    "return a 404 when a Service is viewed as a Library" in {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (200, Some(serviceData)))

      val response = await(WS.url(s"http://localhost:$port/library/serv").get)
      response.status shouldBe 404
    }

    "show the teams owning the service with github and ci links and info box" in {

      serviceEndpoint(GET, "/api/repositories/lib", willRespondWith = (200, Some(libraryData)))
      serviceEndpoint(GET, "/api/jenkins-url/lib", willRespondWith = (200, Some(jenkinsData)))

      val response = await(WS.url(s"http://localhost:$port/library/lib").get)
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

    "Render dependencies with red, green, amber and grey colours" in {

      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith                      = (200, Some(libraryData)))
      serviceEndpoint(GET, "/api/service-dependencies/dependencies/service-name", willRespondWith = (200, None))

      val response = await(WS.url(s"http://localhost:$port/library/service-name").get)

      val document = Jsoup.parse(response.body)

      document.select("#platform-dependencies").size() shouldBe 1

    }

  }


}
