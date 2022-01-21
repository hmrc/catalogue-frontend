/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.Format
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.ResourceUsage
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.StringContextOps

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ResourceUsageConnector @Inject() (
  http: HttpClient,
  servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext) {

  private val baseUrl =
    s"${servicesConfig.baseUrl("service-configs")}/resource-usage"

  implicit val resourceUsageFormat: Format[ResourceUsage] =
    ResourceUsage.format

  def historicResourceUsageForService(serviceName: String)(implicit hc: HeaderCarrier): Future[List[ResourceUsage]] =
    http.GET[List[ResourceUsage]](url = url"$baseUrl/services/$serviceName/snapshots")
}
