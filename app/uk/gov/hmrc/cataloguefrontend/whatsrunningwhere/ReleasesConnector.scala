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
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.cataloguefrontend.util.DateHelper.{atEndOfDayInstant, atStartOfDayInstant}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, DigitalService, ServiceName, TeamName}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import java.time.{Instant, LocalDate}
import scala.concurrent.{ExecutionContext, Future}

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

  def releases(
    teamName      : Option[TeamName]       = None
  , digitalService: Option[DigitalService] = None
  , sm2Profile    : Option[String]         = None
  )(using HeaderCarrier): Future[Seq[WhatsRunningWhere]] =
    httpClientV2
      .get(url"$serviceUrl/releases-api/whats-running-where?teamName=${teamName.map(_.asString)}&digitalService=${digitalService.map(_.asString)}&sm2Profile=$sm2Profile")
      .execute[Seq[WhatsRunningWhere]]

  def profiles()(using HeaderCarrier): Future[Seq[Profile]] =
    httpClientV2
      .get(url"$serviceUrl/releases-api/profiles")
      .execute[Seq[Profile]]

  def releasesForService(service: ServiceName)(using HeaderCarrier): Future[WhatsRunningWhere] =
    httpClientV2
      .get(url"$serviceUrl/releases-api/whats-running-where/${service.asString}")
      .execute[Option[WhatsRunningWhere]]
      .map(_.getOrElse(WhatsRunningWhere(service, Nil)))

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

  private given Reads[ReleasesConnector.ApiService] = ReleasesConnector.apiServiceReads

  def apiServices(environment: Environment)(using HeaderCarrier): Future[Seq[ReleasesConnector.ApiService]] =
    httpClientV2
      .get(url"$serviceUrl/releases-api/apis/${environment.asString}")
      .execute[Seq[ReleasesConnector.ApiService]]

end ReleasesConnector

object ReleasesConnector:
  final case class ApiService(
    serviceName: ServiceName
  , context    : String
  , environment: Environment
  )

  private val apiServiceReads: Reads[ApiService] =
    ( (__ \ "serviceName").read[ServiceName]
    ~ (__ \ "context"    ).read[String]
    ~ (__ \ "environment").read[Environment]
    )(ApiService.apply _)
