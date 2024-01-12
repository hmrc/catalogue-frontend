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

package uk.gov.hmrc.cataloguefrontend.costs.model

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.model.Environment._

import java.text.NumberFormat

case class EnvironmentConfig(
  environment: Environment,
  slots: Int,
  instances: Int
) {

  val slotsToMemory: Int             = slots * 128
  val slotsAndInstancesToMemory: Int = slotsToMemory * instances

  val totalSlots: Int                                              = slots * instances
  def estimatedCostsOfEnvironment(slotCostPerYear: Double): Double = slotCostPerYear * totalSlots
}

object EnvironmentConfig {

  implicit val reads: Reads[EnvironmentConfig] =
    ((__ \ "environment").read[Environment](Environment.format)
      ~ (__ \ "slots").read[Int]
      ~ (__ \ "instances").read[Int])(EnvironmentConfig.apply _)
}

case class ServiceDeploymentConfigGrouped(
  serviceName: String,
  configs: Seq[EnvironmentConfig]
) {

  def totalEstimatedCostFormatted(slotCostsPerYear: Double): String = {
    val formatter: NumberFormat = java.text.NumberFormat.getIntegerInstance
    formatter.format(configs.map(_.estimatedCostsOfEnvironment(slotCostsPerYear)).sum)
  }

  def totalEstimatedCosts(slotCostsPerYear: Double): Double =
    configs.map(_.estimatedCostsOfEnvironment(slotCostsPerYear)).sum

  val getIntegrationEnvironment: Option[EnvironmentConfig]  = configs.find(_.environment == Integration)
  val getDevelopmentEnvironment: Option[EnvironmentConfig]  = configs.find(_.environment == Development)
  val getQAEnvironment: Option[EnvironmentConfig]           = configs.find(_.environment == QA)
  val getStagingEnvironment: Option[EnvironmentConfig]      = configs.find(_.environment == Staging)
  val getExternalTestEnvironment: Option[EnvironmentConfig] = configs.find(_.environment == ExternalTest)
  val getProductionEnvironment: Option[EnvironmentConfig]   = configs.find(_.environment == Production)
}

object ServiceDeploymentConfigGrouped {

  val reads: Reads[ServiceDeploymentConfigGrouped] =
    ((__ \ "_id").read[String]
      ~ (__ \ "configs").read[Seq[EnvironmentConfig]])(ServiceDeploymentConfigGrouped.apply _)
}
