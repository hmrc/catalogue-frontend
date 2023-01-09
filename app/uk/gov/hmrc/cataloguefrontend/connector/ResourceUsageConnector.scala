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
import uk.gov.hmrc.cataloguefrontend.connector.ResourceUsageConnector.ResourceUsage
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ResourceUsageConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext) {

  private val baseUrl = servicesConfig.baseUrl("service-configs")

  implicit val resourceUsageFormat: Format[ResourceUsage] =
    ResourceUsage.format

  def historicResourceUsageForService(serviceName: String)(implicit hc: HeaderCarrier): Future[List[ResourceUsage]] =
    httpClientV2
      .get(url"$baseUrl/service-configs/resource-usage/services/$serviceName/snapshots")
      .execute[List[ResourceUsage]]
}

object ResourceUsageConnector {

  final case class ResourceUsage(
    date       : Instant,
    serviceName: String,
    environment: Environment,
    slots      : Int,
    instances  : Int
  )

  object ResourceUsage {
    val format: Format[ResourceUsage] =
      ( (__ \ "date"        ).format[Instant]
      ~ (__ \ "serviceName" ).format[String]
      ~ (__ \ "environment" ).format[Environment](Environment.format)
      ~ (__ \ "slots"       ).format[Int]
      ~ (__ \ "instances"   ).format[Int]
      ) (ResourceUsage.apply, unlift(ResourceUsage.unapply))
  }
}
