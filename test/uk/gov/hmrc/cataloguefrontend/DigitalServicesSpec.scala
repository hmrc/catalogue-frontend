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

import com.github.tomakehurst.wiremock.http.RequestMethod.GET
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.OneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WS
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec

class DigitalServicesSpec extends UnitSpec with BeforeAndAfter with OneServerPerSuite with WireMockEndpoints {

  implicit override lazy val app = new GuiceApplicationBuilder().configure(
    Map(
      "microservice.services.teams-and-services.port" -> endpointPort,
      "microservice.services.teams-and-services.host" -> host,
      "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler")
  ).build()


  def asDocument(html: String): Document = Jsoup.parse(html)

  "Digital Service list" should {

    "show a list of all digital services" in {

      serviceEndpoint(GET, "/api/digital-services", willRespondWith = (200, Some(
        JsonData.digitalServiceNamesData
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Fri, 14 Oct 1983 10:03:23 GMT"))

      val response = await(WS.url(s"http://localhost:$port/digital-services").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: 14 Oct 1983 10:03")
      response.body should include("<h1>Digital Services</h1>")

      val document = asDocument(response.body)

      document.select("#row0_name").select("td a").text() shouldBe "digital-service-1"
      document.select("#row0_name").select("td a[href]").attr("href") shouldBe "/digital-service/digital-service-1"

      document.select("#row1_name").select("td a").text() shouldBe "digital-service-2"
      document.select("#row1_name").select("td a[href]").attr("href") shouldBe "/digital-service/digital-service-2"

      document.select("#row2_name").select("td a").text() shouldBe "digital-service-3"
      document.select("#row2_name").select("td a[href]").attr("href") shouldBe "/digital-service/digital-service-3"
    }
  }
}
