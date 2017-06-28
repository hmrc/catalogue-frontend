/*
 * Copyright 2017 HM Revenue & Customs
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
import org.scalatestplus.play.OneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WS
import uk.gov.hmrc.cataloguefrontend.DateHelper._
import uk.gov.hmrc.cataloguefrontend.JsonData._
import uk.gov.hmrc.play.test.UnitSpec


class PrototypePageSpec extends UnitSpec with OneServerPerSuite with WireMockEndpoints {

  implicit override lazy val app = new GuiceApplicationBuilder().configure(
    "microservice.services.teams-and-services.port" -> endpointPort,
    "microservice.services.teams-and-services.host" -> host).build()

  "A prototype page" should {

    "show the teams owning the prototype" in {

      serviceEndpoint(GET, "/api/repositories/2fa-prototype", willRespondWith = (200, Some(prototypeDetailsData)))

      val response = await(WS.url(s"http://localhost:$port/prototype/2fa-prototype").get)
      response.status shouldBe 200
      response.body should include("links on this page are automatically generated")
      response.body should include("Designers")
      response.body should include("CATO")
      response.body should include("Github Enterprise")
      response.body should include("https://github.gov.uk/HMRC/2fa-prototype")
      response.body should include("some description")

      response.body should include(createdAt.displayFormat)
      response.body should include(lastActiveAt.displayFormat)
    }
  }

}
