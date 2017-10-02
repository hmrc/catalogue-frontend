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
import com.sun.jmx.snmp.defaults.DefaultPaths
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.{FunSpec, Matchers, TestData}
import org.scalatestplus.play.{OneAppPerSuite, OneAppPerTest, OneServerPerTest}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.{FakeApplication, FakeHeaders}
import uk.gov.hmrc.cataloguefrontend.JsonData.{deploymentThroughputData, jobExecutionTimeData}
import uk.gov.hmrc.cataloguefrontend.connector.{DeploymentIndicators, IndicatorsConnector, JobMetricDataPoint}

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

class IndicatorsConnectorSpec extends FunSpec with WireMockEndpoints with OneAppPerTest with Matchers with TypeCheckedTripleEquals with ScalaFutures{

  implicit val defaultPatienceConfig = new PatienceConfig(Span(200, Millis), Span(15, Millis))



  override def newAppForTest(testData: TestData): Application = {
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(
      "microservice.services.indicators.port" -> endpointPort,
      "microservice.services.indicators.host" -> host
    ).build()
  }

  lazy val indicatorsConnector = app.injector.instanceOf[IndicatorsConnector]

  describe("IndicatorsConnector") {
    it("should convert the DeploymentsMetricResult to DeploymentIndicators for a service") {
      serviceEndpoint(GET, "/api/indicators/service/serv/deployments", willRespondWith = (200, Some(deploymentThroughputData)))

      val deploymentIndicatorsForService: Future[Option[DeploymentIndicators]] =
        indicatorsConnector.deploymentIndicatorsForService("serv")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))

      deploymentIndicatorsForService.futureValue should not be None
      deploymentIndicatorsForService.futureValue.get.throughput.size should be(3)
      deploymentIndicatorsForService.futureValue.get.stability.size should be(3)
    }

    it("should convert the DeploymentsMetricResult to DeploymentIndicators for a team") {
      serviceEndpoint(GET, "/api/indicators/team/teamA/deployments", willRespondWith = (200, Some(deploymentThroughputData)))

      val deploymentIndicatorsForService: Future[Option[DeploymentIndicators]] =
        indicatorsConnector.deploymentIndicatorsForTeam("teamA")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))

      deploymentIndicatorsForService.futureValue should not be None
      deploymentIndicatorsForService.futureValue.get.throughput.size should be(3)
      deploymentIndicatorsForService.futureValue.get.stability.size should be(3)

    }

    it("should get a sequence of JobExecutionTimeDataPoints for a repository") {
      serviceEndpoint(GET, "/api/indicators/repository/reponame/builds", willRespondWith = (200, Some(jobExecutionTimeData)))

      val buildIndicatorsForRepository: Future[Option[Seq[JobMetricDataPoint]]] =
        indicatorsConnector.buildIndicatorsForRepository("reponame")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))

      buildIndicatorsForRepository.futureValue should not be None
      buildIndicatorsForRepository.futureValue.get.size should be(12)

    }
  }

}
