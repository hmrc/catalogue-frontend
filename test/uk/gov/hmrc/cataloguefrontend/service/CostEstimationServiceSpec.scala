/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.connector.ConfigConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.{CostEstimation, DeploymentConfig, DeploymentConfigByEnvironment}
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

      val costEstimation =
        costEstimationService.estimateServiceCost("some-service", stubs.keySet.toSeq)

      val expectedCostEstimation =
        CostEstimation.fromDeploymentConfigByEnvironment(stubs)

      costEstimation.futureValue shouldBe expectedCostEstimation
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

      val costEstimation =
        costEstimationService.estimateServiceCost("some-service", (stubs.keySet ++ missingEnvironments).toSeq)

      val expectedCostEstimation =
        CostEstimation.fromDeploymentConfigByEnvironment(stubs)

      costEstimation.futureValue shouldBe expectedCostEstimation
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

      val costEstimation =
        costEstimationService.estimateServiceCost("some-service", missingEnvironments.toSeq)

      val expectedCostEstimation =
        CostEstimation(0)

      costEstimation.futureValue shouldBe expectedCostEstimation
    }

    "estimate cost as a function of a service's total slots across all environments" in {
      val deploymentConfigByEnvironment: DeploymentConfigByEnvironment =
        Map(
          (Environment.Development, DeploymentConfig(5, 2)),
          (Environment.QA, DeploymentConfig(3, 1)),
          (Environment.Production, DeploymentConfig(10, 3))
        )

      val actualEstimatedCost =
        CostEstimation.fromDeploymentConfigByEnvironment(deploymentConfigByEnvironment).yearlyCostFormatted

      // Yearly cost is estimated as a service's total slot usage across all environments multiplied by £650
      // (5 * 2 + 3 * 1 + 10 * 3) * 650.0
      val expectedEstimatedCost = "£27,950.00"

      actualEstimatedCost shouldBe expectedEstimatedCost
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
}
