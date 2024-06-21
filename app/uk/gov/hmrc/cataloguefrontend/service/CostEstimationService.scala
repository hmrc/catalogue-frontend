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
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.connector.ResourceUsageConnector
import uk.gov.hmrc.cataloguefrontend.connector.ResourceUsageConnector.ResourceUsage
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.cataloguefrontend.util.{ChartDataTable, CurrencyFormatter, FromString, FromStringEnum}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.JsonCodecs.environmentFormat
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CostEstimationService @Inject() (
  serviceConfigsConnector: ServiceConfigsConnector,
  resourceUsageConnector : ResourceUsageConnector,
  costEstimateConfig     : CostEstimateConfig
) {
  import CostEstimationService._

  def estimateServiceCost(
    serviceName: ServiceName
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[ServiceCostEstimate] =
    serviceConfigsConnector
      .deploymentConfig(Some(serviceName))
      .map { deploymentConfigByEnvironment =>
        val slotsByEnv =
          deploymentConfigByEnvironment
            .collect { case config if config.deploymentSize.totalSlots.asInt > 0 =>
              config.environment -> config.deploymentSize.totalSlots
            }
            .sortBy(_._1)

        ServiceCostEstimate(
          slotsByEnv = slotsByEnv,
          chart      = toChartDataTableTotalByEnv(slotsByEnv)
        )
      }

  def historicEstimatedCostChartsForService(
    serviceName: ServiceName
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
        .foldLeft(Set.empty[Environment])((acc, ru) => acc ++ ru.values.collect { case (env, deploymentSize) if deploymentSize != DeploymentSize.empty => env })
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
        val totalSlots = TotalSlots(resourceUsage.values.values.map(_.totalSlots.asInt).sum)
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

  case class DeploymentSize(
    slots       : Int,
    instances   : Int,
  ) {
    def totalSlots: TotalSlots =
      TotalSlots(slots * instances)

    val slotsToMemory: Int =
      slots * 128

    val slotsAndInstancesToMemory: Int =
      slotsToMemory * instances
  }

  object DeploymentSize {
    val empty = DeploymentSize(slots = 0, instances = 0)

    val reads: Reads[DeploymentSize] =
      ( (__ \ "slots"    ).read[Int]
      ~ (__ \ "instances").read[Int]
      )(DeploymentSize.apply)
  }

  case class DeploymentConfig(
    serviceName   : ServiceName,
    deploymentSize: DeploymentSize,
    environment   : Environment,
    zone          : Zone,
    envVars       : Map[String, String],
    jvm           : Map[String, String]
  ) {
    def asMap: Map[String, String] = Map(
      "instances" -> deploymentSize.instances.toString,
      "slots" -> deploymentSize.slots.toString
    ) ++ jvm.map{ case (key, value) => (s"jvm.$key", value) } ++ envVars.map{ case (key, value) => (s"environment.$key", value) }
  }

  case class TotalSlots(asInt: Int) extends AnyVal {
    def costGbp(costEstimateConfig: CostEstimateConfig) =
      asInt * costEstimateConfig.slotCostPerYear
  }

  enum Zone(val asString: String) extends FromString:
    case Protected      extends Zone("protected"      )
    case Public         extends Zone("public"         )
    case ProtectedRate  extends Zone("protected-rate" )
    case PublicMonolith extends Zone("public-monolith")
    case PublicRate     extends Zone("public-rate"    )
    case Private        extends Zone("private"        )

    def displayName: String =
      asString.capitalize

  object Zone extends FromStringEnum[Zone]

  object DeploymentConfig {
    val reads: Reads[DeploymentConfig] =
      ( (__ \ "name"       ).read[ServiceName        ](ServiceName.format)
      ~ (__ \ "slots"      ).read[Int]
      ~ (__ \ "instances"  ).read[Int]
      ~ (__ \ "environment").read[Environment        ](environmentFormat)
      ~ (__ \ "zone"       ).read[Zone               ](Zone.format)
      ~ (__ \ "envVars"    ).read[Map[String, String]]
      ~ (__ \ "jvm"        ).read[Map[String, String]]
      ){ (n, s, i, e, z, ev, j) => DeploymentConfig(n, DeploymentSize(s, i), e, z, ev, j) }
  }

  case class HistoricEstimatedCostCharts(
    totalsChart: ChartDataTable,
    byEnvChart : ChartDataTable
  )

  case class ServiceCostEstimate(
    slotsByEnv: Seq[(Environment, TotalSlots)],
    chart     : ChartDataTable
  ) {
    lazy val totalSlots: TotalSlots =
      TotalSlots(slotsByEnv.map(_._2.asInt).sum)

    def totalYearlyCostGbp(costEstimateConfig: CostEstimateConfig): Double =
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
