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

import play.api.Configuration
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cataloguefrontend.connector.ResourceUsageConnector.ResourceUsage
import uk.gov.hmrc.cataloguefrontend.connector.{ConfigConnector, ResourceUsageConnector}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.{CostedResourceUsage, CostedResourceUsageTotal, DeploymentConfig, EstimatedCostCharts, ServiceCostEstimate}
import uk.gov.hmrc.cataloguefrontend.util.{ChartDataTable, CurrencyFormatter}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CostEstimationService @Inject() (
  configConnector       : ConfigConnector,
  resourceUsageConnector: ResourceUsageConnector
) {

  def estimateServiceCost(
    service                  : String,
    environments             : Seq[Environment],
    serviceCostEstimateConfig: CostEstimateConfig
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[ServiceCostEstimate] =
    Future
      .traverse(environments)(environment =>
        configConnector
          .deploymentConfig(service, environment)
          .map(deploymentConfig => (environment, deploymentConfig.getOrElse(DeploymentConfig.empty)))
      )
      .map(deploymentConfigByEnvironment =>
        ServiceCostEstimate
          .fromDeploymentConfigByEnvironment(deploymentConfigByEnvironment.toMap, serviceCostEstimateConfig)
      )

  def historicResourceUsageForService(
    serviceName              : String,
    serviceCostEstimateConfig: CostEstimateConfig
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[List[CostedResourceUsage]] =
    resourceUsageConnector
      .historicResourceUsageForService(serviceName)
      .map(_.map(CostedResourceUsage.fromResourceUsage(_, serviceCostEstimateConfig.slotCostPerYear)))

  def historicResourceUsageChartsForService(
    serviceName              : String,
    serviceCostEstimateConfig: CostEstimateConfig
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[EstimatedCostCharts] =
    historicResourceUsageForService(serviceName, serviceCostEstimateConfig)
      .map(crus =>
        EstimatedCostCharts(
          historicTotalsChart = CostedResourceUsageTotal.toChartDataTable(
                                  CostedResourceUsageTotal.fromCostedResourceUsages(crus)
                                ),
          historicByEnvChart  = CostedResourceUsage.toChartDataTable(crus)
        )
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

  final case class CostedResourceUsage(
    date       : Instant,
    serviceName: String,
    environment: Environment,
    summary    : CostedResourceUsage.Summary
  )

  object CostedResourceUsage {

    final case class Summary(totalSlots: Int, yearlyCostGbp: Double) {
      def +(other: Summary): Summary =
        copy(
          totalSlots = totalSlots + other.totalSlots,
          yearlyCostGbp = yearlyCostGbp + other.yearlyCostGbp
        )

      def formatForChart: String =
        s"""{ v: $yearlyCostGbp, f: "${CurrencyFormatter.formatGbp(yearlyCostGbp)} (slots = $totalSlots)" }"""
    }

    object Summary {

      val empty: Summary =
        Summary(0, 0)
    }

    def fromResourceUsage(resourceUsage: ResourceUsage, slotCostPerYear: Double): CostedResourceUsage = {
      val totalSlots =
        resourceUsage.slots * resourceUsage.instances

      val yearlyCostGbp =
        totalSlots * slotCostPerYear

      CostedResourceUsage(
        resourceUsage.date,
        resourceUsage.serviceName,
        resourceUsage.environment,
        Summary(totalSlots, yearlyCostGbp)
      )
    }

    def toChartDataTable(costedResourceUsages: List[CostedResourceUsage]): ChartDataTable = {
        val environments =
          costedResourceUsages
            .map(_.environment)
            .distinct
            .sorted

        val resourceUsageByDateAndEnvironment =
          costedResourceUsages
            .groupBy(_.date)
            .mapValues(rus => rus.map(ru => ru.environment -> ru).toMap)

        val headerRow =
          "'Date'" +: environments.map(e => s"'${e.displayString}'")

        val dataRows =
          resourceUsageByDateAndEnvironment
            .toList
            .sortBy(_._1)
            .map { case (date, byEnv) =>
              s"new Date(${date.toEpochMilli})" +:
                environments.map(e => s"${byEnv.get(e).map(_.summary.formatForChart).getOrElse("null")}")
            }

        ChartDataTable(headerRow +: dataRows)
    }
  }

  final case class CostedResourceUsageTotal(
    date   : Instant,
    summary: CostedResourceUsage.Summary
  )

  object CostedResourceUsageTotal {

    def fromCostedResourceUsages(costedResourceUsages: List[CostedResourceUsage]): List[CostedResourceUsageTotal] = {
      val resourceUsageByDate =
        costedResourceUsages
          .groupBy(_.date)

      resourceUsageByDate
        .map { case (date, resourceUsages) =>
          val summary =
            resourceUsages
              .map(_.summary)
              .fold(CostedResourceUsage.Summary.empty)(_ + _)

          CostedResourceUsageTotal(date, summary)
        }
        .toList
    }

    def toChartDataTable(costedResourceUsageTotals: List[CostedResourceUsageTotal]): ChartDataTable = {
      val headerRow =
        List("'Date'", "'Yearly Cost'")

      val dataRows =
        costedResourceUsageTotals
          .sortBy(_.date)
          .map(crut => List(s"new Date(${crut.date.toEpochMilli})", s"${crut.summary.formatForChart}"))

      ChartDataTable(headerRow +: dataRows)
    }
  }

  final case class EstimatedCostCharts(
    historicTotalsChart: ChartDataTable,
    historicByEnvChart : ChartDataTable
  )

  final case class ServiceCostEstimate(forEnvironments: List[ServiceCostEstimate.ForEnvironment]) {

    lazy val summary: ServiceCostEstimate.Summary =
      forEnvironments.map(_.summary).fold(ServiceCostEstimate.Summary.zero)(_ + _)
  }

  object ServiceCostEstimate {

    def fromDeploymentConfigByEnvironment(
      deploymentConfigByEnvironment: DeploymentConfigByEnvironment,
      serviceCostEstimateConfig: CostEstimateConfig): ServiceCostEstimate =
      ServiceCostEstimate {
        deploymentConfigByEnvironment
          .collect { case (env, config) if config.slots > 0 && config.instances > 0 => {
            val totalSlots =
              config.slots * config.instances

            val yearlyCostGbp =
              totalSlots * serviceCostEstimateConfig.slotCostPerYear

            ForEnvironment(env, totalSlots, yearlyCostGbp)
            }
          }
          .toList
          .sortBy(_.environment)
      }

    final case class ForEnvironment(
      environment  : Environment,
      slots        : Int,
      yearlyCostGbp: Double
    ) {

      def summary: Summary =
        Summary(slots, yearlyCostGbp)
    }

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

class CostEstimateConfig @Inject()(configuration: Configuration) {

  def slotCostPerYear: Double =
    configuration
      .get[Double]("cost-estimates.slot-cost-per-year")

  def totalAwsCostPerYear: String =
    configuration
      .get[String]("cost-estimates.total-aws-cost-per-year")
}
