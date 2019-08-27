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
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws._
import uk.gov.hmrc.play.test.UnitSpec

class FrontendRouteWarningsPageSpec extends UnitSpec with GuiceOneServerPerSuite with WireMockEndpoints {

  override def fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.shutter-api.port"          -> endpointPort,
      "microservice.services.shutter-api.host"          -> host
    )
    .build()

  private[this] lazy val ws = app.injector.instanceOf[WSClient]

  override def beforeEach(): Unit = {
    super.beforeEach()
    serviceEndpoint(GET, "/shutter-api/development/frontend-route-warnings/abc-frontend", willRespondWith = (200, Some(emptyJson)))
    serviceEndpoint(GET, "/shutter-api/qa/frontend-route-warnings/abc-frontend"         , willRespondWith = (200, Some(emptyJson)))
    serviceEndpoint(GET, "/shutter-api/staging/frontend-route-warnings/abc-frontend"     , willRespondWith = (200, Some(emptyJson)))
    serviceEndpoint(GET, "/shutter-api/externalTest/frontend-route-warnings/abc-frontend", willRespondWith = (200, Some(emptyJson)))
    serviceEndpoint(GET, "/shutter-api/production/frontend-route-warnings/abc-frontend"  , willRespondWith = (200, Some(emptyJson)))
  }

  "The frontend route warnings page" should {
    "defaults to production if a bad environment is specified" in {
      val response = await(ws.url(s"http://localhost:$port/frontend-route-warnings/something/abc-frontend").get)
      response.status shouldBe 200
      response.body.contains("""<li id="tab-production" class="navbar-item active">""") shouldBe true
      response.body.contains("""There are no frontend-route warnings for this service in this environment""") shouldBe true
    }
    "shows the table with route warnings" in {
      serviceEndpoint(GET, "/shutter-api/development/frontend-route-warnings/abc-frontend", willRespondWith = (200, Some(abcWarnings)))

      val response = await(ws.url(s"http://localhost:$port/frontend-route-warnings/development/abc-frontend").get)
      response.status shouldBe 200
      response.body.contains("""<li id="tab-development" class="navbar-item active">""") shouldBe true
      response.body.contains("""LegacyErrorPageMisconfigured""") shouldBe true
    }
  }

  val emptyJson = "[]"

  val abcWarnings =
    """
      |[
      |{
      |"name": "LegacyErrorPageMisconfigured",
      |"message": "The legacy error_page configured '/shutter/abc/index.html' does not match the format '/shutter/abc-frontend/index.html'",
      |"consequence": "You will mostly likely get a 404 when shuttered as the error_page pointed to will not exist",
      |"ruleConfigurationURL": "conf/config.conf#L4602"
      |}
      |]
      |""".stripMargin

}
