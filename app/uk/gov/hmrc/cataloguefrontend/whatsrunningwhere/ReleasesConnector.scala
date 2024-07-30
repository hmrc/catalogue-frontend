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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import play.api.Logger
import play.api.libs.json.Reads
import uk.gov.hmrc.cataloguefrontend.util.DateHelper.{atEndOfDayInstant, atStartOfDayInstant}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import java.time.{Instant, LocalDate}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class ReleasesConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using ExecutionContext):
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  private val serviceUrl: String = servicesConfig.baseUrl("releases-api")

  private given Reads[WhatsRunningWhere] = JsonCodecs.whatsRunningWhereReads
  private given Reads[Profile]           = JsonCodecs.profileFormat


  def releases(profile: Option[Profile])(using HeaderCarrier): Future[Seq[WhatsRunningWhere]] =
    val url = s"$serviceUrl/releases-api/whats-running-where"
    val params = Seq(
      "profileName" -> profile.map(_.profileName.asString),
      "profileType" -> profile.map(_.profileType.asString)
    )
    httpClientV2
      .get(url"$url?$params")
      .execute[Seq[WhatsRunningWhere]]
      .recover:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty

  def profiles()(using HeaderCarrier): Future[Seq[Profile]] =
    val url = url"$serviceUrl/releases-api/profiles"
    httpClientV2
      .get(url)
      .execute[Seq[Profile]]
      .recover:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty

  def releasesForService(service: ServiceName)(using HeaderCarrier): Future[WhatsRunningWhere] =
    val url = url"$serviceUrl/releases-api/whats-running-where/${service.asString}"
    httpClientV2
      .get(url)
      .execute[Option[WhatsRunningWhere]]
      .map(_.getOrElse(WhatsRunningWhere(service, Nil)))
      .recover:
        case ex =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          throw ex

  private val paginatedHistoryReads: HttpReads[PaginatedDeploymentHistory] =
    implicit val rf = JsonCodecs.deploymentHistoryReads
    for
      history <- summon[HttpReads[Seq[DeploymentHistory]]]
      resp    <- HttpReads.ask.map(_._3)
      totals  =  resp.header("X-Total-Count").map(_.toLong).getOrElse(0L)
    yield PaginatedDeploymentHistory(history, totals)

  def deploymentHistory(
    environment: Environment,
    from   : Option[LocalDate] = None,
    to     : Option[LocalDate] = None,
    team   : Option[String]    = None,
    service: Option[String]    = None,
    skip   : Option[Int]       = None,
    limit  : Option[Int]       = None
  )(using
    HeaderCarrier
  ): Future[PaginatedDeploymentHistory] =
    implicit val mr: HttpReads[PaginatedDeploymentHistory] = paginatedHistoryReads
    val params = Seq(
      "from"    -> from.map(from => from.atStartOfDayInstant.toString),
      "to"      -> to.map(to => to.atEndOfDayInstant.toString),
      "team"    -> team,
      "service" -> service,
      "skip"    -> skip.map(_.toString),
      "limit"   -> limit.map(_.toString)
    )

    httpClientV2
      .get(url"$serviceUrl/releases-api/deployments/${environment.asString}?$params")
      .execute[PaginatedDeploymentHistory]

  def deploymentTimeline(
    service: ServiceName,
    from:    Instant,
    to:      Instant
  )(using
    HeaderCarrier
  ): Future[Map[String, Seq[DeploymentTimelineEvent]]] =
    implicit val dtr  = JsonCodecs.deploymentTimelineEventReads
    httpClientV2
      .get(url"$serviceUrl/releases-api/timeline/${service.asString}?from=$from&to=$to")
      .execute[Map[String, Seq[DeploymentTimelineEvent]]]
      .recover {
        case e: UpstreamErrorResponse if e.statusCode == 404 =>
          logger.warn(s"Received 404 from API for timeline of service ${service.asString}")
          Map.empty[String, Seq[DeploymentTimelineEvent]]
      }

end ReleasesConnector
