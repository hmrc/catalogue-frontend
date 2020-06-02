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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.cataloguefrontend.model.Environment.Production
import uk.gov.hmrc.cataloguefrontend.util.UrlUtils
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class ReleasesConnector @Inject()(
  http          : HttpClient,
  servicesConfig: ServicesConfig
)(implicit
  ec: ExecutionContext
) {
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  private val serviceUrl: String = servicesConfig.baseUrl("releases-api")

  implicit val wrwf = JsonCodecs.whatsRunningWhereReads
  implicit val pf   = JsonCodecs.profileFormat
  implicit val sdf  = JsonCodecs.serviceDeploymentsFormat

  def releases(profile: Option[Profile])(implicit hc: HeaderCarrier): Future[Seq[WhatsRunningWhere]] = {
    val baseUrl = s"$serviceUrl/releases-api/whats-running-where"
    val params  = profileQueryParams(profile)
    http
      .GET[Seq[WhatsRunningWhere]](baseUrl, params)
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $baseUrl: ${ex.getMessage}", ex)
          Seq.empty
      }
  }

  def profiles(implicit hc: HeaderCarrier): Future[Seq[Profile]] = {
    val baseUrl = s"$serviceUrl/releases-api/profiles"
    http
      .GET[Seq[Profile]](baseUrl)
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $baseUrl: ${ex.getMessage}", ex)
          Seq.empty
      }
  }

  def ecsReleases(profile: Option[Profile])(implicit hc: HeaderCarrier): Future[Seq[ServiceDeployment]] = {
    val baseUrl = s"$serviceUrl/releases-api/ecs-deployment-events"
    val params  = profileQueryParams(profile)
    http
      .GET[Seq[ServiceDeployment]](baseUrl, params)
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $baseUrl: ${ex.getMessage}", ex)
          Seq.empty
      }
  }

  private def profileQueryParams(profile: Option[Profile]): Seq[(String, String)] =
    UrlUtils.buildQueryParams(
      "profileName" -> profile.map(_.profileName.asString),
      "profileType" -> profile.map(_.profileType.asString))

  def releasesForService(service: String)(implicit hc: HeaderCarrier): Future[WhatsRunningWhere] = {
    val baseUrl = s"$serviceUrl/releases-api/whats-running-where/$service"

    http
      .GET[Option[WhatsRunningWhere]](baseUrl)
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
    from: Option[Long]   = None,
    to  : Option[Long]   = None,
    team: Option[String] = None,
    app : Option[String] = None
  )(implicit
    hc: HeaderCarrier
  ): Future[Seq[HeritageDeployment]] = {

    implicit val rf = JsonCodecs.heritageDeployment

    val baseUrl = s"$serviceUrl/releases-api/deployments/${Production.asString}"
    val params  = UrlUtils.buildQueryParams(
      "from" -> from.map(_.toString),
      "to"   -> to.map(_.toString),
      "team" -> team,
      "app"  -> app)

    http.GET[Seq[HeritageDeployment]](baseUrl, queryParams = params)
  }
}
