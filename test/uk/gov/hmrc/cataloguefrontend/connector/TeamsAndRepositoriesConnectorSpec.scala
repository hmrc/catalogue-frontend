/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.connector

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.play.http.HeaderCarrierConverter

class TeamsAndRepositoriesConnectorSpec
  extends AnyWordSpec
     with Matchers
     with BeforeAndAfterEach
     with ScalaFutures
     with IntegrationPatience
     with GuiceOneAppPerSuite
     with WireMockSupport
     with TypeCheckedTripleEquals
     with OptionValues
     with EitherValues {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(
        Map(
          "microservice.services.teams-and-repositories.host" -> wireMockHost,
          "microservice.services.teams-and-repositories.port" -> wireMockPort,
          "metrics.jvm"            -> false
        ))
      .build()

  private lazy val teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector =
    app.injector.instanceOf[TeamsAndRepositoriesConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "lookUpLink" should {
    "return a Link if exists" in {
      stubFor(
        get(urlEqualTo("/api/jenkins-jobs/serviceA"))
          .willReturn(
            aResponse()
            .withBody(
              """
                { "jobs": [
                    {
                      "name": "serviceA",
                      "jenkinsURL": "http.jenkins/serviceA"
                    }
                  ]
                }
              """
          )
        )
      )

      val response = teamsAndRepositoriesConnector
        .lookupLatestBuildJobs("serviceA")(HeaderCarrierConverter.fromRequest(FakeRequest()))
        .futureValue

      response shouldBe JenkinsJobs(List(JenkinsJob("serviceA","http.jenkins/serviceA",None)))
    }
  }

}
