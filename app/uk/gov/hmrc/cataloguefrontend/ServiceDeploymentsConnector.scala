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

import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.cataloguefrontend.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost, HttpResponse}

import scala.concurrent.Future

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

case class WhatIsRunningWhere(applicationName: String, environments: Seq[String])
object WhatIsRunningWhere {
  implicit val whatsRunningWhereFormat = Json.format[WhatIsRunningWhere]
}

trait ServiceDeploymentsConnector extends ServicesConfig {

  val http: HttpGet with HttpPost
  def servicesDeploymentsBaseUrl: String
  def whatIsRunningWhereBaseUrl: String

  import uk.gov.hmrc.play.http.HttpReads._
  import JavaDateTimeJsonFormatter._
  import WhatIsRunningWhere._

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

  def getWhatIsRunningWhere(applicationName: String)(implicit hc: HeaderCarrier): Future[Seq[WhatIsRunningWhere]] = {
    val url = s"$whatIsRunningWhereBaseUrl/$applicationName"

    http.GET[HttpResponse](url).map { r =>
      r.status match {
        case 200 => r.json.as[Seq[WhatIsRunningWhere]]
        case 404 => Seq()
      }
    }.recover {
      case ex =>
        Logger.error(s"An error occurred when connecting to $servicesDeploymentsBaseUrl: ${ex.getMessage}", ex)
        Seq.empty
    }
  }

}

object ServiceDeploymentsConnector extends ServiceDeploymentsConnector {
  override val http = WSHttp
  override def servicesDeploymentsBaseUrl: String = baseUrl("service-deployments") + "/api/deployments"
  override def whatIsRunningWhereBaseUrl: String = baseUrl("service-deployments") + "/api/whatsrunningwhere"
}
