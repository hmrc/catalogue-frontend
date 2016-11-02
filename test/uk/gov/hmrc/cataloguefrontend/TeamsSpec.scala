/*
 * Copyright 2016 HM Revenue & Customs
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
import org.scalatest._
import org.scalatestplus.play.OneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WS
import uk.gov.hmrc.play.test.UnitSpec

class TeamsSpec extends UnitSpec with BeforeAndAfter with OneServerPerSuite with WireMockEndpoints {


  implicit override lazy val app = new GuiceApplicationBuilder().configure (
    Map(
    "microservice.services.teams-and-services.port" -> endpointPort,
    "microservice.services.teams-and-services.host" -> host,
      "play.ws.ssl.loose.acceptAnyCertificate" -> true,
      "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler"
  )).build()


  "Teams list" should {

    "show a list of teams" in  {

      serviceEndpoint(GET, "/api/teams", willRespondWith = (200, Some(
        """["teamA", "teamB", "TeamC"]"""
      )), extraHeaders = Map("X-Cache-Timestamp" -> "anything"))

      val response = await(WS.url(s"http://localhost:$port/teams").get)

      response.status shouldBe 200
      response.body should include(s"Last updated at: anything")
      response.body should include("""<li><a href="/teams/teamA">teamA</a></li>""")
    }
  }
}
