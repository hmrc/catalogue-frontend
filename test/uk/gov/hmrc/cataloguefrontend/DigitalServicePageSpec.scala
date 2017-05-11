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

import java.time.ZoneOffset

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest._
import org.scalatestplus.play.OneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WS
import uk.gov.hmrc.cataloguefrontend.DateHelper._
import uk.gov.hmrc.cataloguefrontend.JsonData._
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.TeamMember
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.JavaConversions._
import scala.io.Source

class DigitalServicePageSpec extends UnitSpec with BeforeAndAfter with OneServerPerSuite with WireMockEndpoints {

  def asDocument(html: String): Document = Jsoup.parse(html)

  val umpFrontPageUrl = "http://some.ump.fontpage.com"

  implicit override lazy val app = new GuiceApplicationBuilder().configure (
    "microservice.services.teams-and-services.host" -> host,
    "microservice.services.teams-and-services.port" -> endpointPort,
    "microservice.services.indicators.port" -> endpointPort,
    "microservice.services.indicators.host" -> host,
    "microservice.services.user-management.url" -> endpointMockUrl,
    "usermanagement.portal.url" -> "http://usermanagement/link",
    "microservice.services.user-management.frontPageUrl" -> umpFrontPageUrl,
    "play.ws.ssl.loose.acceptAnyCertificate" -> true,
    "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler").build()


  val digitalServiceName = "digital-service-a"

  "DigitalService page" should {

    "show a list of libraries, services, prototypes and repositories" in {
      serviceEndpoint(GET, s"/api/digital-services/$digitalServiceName", willRespondWith = (200, Some(
s"""{
  |  "name": "$digitalServiceName",
  |  "lastUpdatedAt": 1494240869000,
  |  "repositories": [
  |    {
  |      "name": "A",
  |      "createdAt": 1456326530000,
  |      "lastUpdatedAt": 1494240809000,
  |      "repoType": "Service"
  |    },
  |    {
  |      "name": "B",
  |      "createdAt": 1491916469000,
  |      "lastUpdatedAt": 1494240869000,
  |      "repoType": "Prototype"
  |    },
  |    {
  |      "name": "C",
  |      "createdAt": 1454669716000,
  |      "lastUpdatedAt": 1494240838000,
  |      "repoType": "Library"
  |    },
  |    {
  |      "name": "D",
  |      "createdAt": 1454669716000,
  |      "lastUpdatedAt": 1494240838000,
  |      "repoType": "Other"
  |    }
  |  ]
  |}""".stripMargin
      )), extraHeaders = Map("X-Cache-Timestamp" -> "Fri, 14 Oct 1983 10:03:23 GMT"))

      val response = await(WS.url(s"http://localhost:$port/digital-service/$digitalServiceName").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: 14 Oct 1983 10:03")

      response.body should include("""<a href="/service/A">A</a>""")
      response.body should include("""<a href="/prototype/B">B</a>""")
      response.body should include("""<a href="/library/C">C</a>""")
      response.body should include("""<a href="/repositories/D">D</a>""")

    }

    "show '(None)' if no timestamp is found" in {

      serviceEndpoint(GET, s"/api/digital-services/$digitalServiceName", willRespondWith = (200, Some(
        s"""{
          |   "name":"$digitalServiceName",
          |   "lastUpdatedAt": 1494240869000,
          |   "repositories":[]
          | }""".stripMargin
      )))

      val response = await(WS.url(s"http://localhost:$port/digital-service/$digitalServiceName").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: (None)")

    }

    "show a message if no services are found" in {

      serviceEndpoint(GET, s"/api/digital-services/$digitalServiceName", willRespondWith = (200, Some(
        s"""{
          |   "name":"$digitalServiceName",
          |   "lastUpdatedAt":12345,
          |   "repositories":[]
          | }""".stripMargin

      )), extraHeaders = Map("X-Cache-Timestamp" -> "Fri, 14 Oct 1983 10:03:23 GMT"))

      val response = await(WS.url(s"http://localhost:$port/digital-service/$digitalServiceName").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: 14 Oct 1983 10:03")
      response.body should include(ViewMessages.noRepoOfTypeForDigitalService("service"))
      response.body should include(ViewMessages.noRepoOfTypeForDigitalService("library"))
      response.body should include(ViewMessages.noRepoOfTypeForDigitalService("prototype"))
      response.body should include(ViewMessages.noRepoOfTypeForDigitalService("other"))
    }
  }

  def mockHttpApiCall(url: String, jsonResponseFile: String, httpCodeToBeReturned: Int = 200): String = {

    val json = readFile(jsonResponseFile)

    serviceEndpoint(
      method = GET,
      url = url,
      willRespondWith = (httpCodeToBeReturned, Some(json)),
      extraHeaders = Map("X-Cache-Timestamp" -> "Fri, 14 Oct 1983 10:03:23 GMT"))

    json
  }

  def readFile(jsonFilePath: String): String = {
    Source.fromURL(getClass.getResource(jsonFilePath)).getLines().mkString("\n")
  }
}
