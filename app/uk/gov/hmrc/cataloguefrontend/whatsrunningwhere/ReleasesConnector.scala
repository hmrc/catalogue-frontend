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

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{Instant, LocalDate, LocalTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class ReleasesConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext) {
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  private val serviceUrl: String = servicesConfig.baseUrl("releases-api")

  private implicit val wrwf = JsonCodecs.whatsRunningWhereReads
  private implicit val pf   = JsonCodecs.profileFormat


  def releases(profile: Option[Profile])(implicit hc: HeaderCarrier): Future[Seq[WhatsRunningWhere]] = {
    val url = s"$serviceUrl/releases-api/whats-running-where"
    val params = Seq(
      "profileName" -> profile.map(_.profileName.asString),
      "profileType" -> profile.map(_.profileType.asString)
    )
    httpClientV2
      .get(url"$url?$params")
      .execute[Seq[WhatsRunningWhere]]
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty
      }
  }

  def profiles()(implicit hc: HeaderCarrier): Future[Seq[Profile]] = {
    val url = url"$serviceUrl/releases-api/profiles"
    httpClientV2
      .get(url)
      .execute[Seq[Profile]]
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty
      }
  }

  def releasesForService(service: String)(implicit hc: HeaderCarrier): Future[WhatsRunningWhere] = {
    val url = url"$serviceUrl/releases-api/whats-running-where/$service"
    httpClientV2
      .get(url)
      .execute[Option[WhatsRunningWhere]]
      .map(_.getOrElse {
        logger.error(s"Service $service not found, returning placeholder whatsrunningwhere")
        WhatsRunningWhere(ServiceName(service), Nil)
      })
      .recover {
        case ex =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          throw ex
      }
  }

  private val paginatedHistoryReads: HttpReads[PaginatedDeploymentHistory] = {
    implicit val rf = JsonCodecs.deploymentHistoryReads
    for {
      history <- implicitly[HttpReads[Seq[DeploymentHistory]]]
      resp    <- HttpReads.ask.map(_._3)
      totals  =  resp.header("X-Total-Count").map(_.toLong).getOrElse(0L)
    } yield PaginatedDeploymentHistory(history, totals)
  }

  def deploymentHistory(
    environment: Environment,
    from   : Option[LocalDate] = None,
    to     : Option[LocalDate] = None,
    team   : Option[String]    = None,
    service: Option[String]    = None,
    skip   : Option[Int]       = None,
    limit  : Option[Int]       = None
  )(implicit hc: HeaderCarrier): Future[PaginatedDeploymentHistory] = {
    implicit val mr: HttpReads[PaginatedDeploymentHistory] = paginatedHistoryReads
    val params = Seq(
      "from"    -> from.map(from => DateTimeFormatter.ISO_INSTANT.format(from.atStartOfDay().toInstant(ZoneOffset.UTC))),
      "to"      -> to.map(to => DateTimeFormatter.ISO_INSTANT.format(to.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC))),
      "team"    -> team,
      "service" -> service,
      "skip"    -> skip.map(_.toString),
      "limit"   -> limit.map(_.toString)
    )

    httpClientV2
      .get(url"$serviceUrl/releases-api/deployments/${environment.asString}?$params")
      .execute[PaginatedDeploymentHistory]
  }

  def deploymentTimeline(
    service: String,
    from:    Instant,
    to:      Instant
  )(implicit
    hc: HeaderCarrier
  ): Future[Map[String, Seq[DeploymentTimelineEvent]]] = {
    implicit val dtr  = JsonCodecs.deploymentTimelineEventReads
    httpClientV2
      .get(url"$serviceUrl/releases-api/timeline/$service?from=$from&to=$to")
      .execute[Map[String, Seq[DeploymentTimelineEvent]]]
  }
}
