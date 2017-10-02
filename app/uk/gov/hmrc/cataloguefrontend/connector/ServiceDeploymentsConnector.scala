/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

/*
 * Copyright 2016 HM Revenue & Customs
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

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}

import play.api.libs.json.Json
import play.api.{Configuration, Logger, Environment => PlayEnvironment}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future
import scala.util.{Success, Try}

case class Deployer(name : String, deploymentDate: LocalDateTime)

case class Release(name: String,
                   productionDate: java.time.LocalDateTime,
                    creationDate: Option[java.time.LocalDateTime] = None,
                    interval: Option[Long] = None,
                    leadTime: Option[Long] = None,
                    version: String,
                    deployers : Seq[Deployer] = Seq.empty
                  ){

  import DateHelper._
  val latestDeployer = deployers.sortBy(_.deploymentDate.epochSeconds).lastOption
}

final case class EnvironmentMapping(name: String, releasesAppId: String)
object EnvironmentMapping {
  implicit val environmentFormat = Json.format[EnvironmentMapping]
}

final case class DeploymentVO(environmentMapping: EnvironmentMapping, datacentre: String, version: String)

object DeploymentVO {
  implicit val environmentFormat = Json.format[DeploymentVO]
}

case class ServiceDeploymentInformation(serviceName: String, deployments: Seq[DeploymentVO])
object ServiceDeploymentInformation {
  implicit val whatsRunningWhereFormat = Json.format[ServiceDeploymentInformation]
}


@Singleton
class ServiceDeploymentsConnector @Inject()(http : HttpClient, override val runModeConfiguration:Configuration, environment : PlayEnvironment) extends ServicesConfig {

  def servicesDeploymentsBaseUrl: String = baseUrl("service-deployments") + "/api/deployments"
  def whatIsRunningWhereBaseUrl: String = baseUrl("service-deployments") + "/api/whatsrunningwhere"

  override protected def mode = environment.mode

  import _root_.uk.gov.hmrc.http.HttpReads._
  import JavaDateTimeJsonFormatter._
  import ServiceDeploymentInformation._

  implicit val deployerFormat = Json.format[Deployer]
  
  implicit val deploymentsFormat = Json.reads[Release]



  def getDeployments(serviceNames: Iterable[String])(implicit hc: HeaderCarrier): Future[Seq[Release]] = {
    val url =  s"$servicesDeploymentsBaseUrl"

    http.POST[Seq[String],HttpResponse](url, serviceNames.toSeq).map { r =>
      r.status match {
        case 200 => r.json.as[Seq[Release]]
        case 404 => Seq()
      }
    }.recover {
      case ex =>
        Logger.error(s"An error occurred when connecting to $servicesDeploymentsBaseUrl: ${ex.getMessage}", ex)
        Seq.empty
    }
  }

  def getDeployments(serviceName: Option[String] = None)(implicit hc: HeaderCarrier): Future[Seq[Release]] = {
    val url = serviceName.fold(servicesDeploymentsBaseUrl)(name => s"$servicesDeploymentsBaseUrl/$name")

    http.GET[HttpResponse](url).map { r =>
      r.status match {
        case 200 => r.json.as[Seq[Release]]
        case 404 => Seq()
      }
    }.recover {
      case ex =>
        Logger.error(s"An error occurred when connecting to $servicesDeploymentsBaseUrl: ${ex.getMessage}", ex)
        Seq.empty
    }
  }


  def getWhatIsRunningWhere(serviceName: String)(implicit hc: HeaderCarrier): Future[Either[Throwable, ServiceDeploymentInformation]] = {
    val url = s"$whatIsRunningWhereBaseUrl/$serviceName"

    http.GET[HttpResponse](url).map { r =>
      r.status match {
        case 200 =>
          Try(r.json.as[ServiceDeploymentInformation]).transform(s => Success(Right(s)), f => Success(Left(f))).get
      }
    }.recover {
      case _: NotFoundException => Right(ServiceDeploymentInformation(serviceName, Nil)) // 404 if the service has had no deployments
      case ex =>
        Logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
        Left(ex)
    }
  }

}
