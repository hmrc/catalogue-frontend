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

package uk.gov.hmrc.cataloguefrontend.connector

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, __}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.DeploymentSize
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ResourceUsageConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig,
  clock         : Clock
)(implicit ec: ExecutionContext) {
  import ResourceUsageConnector._

  private val baseUrl = servicesConfig.baseUrl("service-configs")

  private def rawResourceUsageForService(serviceName: String)(implicit hc: HeaderCarrier): Future[List[RawResourceUsage]] = {
    implicit val rruf: Format[RawResourceUsage] = RawResourceUsage.format
    httpClientV2
      .get(url"$baseUrl/service-configs/resource-usage/services/$serviceName/snapshots")
      .execute[List[RawResourceUsage]]
  }

  def historicResourceUsageForService(serviceName: String)(implicit hc: HeaderCarrier): Future[List[ResourceUsage]] =
    rawResourceUsageForService(serviceName)
      .map { res =>
        // for every date with a value, ensure we have a value for all environments
        // if not present, the value will be the same as the previous (or zero)
        (scala.collection.immutable.TreeMap.empty[Instant, List[RawResourceUsage]] ++ res.groupBy(_.date))
          .toList
          .foldLeft(List.empty[ResourceUsage]) { case (acc, (date, resourceUsages)) =>
            val values: Map[Environment, DeploymentSize] = acc.lastOption match {
              case Some(previous) =>
                Environment.valuesAsSeq.map(env =>
                  env -> resourceUsages
                           .find(_.environment == env).map(_.deploymentSize)
                           .orElse(previous.values.get(env))
                           .getOrElse(DeploymentSize.empty)
                ).toMap
              case None =>
                Environment.valuesAsSeq.map(env =>
                  env -> resourceUsages
                           .find(_.environment == env).map(_.deploymentSize)
                           .getOrElse(DeploymentSize.empty)
                ).toMap
            }
            acc :+ ResourceUsage(date, serviceName, values)
          }
      }
      .map(dataForAllEnvs =>
        // double every point, starting at the date we have, but finishing at the next date
        // except the last one, which ends today
        dataForAllEnvs
          .foldRight[(List[ResourceUsage], Instant)]((List.empty[ResourceUsage], Instant.now(clock))){ case (cru, (acc, timestamp)) =>
            val date = cru.date
            (cru +: cru.copy(date = timestamp) +: acc, date)
          }._1
      )
  }

object ResourceUsageConnector {

  final case class ResourceUsage(
    date       : Instant,
    serviceName: String,
    values     : Map[Environment, DeploymentSize]
  )

  final case class RawResourceUsage(
    date       : Instant,
    serviceName: String,
    environment: Environment,
    deploymentSize: DeploymentSize,
  )

  object RawResourceUsage {
    def apply(
      date       : Instant,
      serviceName: String,
      environment: Environment,
      slots      : Int,
      instances  : Int,
    ): RawResourceUsage = RawResourceUsage(
      date,
      serviceName,
      environment,
      DeploymentSize(slots, instances)
    )

    val format: Format[RawResourceUsage] =
      ( (__ \ "date"        ).format[Instant]
      ~ (__ \ "serviceName" ).format[String]
      ~ (__ \ "environment" ).format[Environment](Environment.format)
      ~ (__ \ "slots"       ).format[Int]
      ~ (__ \ "instances"   ).format[Int]
      )(RawResourceUsage.apply, u => (u.date, u.serviceName, u.environment, u.deploymentSize.slots, u.deploymentSize.instances))
  }
}
