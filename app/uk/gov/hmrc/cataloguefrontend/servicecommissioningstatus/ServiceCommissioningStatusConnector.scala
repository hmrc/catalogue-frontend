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

package uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus

import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import HttpReads.Implicits._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.cataloguefrontend.connector.ServiceType
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceCommissioningStatusConnector @Inject() (
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {

  private val serviceCommissioningBaseUrl = servicesConfig.baseUrl("service-commissioning-status")

  private implicit val cReads = Check.reads
  def commissioningStatus(serviceName: String)(implicit hc: HeaderCarrier): Future[Option[List[Check]]] =
    httpClientV2
      .get(url"$serviceCommissioningBaseUrl/service-commissioning-status/status/$serviceName")
      .execute[Option[List[Check]]]

  private implicit val fctReads = FormCheckType.reads
  def allChecks()(implicit hc: HeaderCarrier): Future[Seq[(String, FormCheckType)]] =
    httpClientV2
      .get(url"$serviceCommissioningBaseUrl/service-commissioning-status/checks")
      .execute[Seq[Map[String, FormCheckType]]]
      .map(_.flatMap(_.map { case (k, v ) => (k, v)}))

  private implicit val cscReads = CachedServiceCheck.reads
  def cachedCommissioningStatus(teamName: Option[TeamName], serviceType: Option[ServiceType])(implicit hc: HeaderCarrier): Future[List[CachedServiceCheck]] =
    httpClientV2
      .get(url"$serviceCommissioningBaseUrl/service-commissioning-status/cached-status?teamName=${teamName.map(_.asString)}&serviceType=${serviceType.map(_.asString)}")
      .execute[List[CachedServiceCheck]]

}
