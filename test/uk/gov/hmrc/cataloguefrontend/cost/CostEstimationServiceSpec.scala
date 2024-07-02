/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.cost

import com.typesafe.config.ConfigFactory
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.connector.ResourceUsageConnector
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.cataloguefrontend.util.Parser
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.concurrent.IntegrationPatience

final class CostEstimationServiceSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with MockitoSugar {

  given HeaderCarrier = HeaderCarrier()

  private val mockResourceUsageConnector =
    mock[ResourceUsageConnector]

  "Service" should {
    "produce a cost estimate for a service in all environments in which it's deployed" in {
      val serviceName = ServiceName("some-service")

      val stubs =
        Seq(
          DeploymentConfig(ServiceName("test1"), DeploymentSize(3, 1),  environment = Environment.Development,  zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(ServiceName("test2"), DeploymentSize(3, 1),  environment = Environment.Integration,  zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(ServiceName("test3"), DeploymentSize(3, 1),  environment = Environment.QA,           zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(ServiceName("test4"), DeploymentSize(3, 1),  environment = Environment.Staging,      zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(ServiceName("test5"), DeploymentSize(3, 1),  environment = Environment.ExternalTest, zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty),
          DeploymentConfig(ServiceName("test6"), DeploymentSize(11, 3), environment = Environment.Production,   zone = Zone.Protected, envVars = Map.empty, jvm = Map.empty)
        )

      val serviceConfigsConnector =
        stubConfigConnector(
          service = serviceName,
          stubs   = stubs
        )

      val costEstimationService =
        CostEstimationService(serviceConfigsConnector, mockResourceUsageConnector, costEstimateConfig)

      val costEstimate = costEstimationService.estimateServiceCost(serviceName).futureValue

      costEstimate.slotsByEnv shouldBe List(
        Environment.Integration  -> TotalSlots(3),
        Environment.Development  -> TotalSlots(3),
        Environment.QA           -> TotalSlots(3),
        Environment.Staging      -> TotalSlots(3),
        Environment.ExternalTest -> TotalSlots(3),
        Environment.Production   -> TotalSlots(33)
      )
      costEstimate.totalSlots                             shouldBe TotalSlots(48)
      costEstimate.totalYearlyCostGbp(costEstimateConfig) shouldBe 48
      costEstimate.chart.render shouldBe """[['Environment', 'Estimated Cost']""" +
        """,['Integration', { v: 3.0, f: "£3.00 (slots = 3)" }]""" +
        """,['Development', { v: 3.0, f: "£3.00 (slots = 3)" }]""" +
        """,['QA', { v: 3.0, f: "£3.00 (slots = 3)" }]""" +
        """,['Staging', { v: 3.0, f: "£3.00 (slots = 3)" }]""" +
        """,['External Test', { v: 3.0, f: "£3.00 (slots = 3)" }]""" +
        """,['Production', { v: 33.0, f: "£33.00 (slots = 33)" }]]"""
    }

    "produce a cost estimate of zero for a service which is not deployed in a requested environment" in {
      val serviceName = ServiceName("some-service")

      val serviceConfigsConnector =
        stubConfigConnector(
          service = serviceName,
          stubs   = Seq.empty
        )

      val costEstimationService =
        CostEstimationService(serviceConfigsConnector, mockResourceUsageConnector, costEstimateConfig)

      val costEstimate = costEstimationService.estimateServiceCost(serviceName).futureValue

      costEstimate.slotsByEnv                             shouldBe List.empty
      costEstimate.totalSlots                             shouldBe TotalSlots(0)
      costEstimate.totalYearlyCostGbp(costEstimateConfig) shouldBe 0
    }
  }

  "Zone" should {
    "parse the valid zone" when {
      "it exists" in {
        val zone = "public"

        val result = Parser[Zone].parse(zone)

        result shouldBe Right(Zone.Public)
      }
    }
    "return an error message" when {
      "zone doesn't exists" in {
        val invalidZone = "invalid-zone"

        val result = Parser[Zone].parse(invalidZone)

        result shouldBe Left("Invalid value: \"invalid-zone\" - should be one of: protected, public, protected-rate, public-monolith, public-rate, private")
      }
    }
  }

  private def stubConfigConnector(
    service            : ServiceName,
    stubs              : Seq[DeploymentConfig]
  ): ServiceConfigsConnector =
    val serviceConfigsConnector = mock[ServiceConfigsConnector]

    when(serviceConfigsConnector.deploymentConfig(Option(service)))
      .thenReturn(Future.successful(stubs))

    stubs.foreach(deploymentConfig =>
      when(serviceConfigsConnector.deploymentConfig(Option(service), Some(deploymentConfig.environment)))
        .thenReturn(Future.successful(Seq(deploymentConfig)))
    )

    serviceConfigsConnector

  private lazy val costEstimateConfig =
    CostEstimateConfig(Configuration(ConfigFactory.load()))
}
