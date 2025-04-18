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
