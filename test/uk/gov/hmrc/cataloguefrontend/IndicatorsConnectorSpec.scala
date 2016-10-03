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
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers, TestData}
import org.scalatestplus.play.OneServerPerTest
import play.api.test.{FakeApplication, FakeHeaders}
import uk.gov.hmrc.cataloguefrontend.JsonData.deploymentThroughputData
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

import scala.concurrent.Future

class IndicatorsConnectorSpec extends FunSpec with WireMockEndpoints with OneServerPerTest with Matchers with TypeCheckedTripleEquals with ScalaFutures {

  override def newAppForTest(testData: TestData): FakeApplication = new FakeApplication(
    additionalConfiguration = Map(
      "microservice.services.indicators.port" -> endpointPort
    ))

  describe("IndicatorsConnector") {
    it("should convert the DeploymentsMetricResult to DeploymentIndicators") {
      serviceEndpoint(GET, "/api/indicators/service/serv/deployments", willRespondWith = (200, Some(deploymentThroughputData)))

       val deploymentIndicatorsForService: Future[Option[DeploymentIndicators]] =
         IndicatorsConnector.deploymentIndicatorsForService("serv")(HeaderCarrier.fromHeadersAndSession(FakeHeaders()))

      deploymentIndicatorsForService.futureValue should not be(None)
      deploymentIndicatorsForService.futureValue.get.throughput.size should be(3)
      deploymentIndicatorsForService.futureValue.get.stability.size should be(3)

    }
  }

}
