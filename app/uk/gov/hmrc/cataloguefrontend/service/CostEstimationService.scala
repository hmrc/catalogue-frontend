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

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CostEstimationService @Inject() (
  serviceConfigsConnector: ServiceConfigsConnector,
  resourceUsageConnector : ResourceUsageConnector
) {
  import CostEstimationService._

  def estimateServiceCost(
    service                  : String,
    serviceCostEstimateConfig: CostEstimateConfig
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[ServiceCostEstimate] =
    Environment.values.traverse(environment =>
        serviceConfigsConnector
          .deploymentConfig(service, environment)
          .map(deploymentConfig => (environment, deploymentConfig.getOrElse(DeploymentConfig.empty)))
      )
      .map(deploymentConfigByEnvironment =>
        ServiceCostEstimate
          .fromDeploymentConfigByEnvironment(deploymentConfigByEnvironment.toMap, serviceCostEstimateConfig)
      )

  def historicResourceUsageChartsForService(
    serviceName              : String,
    serviceCostEstimateConfig: CostEstimateConfig
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[EstimatedCostCharts] =
    resourceUsageConnector
      .historicResourceUsageForService(serviceName)
      .map(_.map(CostedResourceUsage.fromResourceUsage(_, serviceCostEstimateConfig.slotCostPerYear)))
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

  final case class DeploymentConfig(
    slots    : Int,
    instances: Int
  )

  object DeploymentConfig {
    val empty: DeploymentConfig =
      DeploymentConfig(slots = 0, instances = 0)

    val reads: Reads[DeploymentConfig] =
      Json.reads[DeploymentConfig]
  }

  final case class CostedResourceUsage(
    date       : Instant,
    serviceName: String,
    values     : Map[Environment, CostedResourceUsage.Summary]
  )

  object CostedResourceUsage {
    def fromResourceUsage(resourceUsage: ResourceUsage, slotCostPerYear: Double): CostedResourceUsage =
      CostedResourceUsage(
        date        = resourceUsage.date,
        serviceName = resourceUsage.serviceName,
        values      = resourceUsage.values.view.mapValues(ru =>
                        CostedResourceUsage.Summary(
                          totalSlots    = ru.slots * ru.instances,
                          yearlyCostGbp = ru.slots * ru.instances * slotCostPerYear
                        )
                      ).toMap
      )

    final case class Summary(totalSlots: Int, yearlyCostGbp: Double) {
      def +(other: Summary): Summary =
        copy(
          totalSlots    = totalSlots    + other.totalSlots,
          yearlyCostGbp = yearlyCostGbp + other.yearlyCostGbp
        )

      def formatForChart: String =
        s"""{ v: $yearlyCostGbp, f: "${CurrencyFormatter.formatGbp(yearlyCostGbp)} (slots = $totalSlots)" }"""
    }

    object Summary {

      val empty: Summary =
        Summary(0, 0)

    }

    def toChartDataTable(costedResourceUsages: List[CostedResourceUsage]): ChartDataTable = {
      // TODO do this efficiently
        val environments =
          costedResourceUsages
            .foldLeft(Set.empty[Environment])((acc, ru) => acc ++ ru.values.collect { case (env, summary) if summary != Summary.empty => env })
            .toList
            .sorted

        val headerRow =
          "'Date'" +: environments.map(e => s"'${e.displayString}'")

        val dataRows =
          costedResourceUsages
            .map { ru =>
              s"new Date(${ru.date.toEpochMilli})" +:
                environments.map(e => s"${ru.values.get(e).map(_.formatForChart).getOrElse("null")}")
            }

        ChartDataTable(headerRow +: dataRows)
    }
  }

  final case class CostedResourceUsageTotal(
    date   : Instant,
    summary: CostedResourceUsage.Summary
  )

  object CostedResourceUsageTotal {

    def fromCostedResourceUsages(costedResourceUsages: List[CostedResourceUsage]): List[CostedResourceUsageTotal] =
      costedResourceUsages
        .map(resourceUsage =>
          CostedResourceUsageTotal(
            date    = resourceUsage.date,
            summary = resourceUsage.values.values.fold(CostedResourceUsage.Summary.empty)(_ + _)
          )
        )
        .toList

    def toChartDataTable(costedResourceUsageTotals: List[CostedResourceUsageTotal]): ChartDataTable = {
      val headerRow =
        List("'Date'", "'Yearly Cost'")

      val dataRows =
        costedResourceUsageTotals.map(crut => List(s"new Date(${crut.date.toEpochMilli})", s"${crut.summary.formatForChart}"))

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
      deploymentConfigByEnvironment: Map[Environment, DeploymentConfig],
      serviceCostEstimateConfig    : CostEstimateConfig
    ): ServiceCostEstimate =
      ServiceCostEstimate {
        deploymentConfigByEnvironment
          .collect { case (env, config) if config.slots > 0 && config.instances > 0 =>
            ForEnvironment(
              env,
              slots         = config.slots * config.instances,
              yearlyCostGbp = config.slots * config.instances * serviceCostEstimateConfig.slotCostPerYear
            )
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
