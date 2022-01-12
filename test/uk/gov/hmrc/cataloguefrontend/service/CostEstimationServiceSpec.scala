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

package uk.gov.hmrc.cataloguefrontend.service

import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.connector.ConfigConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.ServiceCostEstimate.Summary
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.{DeploymentConfig, DeploymentConfigByEnvironment, ServiceCostEstimate}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

final class CostEstimationServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "Service" should {
    "produce a cost estimate for a service in all requested environments in which it's deployed" in {
      val stubs: DeploymentConfigByEnvironment =
        Map(
          (Environment.Development, DeploymentConfig(3, 1)),
          (Environment.Integration, DeploymentConfig(3, 1)),
          (Environment.QA, DeploymentConfig(3, 1)),
          (Environment.Staging, DeploymentConfig(3, 1)),
          (Environment.ExternalTest, DeploymentConfig(3, 1)),
          (Environment.Production, DeploymentConfig(11, 3))
        )

      val configConnector =
        stubConfigConnector(
          service = "some-service",
          stubs = stubs,
          missingEnvironments = Set.empty
        )

      val costEstimationService =
        new CostEstimationService(configConnector)

      val costEstimate =
        costEstimationService.estimateServiceCost("some-service", stubs.keySet.toSeq, costEstimateConfig)

      val expectedCostEstimation =
        ServiceCostEstimate.fromDeploymentConfigByEnvironment(stubs, costEstimateConfig)

      costEstimate.futureValue shouldBe expectedCostEstimation
    }

    "disregard services which are not deployed in requested environments" in {
      val stubs: DeploymentConfigByEnvironment =
        Map((Environment.Production, DeploymentConfig(11, 3)))

      val missingEnvironments: Set[Environment] =
        Set(Environment.QA, Environment.Staging)

      val configConnector =
        stubConfigConnector(
          service = "some-service",
          stubs = stubs,
          missingEnvironments = missingEnvironments
        )

      val costEstimationService =
        new CostEstimationService(configConnector)

      val costEstimate =
        costEstimationService
          .estimateServiceCost("some-service", (stubs.keySet ++ missingEnvironments).toSeq, costEstimateConfig)

      val expectedCostEstimate =
        ServiceCostEstimate.fromDeploymentConfigByEnvironment(stubs, costEstimateConfig)

      costEstimate.futureValue shouldBe expectedCostEstimate
    }

    "produce a cost estimate of zero for a service which is not deployed in a requested environment" in {
      val missingEnvironments: Set[Environment] =
        Set(Environment.QA, Environment.Staging, Environment.Production)

      val configConnector =
        stubConfigConnector(
          service = "some-service",
          stubs = Map.empty,
          missingEnvironments = missingEnvironments
        )

      val costEstimationService =
        new CostEstimationService(configConnector)

      val costEstimateSummary =
        costEstimationService
          .estimateServiceCost("some-service", missingEnvironments.toSeq, costEstimateConfig)
          .map(_.summary)

      val expectedCostEstimateSummary =
        ServiceCostEstimate.Summary(totalSlots = 0, totalYearlyCostGbp = 0)

      costEstimateSummary.futureValue shouldBe expectedCostEstimateSummary
    }

    "estimate cost as a function of a service's total slots across all environments" in {
      val deploymentConfigByEnvironment: DeploymentConfigByEnvironment =
        Map(
          (Environment.Development, DeploymentConfig(5, 2)),
          (Environment.QA, DeploymentConfig(3, 1)),
          (Environment.Production, DeploymentConfig(10, 3))
        )

      val actualSummary =
        ServiceCostEstimate
          .fromDeploymentConfigByEnvironment(deploymentConfigByEnvironment, costEstimateConfig)
          .summary

      // Yearly cost is estimated as a service's total slots across all environments multiplied by £650
      // (5 * 2 + 3 * 1 + 10 * 3) * 650
      val expectedEstimatedCost = 27950.0

      val expectedTotalSlots =
        5 * 2 + 3 * 1 + 10 * 3

      actualSummary shouldBe Summary(expectedTotalSlots, expectedEstimatedCost)
    }
  }

  private def stubConfigConnector(
    service: String,
    stubs: DeploymentConfigByEnvironment,
    missingEnvironments: Set[Environment]
  ): ConfigConnector = {
    val configConnector =
      mock[ConfigConnector]

    stubs.foreach {
      case (environment, deploymentConfig) =>
        when(configConnector.deploymentConfig(service, environment))
          .thenReturn(Future.successful(Some(deploymentConfig)))
    }

    missingEnvironments.foreach { environment =>
      when(configConnector.deploymentConfig(service, environment))
        .thenReturn(Future.successful(None))
    }

    configConnector
  }

  private lazy val costEstimateConfig =
    new CostEstimateConfig(
      Configuration.from(
        Map(
          "cost-estimates.slot-cost-per-year" -> 650.0,
          "cost-estimates.total-aws-cost-per-year" -> "£5.4M"
        )
      )
    )
}
