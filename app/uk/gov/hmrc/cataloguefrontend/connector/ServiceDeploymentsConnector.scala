/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.{LocalDate, LocalDateTime}

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsValue, Json, OFormat, Reads}
import uk.gov.hmrc.cataloguefrontend.connector.model.Version
import uk.gov.hmrc.cataloguefrontend.{DateHelper, JavaDateTimeJsonFormatter}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

case class Deployer(name: String, deploymentDate: LocalDateTime)

case class Release(
  name: String,
  productionDate: LocalDateTime,
  creationDate: Option[LocalDateTime] = None,
  interval: Option[Long]              = None,
  leadTime: Option[Long]              = None,
  version: String,
  deployers: Seq[Deployer] = Seq.empty) {

  lazy val latestDeployer: Option[Deployer] = {
    import DateHelper._
    deployers.sortBy(_.deploymentDate.epochSeconds).lastOption
  }
}

final case class EnvironmentMapping(name: String, releasesAppId: String)
object EnvironmentMapping {
  implicit val environmentFormat = Json.format[EnvironmentMapping]
}

final case class DeploymentVO(environmentMapping: EnvironmentMapping, datacentre: String, version: Version)

object DeploymentVO {
  implicit val environmentFormat = {
    implicit val vf = Version.format
    Json.format[DeploymentVO]
  }
}

case class ServiceDeploymentInformation(serviceName: String, deployments: Seq[DeploymentVO])
object ServiceDeploymentInformation {
  implicit val whatsRunningWhereFormat = Json.format[ServiceDeploymentInformation]
}

@Singleton
class ServiceDeploymentsConnector @Inject()(
  http          : HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {

  private val serviceUrl: String                 = servicesConfig.baseUrl("service-deployments")
  private val servicesDeploymentsBaseUrl: String = s"$serviceUrl/api/deployments"

  import ServiceDeploymentInformation._
  import uk.gov.hmrc.http.HttpReads._

  private implicit val dateTimeReads: Reads[LocalDateTime] = JavaDateTimeJsonFormatter.localDateTimeReads
  private implicit val dateReads: Reads[LocalDate]         = JavaDateTimeJsonFormatter.localDateReads
  private implicit val deployerFormat: OFormat[Deployer]   = Json.format[Deployer]
  private implicit val deploymentsFormat: Reads[Release]   = Json.reads[Release]

  def getDeployments(serviceNames: Set[String])(implicit hc: HeaderCarrier): Future[Seq[Release]] =
    http
      .POST[JsValue, HttpResponse](
        servicesDeploymentsBaseUrl,
        Json.arr(serviceNames.toSeq.map(toJsFieldJsValueWrapper(_)): _*))
      .map { r =>
        r.status match {
          case 200 => r.json.as[Seq[Release]]
          case 404 => Seq()
        }
      }
      .recover {
        case ex =>
          Logger.error(s"An error occurred when connecting to $servicesDeploymentsBaseUrl: ${ex.getMessage}", ex)
          Seq.empty
      }

  def getDeployments(serviceName: Option[String] = None)(implicit hc: HeaderCarrier): Future[Seq[Release]] =
    http
      .GET[HttpResponse](serviceName.fold(servicesDeploymentsBaseUrl)(name => s"$servicesDeploymentsBaseUrl/$name"))
      .map { r =>
        r.status match {
          case 200 => r.json.as[Seq[Release]]
          case 404 => Seq()
        }
      }
      .recover {
        case ex =>
          Logger.error(s"An error occurred when connecting to $servicesDeploymentsBaseUrl: ${ex.getMessage}", ex)
          Seq.empty
      }

  def getWhatIsRunningWhere(serviceName: String)(
    implicit hc: HeaderCarrier): Future[ServiceDeploymentInformation] = {
    val url = s"$serviceUrl/api/whatsrunningwhere/$serviceName"

    http
      .GET[HttpResponse](url)
      .map { r =>
        r.status match {
          case 200 => r.json.as[ServiceDeploymentInformation]
        }
      }
      .recover {
        case _: NotFoundException =>
          ServiceDeploymentInformation(serviceName, Nil) // 404 if the service has had no deployments
        case ex =>
          Logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          throw ex
      }
  }
}
