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

import cats.implicits._
import play.api.Configuration
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cataloguefrontend.connector.ResourceUsageConnector.ResourceUsage
import uk.gov.hmrc.cataloguefrontend.connector.ResourceUsageConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.cataloguefrontend.util.{ChartDataTable, CurrencyFormatter}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CostEstimationService @Inject() (
  serviceConfigsConnector: ServiceConfigsConnector,
  resourceUsageConnector: ResourceUsageConnector,
  costEstimateConfig    : CostEstimateConfig
) {
  import CostEstimationService._

  def estimateServiceCost(
    serviceName: String
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[ServiceCostEstimate] =
    Environment.values
      .traverse(environment =>
        serviceConfigsConnector
          .deploymentConfig(serviceName, environment)
          .map(deploymentConfig => (environment, deploymentConfig.getOrElse(DeploymentConfig.empty)))
      )
      .map { deploymentConfigByEnvironment =>
        val slotsByEnv =
          deploymentConfigByEnvironment
            .collect { case (env, config) if config.totalSlots.asInt > 0 =>
              env -> config.totalSlots
            }
            .sortBy(_._1)

        ServiceCostEstimate(
          slotsByEnv = slotsByEnv,
          chart      = toChartDataTableTotalByEnv(slotsByEnv)
        )
      }

  def historicEstimatedCostChartsForService(
    serviceName: String
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[HistoricEstimatedCostCharts] =
    resourceUsageConnector
      .historicResourceUsageForService(serviceName)
      .map(rus =>
        HistoricEstimatedCostCharts(
          totalsChart = toChartDataTableTotal(rus),
          byEnvChart  = toChartDataTableByEnv(rus)
        )
      )

  private def formatForChart(totalSlots: TotalSlots): String = {
    val yearlyCostGbp = totalSlots.costGbp(costEstimateConfig)
    s"""{ v: $yearlyCostGbp, f: "${CurrencyFormatter.formatGbp(yearlyCostGbp)} (slots = ${totalSlots.asInt})" }"""
  }

  private def toChartDataTableByEnv(resourceUsages: List[ResourceUsage]): ChartDataTable = {
    val applicableEnvironments =
      resourceUsages
        .foldLeft(Set.empty[Environment])((acc, ru) => acc ++ ru.values.collect { case (env, deploymentConfig) if deploymentConfig != DeploymentConfig.empty => env })
        .toList
        .sorted

    val headerRow =
      "'Date'" +: applicableEnvironments.map(e => s"'${e.displayString}'")

    val dataRows =
      resourceUsages
        .map { ru =>
          s"new Date(${ru.date.toEpochMilli})" +:
            applicableEnvironments.map(e => s"${ru.values.get(e).map(dc => formatForChart(dc.totalSlots)).getOrElse("null")}")
        }

    ChartDataTable(headerRow +: dataRows)
  }

  private def toChartDataTableTotal(resourceUsages: List[ResourceUsage]): ChartDataTable = {
    val headerRow =
      List("'Date'", "'Yearly Cost'")

    val dataRows =
      resourceUsages.map { resourceUsage =>
        val totalSlots = TotalSlots(resourceUsage.values.values.map(_.totalSlots.asInt).fold(0)(_ + _))
        List(s"new Date(${resourceUsage.date.toEpochMilli})", formatForChart(totalSlots))
      }
      .toList

    ChartDataTable(headerRow +: dataRows)
  }

  private def toChartDataTableTotalByEnv(slotsByEnv: Seq[(Environment, TotalSlots)]): ChartDataTable = {
    val headerRow =
      List("'Environment'", "'Estimated Cost'")

    val dataRows =
      slotsByEnv
        .map { case (environment, totalSlots) =>
          List(s"'${environment.displayString}'", formatForChart(totalSlots))
        }
        .toList
    ChartDataTable(headerRow +: dataRows)
  }
}

object CostEstimationService {

  final case class DeploymentConfig(
    slots    : Int,
    instances: Int,
    zone     : Option[String] = None,
  ) {
    def totalSlots: TotalSlots =
      TotalSlots(slots * instances)
  }

  case class TotalSlots(asInt: Int) extends AnyVal {
    def costGbp(costEstimateConfig: CostEstimateConfig) =
      asInt * costEstimateConfig.slotCostPerYear
  }

  object DeploymentConfig {
    val empty: DeploymentConfig =
      DeploymentConfig(slots = 0, instances = 0)

    val reads: Reads[DeploymentConfig] =
      Json.reads[DeploymentConfig]
  }

  final case class HistoricEstimatedCostCharts(
    totalsChart: ChartDataTable,
    byEnvChart : ChartDataTable
  )

  final case class ServiceCostEstimate(
    slotsByEnv: Seq[(Environment, TotalSlots)],
    chart     : ChartDataTable
  ) {
    lazy val totalSlots: TotalSlots =
      TotalSlots(slotsByEnv.map(_._2.asInt).fold(0)(_ + _))

    def totalYearlyCostGbp(costEstimateConfig: CostEstimateConfig) =
      totalSlots.costGbp(costEstimateConfig)
  }
}

class CostEstimateConfig @Inject()(configuration: Configuration) {

  def slotCostPerYear: Double =
    configuration
      .get[Double]("cost-estimates.slot-cost-per-year")

  def totalAwsCostPerYear: String =
    configuration
      .get[String]("cost-estimates.total-aws-cost-per-year")
}
