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

import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cataloguefrontend.connector.ConfigConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.{CostEstimation, DeploymentConfig}
import uk.gov.hmrc.http.HeaderCarrier

import java.util.Locale
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
final class CostEstimationService @Inject() (configConnector: ConfigConnector) {

  def estimateServiceCost(
    service: String,
    environments: Seq[Environment]
  )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[CostEstimation] =
    Future
      .traverse(environments)(environment =>
        configConnector
          .deploymentConfig(service, environment)
          .map(deploymentConfig => (environment, deploymentConfig.getOrElse(DeploymentConfig(0, 0))))
      )
      .map(deploymentConfigByEnvironment =>
        CostEstimation
          .fromDeploymentConfigByEnvironment(deploymentConfigByEnvironment.toMap)
      )
}

object CostEstimationService {

  type DeploymentConfigByEnvironment = Map[Environment, DeploymentConfig]

  final case class DeploymentConfig(slots: Int, instances: Int)

  object DeploymentConfig {
    val reads: Reads[DeploymentConfig] = Json.reads[DeploymentConfig]
  }

  final case class CostEstimation(totalSlots: Int) {

    val yearlyCost: Double =
      totalSlots * 650.0

    val yearlyCostFormatted: String = {
      val formattter = java.text.NumberFormat.getCurrencyInstance(Locale.UK)
      formattter.format(yearlyCost)
    }
  }

  object CostEstimation {

    def fromDeploymentConfigByEnvironment(deploymentConfigByEnvironment: DeploymentConfigByEnvironment): CostEstimation = {
      val totalSlots =
        deploymentConfigByEnvironment.values
          .map(deploymentConfig => deploymentConfig.slots * deploymentConfig.instances)
          .sum

      CostEstimation(totalSlots)
    }
  }
}
