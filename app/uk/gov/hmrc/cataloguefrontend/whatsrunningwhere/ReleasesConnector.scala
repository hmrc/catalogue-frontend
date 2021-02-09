/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads}
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class ReleasesConnector @Inject()(
  http          : HttpClient,
  servicesConfig: ServicesConfig
)(
  implicit
  ec: ExecutionContext) {
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  private val serviceUrl: String = servicesConfig.baseUrl("releases-api")

  implicit val wrwf = JsonCodecs.whatsRunningWhereReads
  implicit val pf   = JsonCodecs.profileFormat
  implicit val sdf  = JsonCodecs.serviceDeploymentsFormat

  def releases(profile: Option[Profile], platform: Platform)(implicit hc: HeaderCarrier): Future[Seq[WhatsRunningWhere]] = {
    val baseUrl = s"$serviceUrl/releases-api/whats-running-where"
    val params = Seq(
      "profileName" -> profile.map(_.profileName.asString),
      "profileType" -> profile.map(_.profileType.asString),
      "platform"    -> platform.asString
    )
    http
      .GET[Seq[WhatsRunningWhere]](url"$baseUrl?$params")
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $baseUrl: ${ex.getMessage}", ex)
          Seq.empty
      }
  }

  def profiles(implicit hc: HeaderCarrier): Future[Seq[Profile]] = {
    val baseUrl = s"$serviceUrl/releases-api/profiles"
    http
      .GET[Seq[Profile]](url"$baseUrl")
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $baseUrl: ${ex.getMessage}", ex)
          Seq.empty
      }
  }

  def releasesForService(service: String)(implicit hc: HeaderCarrier): Future[WhatsRunningWhere] = {
    val baseUrl = s"$serviceUrl/releases-api/whats-running-where"
    http
      .GET[Option[WhatsRunningWhere]](url"$baseUrl/$service")
      .map(_.getOrElse {
        logger.error(s"Service $service not found, returning placeholder whatsrunningwhere")
        WhatsRunningWhere(ServiceName(service), Nil)
      })
      .recover {
        case ex =>
          logger.error(s"An error occurred when connecting to $baseUrl: ${ex.getMessage}", ex)
          throw ex
      }
  }

  def deploymentHistory(
    platform   : Platform,
    environment: Environment,
    from       : Option[Long]   = None,
    to         : Option[Long]   = None,
    team       : Option[String] = None,
    app        : Option[String] = None
  )(implicit
    hc: HeaderCarrier
  ): Future[Seq[Deployment]] = {
    implicit val rf = JsonCodecs.heritageDeploymentReads
    val params = Seq(
      "from"     -> from.map(_.toString),
      "to"       -> to.map(_.toString),
      "team"     -> team,
      "app"      -> app,
      "platform" -> Some(platform.asString)
    )
    http.GET[Seq[Deployment]](url"$serviceUrl/releases-api/deployments/${environment.asString}?$params")
  }
}
