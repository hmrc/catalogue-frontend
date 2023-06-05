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

package uk.gov.hmrc.cataloguefrontend.service

import com.typesafe.config.ConfigFactory
import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.connector.ResourceUsageConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.ServiceCostEstimate.Summary
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.{DeploymentConfig, ServiceCostEstimate}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

final class CostEstimationServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val mockResourceUsageConnector =
    mock[ResourceUsageConnector]

  "Service" should {
    "produce a cost estimate for a service in all requested environments in which it's deployed" in {
      val stubs =
        Map[Environment, DeploymentConfig](
          (Environment.Development , DeploymentConfig(3, 1)),
          (Environment.Integration , DeploymentConfig(3, 1)),
          (Environment.QA          , DeploymentConfig(3, 1)),
          (Environment.Staging     , DeploymentConfig(3, 1)),
          (Environment.ExternalTest, DeploymentConfig(3, 1)),
          (Environment.Production  , DeploymentConfig(11, 3))
        )

      val serviceConfigsConnector =
        stubConfigConnector(
          service = "some-service",
          stubs   = stubs
        )

      val costEstimationService =
        new CostEstimationService(serviceConfigsConnector, mockResourceUsageConnector, costEstimateConfig)

      val costEstimate =
        costEstimationService.estimateServiceCost("some-service")

      val expectedCostEstimation =
        ServiceCostEstimate.fromDeploymentConfigByEnvironment(stubs, costEstimateConfig)

      costEstimate.futureValue shouldBe expectedCostEstimation
    }

    "produce a cost estimate of zero for a service which is not deployed in a requested environment" in {
      val serviceConfigsConnector =
        stubConfigConnector(
          service = "some-service",
          stubs   = Map.empty
        )

      val costEstimationService =
        new CostEstimationService(serviceConfigsConnector, mockResourceUsageConnector, costEstimateConfig)

      val costEstimateSummary =
        costEstimationService
          .estimateServiceCost("some-service")
          .map(_.summary)

      val expectedCostEstimateSummary =
        ServiceCostEstimate.Summary(totalSlots = 0, totalYearlyCostGbp = 0)

      costEstimateSummary.futureValue shouldBe expectedCostEstimateSummary
    }

    "estimate cost as a function of a service's total slots across all environments" in {
      val deploymentConfigByEnvironment =
        Map[Environment, DeploymentConfig](
          (Environment.Development, DeploymentConfig(5, 2)),
          (Environment.QA         , DeploymentConfig(3, 1)),
          (Environment.Production , DeploymentConfig(10, 3))
        )

      val actualSummary =
        ServiceCostEstimate
          .fromDeploymentConfigByEnvironment(deploymentConfigByEnvironment, costEstimateConfig)
          .summary

      // Total slot usage is given as the sum of the per environment multiplication of slots by instances
      val expectedTotalSlots =
        5 * 2 + 3 * 1 + 10 * 3

      // Yearly cost is estimated as a service's total slots across all environments multiplied by the cost of a slot
      // per year
      val expectedEstimatedCost =
        expectedTotalSlots * costEstimateConfig.slotCostPerYear

      actualSummary shouldBe Summary(expectedTotalSlots, expectedEstimatedCost)
    }
  }

  private def stubConfigConnector(
    service            : String,
    stubs              : Map[Environment, DeploymentConfig]
  ): ServiceConfigsConnector = {
    val serviceConfigsConnector = mock[ServiceConfigsConnector]

    Environment.values.foreach { environment =>
      when(serviceConfigsConnector.deploymentConfig(service, environment))
        .thenReturn(Future.successful(stubs.get(environment)))
    }

    serviceConfigsConnector
  }

  private lazy val costEstimateConfig =
    new CostEstimateConfig(Configuration(ConfigFactory.load()))
}
