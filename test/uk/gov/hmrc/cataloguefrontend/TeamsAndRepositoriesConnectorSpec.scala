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
import org.scalatest.BeforeAndAfter
import org.scalatestplus.play.OneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeHeaders
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class TeamsAndRepositoriesConnectorSpec extends UnitSpec with BeforeAndAfter with OneServerPerSuite with WireMockEndpoints {

  implicit override lazy val app = new GuiceApplicationBuilder().configure (
    "microservice.services.indicators.port" -> endpointPort,
    "microservice.services.indicators.host" -> host,
    "microservice.services.teams-and-services.host" -> host,
    "microservice.services.teams-and-services.port" -> endpointPort,
    "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler"
  ).build()



  "teamsByService" should {

    "return a list of team information for each given service" in {

      serviceEndpoint(POST, "/api/services?teamDetails=true", willRespondWith = (200, Some(
        """
          |	{
          |		"serviceA": ["teamA","teamB"],
          |		"serviceB": ["teamA"]
          |	}
          | """.stripMargin
      )), givenJsonBody = Some("[\"serviceA\",\"serviceB\"]"))

      val response = await(TeamsAndServicesConnector.teamsByService(Seq("serviceA","serviceB"))(HeaderCarrier.fromHeadersAndSession(FakeHeaders())))

      response.data.size shouldBe 2
      response.data("serviceA") shouldBe Seq("teamA","teamB")
      response.data("serviceB") shouldBe Seq("teamA")

    }

  }
}
