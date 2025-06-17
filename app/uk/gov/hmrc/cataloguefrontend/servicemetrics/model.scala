/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.servicemetrics

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.util.{FormFormat, FromString, FromStringEnum, Parser}, FromStringEnum.*

import java.time.Instant

case class EnvironmentResult(
  kibanaLink: String
, count     : Int
)

case class LogMetric(
  id          : String
, displayName : String
, environments: Map[Environment, EnvironmentResult]
)

object LogMetric:
  val reads: Reads[LogMetric] =
    given Reads[EnvironmentResult] =
      ( (__ \ "kibanaLink").read[String]
      ~ (__ \ "count"     ).read[Int]
      )(EnvironmentResult.apply)

    ( (__ \ "id"          ).read[String]
    ~ (__ \ "displayName" ).read[String]
    ~ (__ \ "environments").read[Map[Environment, EnvironmentResult]]
    )(apply)

case class ServiceMetric(
  service    : String
, id         : LogMetricId
, environment: Environment
, kibanaLink : String
, logCount   : Int
)

object ServiceMetric:
  val reads: Reads[ServiceMetric] =
    ( (__ \ "service"     ).read[String]
    ~ (__ \ "id"          ).read[LogMetricId]
    ~ (__ \ "environment" ).read[Environment]
    ~ (__ \ "kibanaLink"  ).read[String]
    ~ (__ \ "logCount"    ).read[Int]
    )(apply)

given Parser[LogMetricId] = Parser.parser(LogMetricId.values)

enum LogMetricId(
  override val asString: String,
  val displayString    : String
) extends FromString
  derives Reads, FormFormat, QueryStringBindable:
  case ContainerKills   extends LogMetricId(asString = "container-kills"   , displayString = "Container Kills"   )
  case NonIndexedQuery  extends LogMetricId(asString = "non-indexed-query" , displayString = "Non-indexed Queries" )
  case SlowRunningQuery extends LogMetricId(asString = "slow-running-query", displayString = "Slow Running Queries")

case class ServiceProvision(
  from       : Instant
, to         : Instant
, service    : String
, environment: Environment
, metrics    : Map[String, BigDecimal]
):

  private val costPerSlot    = 6.25    // £
  private val secondsInMonth = 2628000 // 365/12*24*60*60

  val slotsPerInstance: Option[BigDecimal] =
    ( metrics.get("slots")
    , metrics.get("instances")
    ) match
      case (_          , Some(0        )) => None
      case (Some(slots), Some(instances)) => Some(slots / instances)
      case _                              => None

  val percentageOfMaxMemoryUsed: Option[BigDecimal] =
    ( slotsPerInstance
    , metrics.get("memory")
  ) match
      case (Some(0               ), _           ) => None
                                                     // max memory used is converted from bytes to megabytes, a slot has 128mb of memory
      case (Some(slotsForInstance), Some(memory)) => Some(memory / 1000000 / (slotsForInstance * 128) * 100)
      case _                                      => None

  val costPerInstance: Option[BigDecimal] =
    ( slotsPerInstance
    , metrics.get("memory")
    ) match
      case (Some(slotsForInstance), Some(memory)) => Some(slotsForInstance * costPerSlot)
      case _                                      => None

  val costPerRequest: Option[BigDecimal] =
    ( metrics.get("slots")
    , metrics.get("requests")
    ) match
      case (_          , Some(0       )) => None
      case (Some(slots), Some(requests)) => Some(costPerSlot * slots / requests)
      case _                             => None

  val costPerTime: Option[BigDecimal] =
    ( metrics.get("slots")
    , metrics.get("time")
    ) match
      case (Some(slots), Some(time)) => Some((costPerSlot * slots / secondsInMonth) * time)
      case _                         => None


object ServiceProvision:
  val reads: Reads[ServiceProvision] =
    ( (__ \ "from"       ).read[Instant]
    ~ (__ \ "to"         ).read[Instant]
    ~ (__ \ "service"    ).read[String]
    ~ (__ \ "environment").read[Environment]
    ~ (__ \ "metrics"    ).read[Map[String, BigDecimal]]
    )(ServiceProvision.apply _)
