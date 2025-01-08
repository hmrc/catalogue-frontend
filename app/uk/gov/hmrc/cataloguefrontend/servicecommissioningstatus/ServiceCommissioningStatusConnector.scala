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
import play.api.libs.json._
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.cataloguefrontend.connector.ServiceType
import uk.gov.hmrc.cataloguefrontend.model.{ServiceName, TeamName}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceCommissioningStatusConnector @Inject() (
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
)(using ExecutionContext):

  private val serviceCommissioningBaseUrl =
    servicesConfig.baseUrl("service-commissioning-status")

  def commissioningStatus(serviceName: ServiceName)(using HeaderCarrier): Future[List[Check]] =
    given Reads[Check] = Check.reads
    httpClientV2
      .get(url"$serviceCommissioningBaseUrl/service-commissioning-status/status/${serviceName.asString}")
      .execute[List[Check]]

  def allChecks()(using HeaderCarrier): Future[Seq[(String, FormCheckType)]] =
    httpClientV2
      .get(url"$serviceCommissioningBaseUrl/service-commissioning-status/checks")
      .execute[Seq[Map[String, FormCheckType]]]
      .map(_.flatMap(identity))

  def cachedCommissioningStatus(
    teamName       : Option[TeamName]     = None,
    serviceType    : Option[ServiceType]  = None,
    lifecycleStatus: Seq[LifecycleStatus] = Nil
  )(using
    HeaderCarrier
  ): Future[List[CachedServiceCheck]] =
    given Reads[CachedServiceCheck] = CachedServiceCheck.reads
    httpClientV2
      .get(url"$serviceCommissioningBaseUrl/service-commissioning-status/cached-status?teamName=${teamName.map(_.asString)}&serviceType=${serviceType.map(_.asString)}&lifecycleStatus=${lifecycleStatus.map(_.asString)}")
      .execute[List[CachedServiceCheck]]

  def setLifecycleStatus(
    serviceName    : ServiceName,
    lifecycleStatus: LifecycleStatus,
    username       : String
  )(using
    HeaderCarrier
  ): Future[Unit] =
    httpClientV2
      .post(url"$serviceCommissioningBaseUrl/service-commissioning-status/services/${serviceName.asString}/lifecycleStatus")
      .withBody(Json.obj("lifecycleStatus" -> lifecycleStatus.asString, "username" -> username))
      .execute[Unit]

  def getLifecycle(serviceName: ServiceName)(using HeaderCarrier): Future[Option[Lifecycle]] =
    given Reads[Lifecycle] = Lifecycle.reads
    httpClientV2
      .get(url"$serviceCommissioningBaseUrl/service-commissioning-status/services/${serviceName.asString}/lifecycleStatus")
      .execute[Option[Lifecycle]]

end ServiceCommissioningStatusConnector
