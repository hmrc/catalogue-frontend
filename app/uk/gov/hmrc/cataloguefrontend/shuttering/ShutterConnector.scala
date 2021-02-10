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

package uk.gov.hmrc.cataloguefrontend.shuttering

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Writes
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.UmpToken
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterConnector.ShutterEventsFilter
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpClient, HttpReads, UpstreamErrorResponse}
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterConnector @Inject()(
  http         : HttpClient,
  serviceConfig: ServicesConfig
)(implicit val ec: ExecutionContext
) {
  import HttpReads.Implicits._

  private val baseUrl = serviceConfig.baseUrl("shutter-api")

  private implicit val ssr = ShutterState.reads
  private implicit val ser = ShutterEvent.reads

  /**
    * GET
    * /shutter-api/{environment}/{serviceType}/states
    * Retrieves the current shutter states for all services in given environment
    */
  def shutterStates(st: ShutterType, env: Environment)(implicit hc: HeaderCarrier): Future[Seq[ShutterState]] =
    http.GET[Seq[ShutterState]](
      url"$baseUrl/shutter-api/${env.asString}/${st.asString}/states"
    )

  /**
    * GET
    * /shutter-api/{environment}/{serviceType}/states/{serviceName}
    * Retrieves the current shutter states for the given service in the given environment
    */
  def shutterState(st: ShutterType, env: Environment, serviceName: String)(implicit hc: HeaderCarrier): Future[Option[ShutterState]] =
    http.GET[Option[ShutterState]](
      url"$baseUrl/shutter-api/${env.asString}/${st.asString}/states/$serviceName"
    )

  /**
    * PUT
    * /shutter-api/{environment}/{serviceType}/states/{serviceName}
    * Shutters/un-shutters the service in the given environment
    */
  def updateShutterStatus(
      umpToken   : UmpToken
    , serviceName: String
    , st         : ShutterType
    , env        : Environment
    , status     : ShutterStatus
    )(implicit hc: HeaderCarrier): Future[Unit] = {
    implicit val isf = ShutterStatus.format
    http
      .PUT[ShutterStatus, Unit](
          url"$baseUrl/shutter-api/${env.asString}/${st.asString}/states/$serviceName",
          status
        )(
          implicitly[Writes[ShutterStatus]]
        , HttpReads.Implicits.throwOnFailure(implicitly[HttpReads[Either[UpstreamErrorResponse, Unit]]])
        , hc.copy(authorization = Some(Authorization(umpToken.value)))
        , implicitly[ExecutionContext]
        )
  }

  /**
    * GET
    * /shutter-api/{environment}/events
    * Retrieves the current shutter events for all services for given environment
    */
  def latestShutterEvents(st: ShutterType, env: Environment)(implicit hc: HeaderCarrier): Future[Seq[ShutterStateChangeEvent]] = {
    val queryParams = Seq(
      "type"             -> EventType.ShutterStateChange.asString,
      "namedFilter"      -> "latestByServiceName",
      "data.environment" -> env.asString,
      "data.shutterType" -> st.asString
    )
    http
      .GET[Seq[ShutterEvent]](url"$baseUrl/shutter-api/events?$queryParams")
      .map(_.flatMap(_.toShutterStateChangeEvent))
  }

  def shutterEventsByTimestampDesc(filter: ShutterEventsFilter)(implicit hc: HeaderCarrier): Future[Seq[ShutterStateChangeEvent]] =
    http
      .GET[Seq[ShutterEvent]](
        url"$baseUrl/shutter-api/events?type=${EventType.ShutterStateChange.asString}&${filter.asQueryParams}"
      )
      .map(_.flatMap(_.toShutterStateChangeEvent))

  /**
    * GET
    * /shutter-api/{environment}/outage-pages/{serviceName}
    * Retrieves the current shutter state for the given service in the given environment
    */
  def outagePage(env: Environment, serviceName: String)(
    implicit hc: HeaderCarrier): Future[Option[OutagePage]] = {
    implicit val ssf = OutagePage.reads
    http.GET[Option[OutagePage]](
      url"$baseUrl/shutter-api/${env.asString}/outage-pages/$serviceName"
    )
  }

  /**
    * GET
    * /shutter-api/{environment}/frontend-route-warnings/{serviceName}
    * Retrieves the warnings (if any) for the given service in the given environment, based on parsing the mdtp-frontend-routes
    */
  def frontendRouteWarnings(env: Environment, serviceName: String)(
    implicit hc: HeaderCarrier): Future[Seq[FrontendRouteWarning]] = {
    implicit val r = FrontendRouteWarning.reads
    http.GET[Seq[FrontendRouteWarning]](
      url"$baseUrl/shutter-api/${env.asString}/frontend-route-warnings/$serviceName"
    )
  }
}

object ShutterConnector {
  case class ShutterEventsFilter(
    environment: Environment,
    serviceName: Option[String]
  ) {
    def asQueryParams: Seq[(String, String)] =
      Seq("data.environment" -> environment.asString) ++
        serviceName.map("data.serviceName" -> _)
  }
}
