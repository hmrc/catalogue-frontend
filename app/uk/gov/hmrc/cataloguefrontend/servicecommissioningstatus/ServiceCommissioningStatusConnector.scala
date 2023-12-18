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

  import ServiceCommissioningStatusConnector.ServiceStatusType

  private val serviceCommissioningBaseUrl = servicesConfig.baseUrl("service-commissioning-status")

  def commissioningStatus(serviceName: String)(implicit hc: HeaderCarrier): Future[Option[List[Check]]] = {
    implicit val cReads = Check.reads
    httpClientV2
      .get(url"$serviceCommissioningBaseUrl/service-commissioning-status/status/$serviceName")
      .execute[Option[List[Check]]]
  }

  def allChecks()(implicit hc: HeaderCarrier): Future[Seq[(String, FormCheckType)]] = {
    implicit val fctReads = FormCheckType.reads
    httpClientV2
      .get(url"$serviceCommissioningBaseUrl/service-commissioning-status/checks")
      .execute[Seq[Map[String, FormCheckType]]]
      .map(_.flatMap(_.map { case (k, v ) => (k, v) }))
  }

  def cachedCommissioningStatus(
    teamName   : Option[TeamName],
    serviceType: Option[ServiceType]
  )(implicit
    hc: HeaderCarrier
  ): Future[List[CachedServiceCheck]] = {
    implicit val cscReads = CachedServiceCheck.reads
    httpClientV2
      .get(url"$serviceCommissioningBaseUrl/service-commissioning-status/cached-status?teamName=${teamName.map(_.asString)}&serviceType=${serviceType.map(_.asString)}")
      .execute[List[CachedServiceCheck]]
  }

  def setServiceStatus(serviceName: String, serviceStatusType: ServiceStatusType)(implicit hc: HeaderCarrier): Future[Unit] = {
    httpClientV2
      .post(url"$serviceCommissioningBaseUrl/service-commissioning-status/lifecycle/$serviceName/status")
      .withBody(Json.obj("status" -> serviceStatusType.asString))
      .execute[Unit]
  }

  def status(serviceName: String)(implicit hc: HeaderCarrier): Future[Option[ServiceStatusType]] = {
    implicit val serviceStatusTypeFormat = ServiceStatusType.format
    httpClientV2
      .get(url"$serviceCommissioningBaseUrl/service-commissioning-status/lifecycle/$serviceName/status")
      .execute[Option[ServiceStatusType]]
  }
}

object ServiceCommissioningStatusConnector {
  sealed trait ServiceStatusType { val asString: String }

  object ServiceStatusType {
    object BeingDecommissioned extends ServiceStatusType { val asString: String = "Being decommissioned" }
    object Archived            extends ServiceStatusType { val asString: String = "Archived" }
    object Deprecated          extends ServiceStatusType { val asString: String = "Deprecated" }
    object Active              extends ServiceStatusType { val asString: String = "Active" }

    val values: List[ServiceStatusType] = List(BeingDecommissioned, Archived, Deprecated, Active)

    def parse(s: String): Either[String, ServiceStatusType] =
      values
        .find(_.asString == s)
        .toRight(s"Invalid service status - should be one of: ${values.map(_.asString).mkString(", ")}")

    val format: Format[ServiceStatusType] =
      new Format[ServiceStatusType] {
        override def reads(json: JsValue): JsResult[ServiceStatusType] =
          json match {
            case JsString(s) => parse(s).fold(msg => JsError(msg), rt => JsSuccess(rt))
            case _           => JsError("String value expected")
          }

        override def writes(rt: ServiceStatusType): JsValue =
          JsString(rt.asString)
      }
  }
}
