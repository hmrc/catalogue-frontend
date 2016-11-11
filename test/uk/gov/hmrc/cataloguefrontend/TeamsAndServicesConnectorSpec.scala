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

import java.time.format.DateTimeFormatter
import java.util.Date

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers, OptionValues}
import org.scalatestplus.play.OneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeHeaders
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.cataloguefrontend.JsonData._

class TeamsAndServicesConnectorSpec extends FunSpec with Matchers  with OneAppPerSuite with TypeCheckedTripleEquals with WireMockEndpoints with ScalaFutures with OptionValues{

  override lazy implicit val app = new GuiceApplicationBuilder().configure(
    "microservice.services.teams-and-services.port" -> endpointPort,
    "microservice.services.teams-and-services.host" -> host
  ).build()

  import DateHelper._

  describe("repositoryDetails") {
    it("should convert the json string to RepositoryDetails") {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (200, Some(JsonData.serviceDetailsData)))

      val responseData: RepositoryDetails =
        TeamsAndServicesConnector
          .repositoryDetails("serv")(HeaderCarrier.fromHeadersAndSession(FakeHeaders()))
          .futureValue
          .value
          .data

      responseData.name shouldBe "serv"
      responseData.description shouldBe "some description"
      responseData.createdAt shouldBe createdAt
      responseData.lastActive shouldBe lastActiveAt
      responseData.teamNames should===(Seq("teamA", "teamB"))
      responseData.githubUrls should===(Seq(Link("github", "github.com", "https://github.com/hmrc/serv")))
      responseData.ci should===(Seq(Link("open1", "open 1", "http://open1/serv"), Link("open2", "open 2", "http://open2/serv")))
      responseData.environments should===(Some(Seq(
        Environment(
          "env1",
          Seq(
            Link("ser1", "service1", "http://ser1/serv"),
            Link("ser2", "service2", "http://ser2/serv")
        )),
        Environment(
          "env2",
          Seq(
            Link("ser1", "service1", "http://ser1/serv"),
            Link("ser2", "service2", "http://ser2/serv")
          )
        ))))

      responseData.repoType should===(RepoType.Deployable)
    }
  }


}
