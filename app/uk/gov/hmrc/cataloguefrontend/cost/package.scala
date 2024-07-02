/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.util.{ChartDataTable, FromString, FromStringEnum, Parser}

package object cost:
  import FromStringEnum._

  case class DeploymentSize(
    slots       : Int,
    instances   : Int,
  ):
    def totalSlots: TotalSlots =
      TotalSlots(slots * instances)

    val slotsToMemory: Int =
      slots * 128

    val slotsAndInstancesToMemory: Int =
      slotsToMemory * instances

  object DeploymentSize:
    val empty: DeploymentSize =
      DeploymentSize(slots = 0, instances = 0)

    val reads: Reads[DeploymentSize] =
      ( (__ \ "slots"    ).read[Int]
      ~ (__ \ "instances").read[Int]
      )(DeploymentSize.apply)

  case class DeploymentConfig(
    serviceName   : ServiceName,
    deploymentSize: DeploymentSize,
    environment   : Environment,
    zone          : Zone,
    envVars       : Map[String, String],
    jvm           : Map[String, String]
  ):
    def asMap: Map[String, String] =
      Map(
        "instances" -> deploymentSize.instances.toString,
        "slots"     -> deploymentSize.slots.toString
      )
        ++ jvm.map : (key, value) =>
             (s"jvm.$key", value)
        ++ envVars.map: (key, value) =>
             (s"environment.$key", value)

  case class TotalSlots(asInt: Int) extends AnyVal:
    def costGbp(costEstimateConfig: CostEstimateConfig) =
      asInt * costEstimateConfig.slotCostPerYear

  given Parser[Zone] = Parser.parser(Zone.values)

  enum Zone(
    override val asString: String
  ) extends FromString
    derives Ordering, Reads:
    case Protected      extends Zone("protected"      )
    case Public         extends Zone("public"         )
    case ProtectedRate  extends Zone("protected-rate" )
    case PublicMonolith extends Zone("public-monolith")
    case PublicRate     extends Zone("public-rate"    )
    case Private        extends Zone("private"        )

    def displayName: String =
      asString.capitalize

  object DeploymentConfig:
    val reads: Reads[DeploymentConfig] =
      ( (__ \ "name"       ).read[ServiceName        ](ServiceName.format)
      ~ (__ \ "slots"      ).read[Int                ]
      ~ (__ \ "instances"  ).read[Int                ]
      ~ (__ \ "environment").read[Environment        ]
      ~ (__ \ "zone"       ).read[Zone               ]
      ~ (__ \ "envVars"    ).read[Map[String, String]]
      ~ (__ \ "jvm"        ).read[Map[String, String]]
      ){ (n, s, i, e, z, ev, j) => DeploymentConfig(n, DeploymentSize(s, i), e, z, ev, j) }

  case class HistoricEstimatedCostCharts(
    totalsChart: ChartDataTable,
    byEnvChart : ChartDataTable
  )

  case class ServiceCostEstimate(
    slotsByEnv: Seq[(Environment, TotalSlots)],
    chart     : ChartDataTable
  ):
    lazy val totalSlots: TotalSlots =
      TotalSlots(slotsByEnv.map(_._2.asInt).sum)

    def totalYearlyCostGbp(costEstimateConfig: CostEstimateConfig): Double =
      totalSlots.costGbp(costEstimateConfig)

end cost
