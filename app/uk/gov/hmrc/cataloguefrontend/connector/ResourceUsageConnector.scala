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
import uk.gov.hmrc.cataloguefrontend.cost.DeploymentSize
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
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
)(using ExecutionContext):
  import ResourceUsageConnector._

  private val baseUrl = servicesConfig.baseUrl("service-configs")

  private def rawResourceUsageForService(serviceName: ServiceName)(using HeaderCarrier): Future[List[RawResourceUsage]] =
    given Format[RawResourceUsage] = RawResourceUsage.format
    httpClientV2
      .get(url"$baseUrl/service-configs/resource-usage/services/${serviceName.asString}/snapshots")
      .execute[List[RawResourceUsage]]

  def historicResourceUsageForService(serviceName: ServiceName)(using HeaderCarrier): Future[List[ResourceUsage]] =
    rawResourceUsageForService(serviceName)
      .map: res =>
        // for every date with a value, ensure we have a value for all environments
        // if not present, the value will be the same as the previous (or zero)
        (scala.collection.immutable.TreeMap.empty[Instant, List[RawResourceUsage]] ++ res.groupBy(_.date))
          .toList
          .foldLeft(List.empty[ResourceUsage]):
            case (acc, (date, resourceUsages)) =>
              val values: Map[Environment, DeploymentSize] =
                acc.lastOption match
                  case Some(previous) =>
                    Environment
                      .valuesAsSeq.map: env =>
                        env -> resourceUsages
                                .find(_.environment == env).map(_.deploymentSize)
                                .orElse(previous.values.get(env))
                                .getOrElse(DeploymentSize.empty)
                      .toMap
                  case None =>
                    Environment
                      .valuesAsSeq.map: env =>
                        env -> resourceUsages
                                .find(_.environment == env).map(_.deploymentSize)
                                .getOrElse(DeploymentSize.empty)
                      .toMap
              acc :+ ResourceUsage(date, serviceName, values)
      .map: dataForAllEnvs =>
        // double every point, starting at the date we have, but finishing at the next date
        // except the last one, which ends today
        dataForAllEnvs
          .foldRight[(List[ResourceUsage], Instant)]((List.empty, Instant.now(clock))):
            case (ru, (acc, timestamp)) =>
              (ru +: ru.copy(date = timestamp) +: acc, ru.date)
          ._1

end ResourceUsageConnector

object ResourceUsageConnector:

  case class ResourceUsage(
    date       : Instant,
    serviceName: ServiceName,
    values     : Map[Environment, DeploymentSize]
  )

  case class RawResourceUsage(
    date          : Instant,
    serviceName   : ServiceName,
    environment   : Environment,
    deploymentSize: DeploymentSize,
  )

  object RawResourceUsage:
    val format: Format[RawResourceUsage] =
      ( (__ \ "date"        ).format[Instant]
      ~ (__ \ "serviceName" ).format[ServiceName](ServiceName.format)
      ~ (__ \ "environment" ).format[Environment](Environment.format)
      ~ (__ \ "slots"       ).format[Int]
      ~ (__ \ "instances"   ).format[Int]
      )((d, sn, e, s, i) => RawResourceUsage(d, sn, e, DeploymentSize(s, i))
       , u => (u.date, u.serviceName, u.environment, u.deploymentSize.slots, u.deploymentSize.instances)
       )

end ResourceUsageConnector
