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

import com.github.tomakehurst.wiremock.http.RequestMethod.GET
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.BeforeAndAfter
import play.api.libs.ws._
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class DigitalServicesSpec extends UnitSpec with BeforeAndAfter with FakeApplicationBuilder {

  private[this] lazy val WS = app.injector.instanceOf[WSClient]

  def asDocument(html: String): Document = Jsoup.parse(html)

  "Digital Service list" should {

    "show a list of all digital services" in {

      serviceEndpoint(
        GET,
        "/api/digital-services",
        willRespondWith = (
          200,
          Some(
            JsonData.digitalServiceNamesData
          )))

      val response = WS.url(s"http://localhost:$port/digital-services").get.futureValue

      response.status shouldBe 200
      response.body   should include("<h1>Digital Services</h1>")

      val document = asDocument(response.body)

      document.select("#row0_name").select("td a").text()             shouldBe "digital-service-1"
      document.select("#row0_name").select("td a[href]").attr("href") shouldBe "/digital-service/digital-service-1"

      document.select("#row1_name").select("td a").text()             shouldBe "digital-service-2"
      document.select("#row1_name").select("td a[href]").attr("href") shouldBe "/digital-service/digital-service-2"

      document.select("#row2_name").select("td a").text()             shouldBe "digital-service-3"
      document.select("#row2_name").select("td a[href]").attr("href") shouldBe "/digital-service/digital-service-3"
    }
  }
}
