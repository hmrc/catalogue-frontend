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

package uk.gov.hmrc.cataloguefrontend.shuttering

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Json, JsValue, JsNull, JsString, Reads, Writes}
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, Environment, ServiceName, TeamName}
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterConnector.ShutterEventsFilter
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.internalauth.client.AuthorizationToken
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterConnector @Inject() (
  httpClientV2 : HttpClientV2,
  serviceConfig: ServicesConfig
)(using
  ExecutionContext
):
  import HttpReads.Implicits._

  private val baseUrl = serviceConfig.baseUrl("shutter-api")

  /**
    * GET
    * /shutter-api/{environment}/{serviceType}/states
    * Retrieves the current shutter states for all services in given environment
    */
  def shutterStates(
    st            : ShutterType,
    env           : Environment,
    teamName      : Option[TeamName],
    digitalService: Option[DigitalService],
    serviceName   : Option[ServiceName]
  )(using
    HeaderCarrier
  ): Future[Seq[ShutterState]] =
    given Reads[ShutterState] = ShutterState.reads
    httpClientV2
      .get(url"$baseUrl/shutter-api/${env.asString}/${st.asString}/states?serviceName=${serviceName.map(_.asString)}&teamName=${teamName.map(_.asString)}&digitalService=${digitalService.map(_.asString)}")
      .execute[Seq[ShutterState]]

  /**
    * PUT
    * /shutter-api/{environment}/{serviceType}/states/{serviceName}
    * Shutters/un-shutters the service in the given environment
    */
  def updateShutterStatus(
    token      : AuthorizationToken,
    serviceName: ServiceName,
    context    : Option[String],
    st         : ShutterType,
    env        : Environment,
    status     : ShutterStatus
  )(using
    HeaderCarrier
  ): Future[Unit] =
    given Writes[ShutterStatus] = ShutterStatus.format
    httpClientV2
      .put(url"$baseUrl/shutter-api/${env.asString}/${st.asString}/states/${serviceName.asString}")
      .setHeader("Authorization" -> token.value)
      .withBody(
        Json.obj(
          "context"       -> context.fold(JsNull: JsValue)(JsString(_))
        , "shutterStatus" -> Json.toJson(status)
        )
      )
      .execute[Unit](HttpReads.Implicits.throwOnFailure(summon[HttpReads[Either[UpstreamErrorResponse, Unit]]]), summon[ExecutionContext])

  /**
    * GET
    * /shutter-api/{environment}/events
    * Retrieves the current shutter events for all services for given environment
    */
  def latestShutterEvents(st: ShutterType, env: Environment)(using HeaderCarrier): Future[Seq[ShutterStateChangeEvent]] =
    val queryParams = Seq(
      "type"             -> EventType.ShutterStateChange.asString,
      "namedFilter"      -> "latestByServiceName",
      "data.environment" -> env.asString,
      "data.shutterType" -> st.asString
    )
    given Reads[ShutterEvent] = ShutterEvent.reads
    httpClientV2
      .get(url"$baseUrl/shutter-api/events?$queryParams")
      .execute[Seq[ShutterEvent]]
      .map(_.flatMap(_.toShutterStateChangeEvent))

  def shutterEventsByTimestampDesc(
    filter: ShutterEventsFilter,
    limit : Option[Int]         = None,
    offset: Option[Int]         = None
  )(using
    HeaderCarrier
  ): Future[Seq[ShutterStateChangeEvent]] =
    given Reads[ShutterEvent] = ShutterEvent.reads
    httpClientV2
      .get(url"$baseUrl/shutter-api/events?type=${EventType.ShutterStateChange.asString}&${filter.asQueryParams}&limit=${limit.getOrElse(2500)}&offset=${offset.getOrElse(0)}")
      .execute[Seq[ShutterEvent]]
      .map(_.flatMap(_.toShutterStateChangeEvent))

  /**
    * GET
    * /shutter-api/{environment}/outage-pages/{serviceName}
    * Retrieves the current shutter state for the given service in the given environment
    */
  def outagePage(serviceName: ServiceName)(using HeaderCarrier): Future[Option[OutagePage]] =
    given Reads[OutagePage] = OutagePage.reads
    httpClientV2
      .get(url"$baseUrl/shutter-api/outage-pages/${serviceName.asString}")
      .execute[Option[OutagePage]]

  /**
    * GET
    * /shutter-api/{environment}/frontend-route-warnings/{serviceName}
    * Retrieves the warnings (if any) for the given service in the given environment, based on parsing the mdtp-frontend-routes
    */
  def frontendRouteWarnings(env: Environment, serviceName: ServiceName)(using HeaderCarrier): Future[Seq[FrontendRouteWarning]] =
    given Reads[FrontendRouteWarning] = FrontendRouteWarning.reads
    httpClientV2
      .get(url"$baseUrl/shutter-api/${env.asString}/frontend-route-warnings/${serviceName.asString}")
      .execute[Seq[FrontendRouteWarning]]

end ShutterConnector

object ShutterConnector:
  case class ShutterEventsFilter(
    environment: Environment,
    serviceName: Option[ServiceName]
  ):
    def asQueryParams: Seq[(String, String)] =
      ("data.environment" -> environment.asString)
        +: serviceName.map("data.serviceName" -> _.asString).toSeq
