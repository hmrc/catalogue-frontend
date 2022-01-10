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

import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cataloguefrontend.connector.ConfigConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.ServiceCostEstimate.Summary
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.{DeploymentConfig, ServiceCostEstimate}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CostEstimationService @Inject() (configConnector: ConfigConnector) {

  def estimateServiceCost(
    service: String,
    environments: Seq[Environment]
  )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[ServiceCostEstimate] =
    Future
      .traverse(environments)(environment =>
        configConnector
          .deploymentConfig(service, environment)
          .map(deploymentConfig => (environment, deploymentConfig.getOrElse(DeploymentConfig.empty)))
      )
      .map(deploymentConfigByEnvironment =>
        ServiceCostEstimate
          .fromDeploymentConfigByEnvironment(deploymentConfigByEnvironment.toMap)
      )
}

object CostEstimationService {

  type DeploymentConfigByEnvironment = Map[Environment, DeploymentConfig]

  final case class DeploymentConfig(slots: Int, instances: Int)

  object DeploymentConfig {
    val empty: DeploymentConfig =
      DeploymentConfig(slots = 0, instances = 0)

    val reads: Reads[DeploymentConfig] =
      Json.reads[DeploymentConfig]
  }

  final case class ServiceCostEstimate(forEnvironments: List[ServiceCostEstimate.ForEnvironment]) {

    lazy val summary: ServiceCostEstimate.Summary =
      forEnvironments
        .foldLeft(Summary.zero)((summary, forEnv) => summary + Summary(forEnv.slots, forEnv.yearlyCostGbp))
  }

  object ServiceCostEstimate {

    val slotCostPerYear: Double = 650

    def fromDeploymentConfigByEnvironment(deploymentConfigByEnvironment: DeploymentConfigByEnvironment): ServiceCostEstimate =
      ServiceCostEstimate {
        deploymentConfigByEnvironment
          .collect { case (env, config) if config.slots > 0 && config.instances > 0 =>
            val yearlyCostGbp =
              config.slots * config.instances * slotCostPerYear
            ForEnvironment(env, config.slots, yearlyCostGbp)
          }
          .toList
          .sortBy(_.environment)
      }

    final case class ForEnvironment(
      environment: Environment,
      slots: Int,
      yearlyCostGbp: Double
    )

    final case class Summary(totalSlots: Int, totalYearlyCostGbp: Double) {
      def +(other: Summary): Summary =
        Summary(totalSlots + other.totalSlots, totalYearlyCostGbp + other.totalYearlyCostGbp)
    }

    object Summary {

      val zero: Summary =
        Summary(totalSlots = 0, totalYearlyCostGbp = 0)
    }
  }
}