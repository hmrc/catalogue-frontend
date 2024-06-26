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

import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.connector.ResourceUsageConnector
import uk.gov.hmrc.cataloguefrontend.connector.ResourceUsageConnector.ResourceUsage
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.cataloguefrontend.util.{ChartDataTable, CurrencyFormatter}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CostEstimationService @Inject() (
  serviceConfigsConnector: ServiceConfigsConnector,
  resourceUsageConnector : ResourceUsageConnector,
  costEstimateConfig     : CostEstimateConfig
)(using
  ExecutionContext
):

  def estimateServiceCost(
    serviceName: ServiceName
  )(using
    HeaderCarrier
  ): Future[ServiceCostEstimate] =
    serviceConfigsConnector
      .deploymentConfig(Some(serviceName))
      .map: deploymentConfigByEnvironment =>
        val slotsByEnv =
          deploymentConfigByEnvironment
            .collect:
              case config if config.deploymentSize.totalSlots.asInt > 0 =>
                config.environment -> config.deploymentSize.totalSlots
            .sortBy(_._1)

        ServiceCostEstimate(
          slotsByEnv = slotsByEnv,
          chart      = toChartDataTableTotalByEnv(slotsByEnv)
        )

  def historicEstimatedCostChartsForService(
    serviceName: ServiceName
  )(using
    HeaderCarrier
  ): Future[HistoricEstimatedCostCharts] =
    resourceUsageConnector
      .historicResourceUsageForService(serviceName)
      .map: rus =>
        HistoricEstimatedCostCharts(
          totalsChart = toChartDataTableTotal(rus),
          byEnvChart  = toChartDataTableByEnv(rus)
        )

  private def formatForChart(totalSlots: TotalSlots): String =
    val yearlyCostGbp = totalSlots.costGbp(costEstimateConfig)
    s"""{ v: $yearlyCostGbp, f: "${CurrencyFormatter.formatGbp(yearlyCostGbp)} (slots = ${totalSlots.asInt})" }"""

  private def toChartDataTableByEnv(resourceUsages: List[ResourceUsage]): ChartDataTable =
    val applicableEnvironments =
      resourceUsages
        .foldLeft(Set.empty[Environment]): (acc, ru) =>
          acc ++ ru.values.collect { case (env, deploymentSize) if deploymentSize != DeploymentSize.empty => env }
        .toList
        .sorted

    val headerRow =
      "'Date'" +: applicableEnvironments.map(e => s"'${e.displayString}'")

    val dataRows =
      resourceUsages
        .map: ru =>
          s"new Date(${ru.date.toEpochMilli})" +:
            applicableEnvironments.map(e => s"${ru.values.get(e).map(dc => formatForChart(dc.totalSlots)).getOrElse("null")}")

    ChartDataTable(headerRow +: dataRows)

  private def toChartDataTableTotal(resourceUsages: List[ResourceUsage]): ChartDataTable =
    val headerRow =
      List("'Date'", "'Yearly Cost'")

    val dataRows =
      resourceUsages
        .map: resourceUsage =>
          val totalSlots = TotalSlots(resourceUsage.values.values.map(_.totalSlots.asInt).sum)
          List(s"new Date(${resourceUsage.date.toEpochMilli})", formatForChart(totalSlots))
        .toList

    ChartDataTable(headerRow +: dataRows)

  private def toChartDataTableTotalByEnv(slotsByEnv: Seq[(Environment, TotalSlots)]): ChartDataTable =
    val headerRow =
      List("'Environment'", "'Estimated Cost'")

    val dataRows =
      slotsByEnv
        .map: (environment, totalSlots) =>
          List(s"'${environment.displayString}'", formatForChart(totalSlots))
        .toList

    ChartDataTable(headerRow +: dataRows)

end CostEstimationService

class CostEstimateConfig @Inject()(configuration: Configuration):

  def slotCostPerYear: Double =
    configuration
      .get[Double]("cost-estimates.slot-cost-per-year")

  def totalAwsCostPerYear: String =
    configuration
      .get[String]("cost-estimates.total-aws-cost-per-year")
