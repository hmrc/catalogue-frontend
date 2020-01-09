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

package uk.gov.hmrc.cataloguefrontend.shuttering

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Writes
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.UmpToken
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterConnector.ShutterEventsFilter
import uk.gov.hmrc.cataloguefrontend.util.UrlUtils.{encodePathParam, encodeQueryParam}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, Token}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterConnector @Inject()(
  http: HttpClient,
  serviceConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {

  private val shutterApiBaseUrl = serviceConfig.baseUrl("shutter-api") + "/shutter-api"

  private def urlStates(st: ShutterType, env: Environment) = s"$shutterApiBaseUrl/${encodePathParam(env.asString)}/${encodePathParam(st.asString)}/states"
  private val urlEvents: String                            = s"$shutterApiBaseUrl/events"
  private def urlOutagePages(env: Environment)             = s"$shutterApiBaseUrl/${encodePathParam(env.asString)}/outage-pages"
  private def urlFrontendRouteWarnings(env: Environment)   = s"$shutterApiBaseUrl/${encodePathParam(env.asString)}/frontend-route-warnings"

  private implicit val ssr = ShutterState.reads
  private implicit val ser = ShutterEvent.reads

  /**
    * GET
    * /shutter-api/{environment}/{serviceType}/states
    * Retrieves the current shutter states for all services in given environment
    */
  def shutterStates(st: ShutterType, env: Environment)(implicit hc: HeaderCarrier): Future[Seq[ShutterState]] =
    http.GET[Seq[ShutterState]](url = urlStates(st, env))

  /**
    * GET
    * /shutter-api/{environment}/{serviceType}/states/{serviceName}
    * Retrieves the current shutter states for the given service in the given environment
    */
  def shutterState(st: ShutterType, env: Environment, serviceName: String)(implicit hc: HeaderCarrier): Future[Option[ShutterState]] =
    http.GET[Option[ShutterState]](s"${urlStates(st, env)}/${encodePathParam(serviceName)}")

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
      .PUT[ShutterStatus, HttpResponse](s"${urlStates(st, env)}/${encodePathParam(serviceName)}", status)(
          implicitly[Writes[ShutterStatus]]
        , implicitly[HttpReads[HttpResponse]]
        , hc.copy(token = Some(Token(umpToken.value)))
        , implicitly[ExecutionContext]
        )
        .map(_ => ())
  }

  /**
    * GET
    * /shutter-api/{environment}/events
    * Retrieves the latest shutter events for all services for given environment
    */
  def latestShutterEvents(st: ShutterType, env: Environment)(implicit hc: HeaderCarrier): Future[Seq[ShutterStateChangeEvent]] =
    shutterEventsFrom(shutterEventsUrlFor(st, env) + "&namedFilter=latestByServiceName")

  /*
   * Latest shutter events for all services that are not 'auto-reconciled' events
   */
  def latestNonAutoReconciliationShutterEvents(st: ShutterType, env: Environment)(implicit hc: HeaderCarrier): Future[Seq[ShutterStateChangeEvent]] =
    shutterEventsFrom(shutterEventsUrlFor(st, env) + "&namedFilter=latestNonAutoReconciliationByServiceName")

  def shutterEventsByTimestampDesc(filter: ShutterEventsFilter)(implicit hc: HeaderCarrier): Future[Seq[ShutterStateChangeEvent]] =
    shutterEventsFrom(s"$urlEvents?type=${encodeQueryParam(EventType.ShutterStateChange.asString)}&${ShutterEventsFilter.asQuery(filter)}")

  private def shutterEventsFrom(url: String)(implicit hc: HeaderCarrier): Future[Seq[ShutterStateChangeEvent]] =
    http.GET[Seq[ShutterEvent]](url).map {
      _.flatMap(_.toShutterStateChangeEvent)
    }

  private def shutterEventsUrlFor(st: ShutterType, env: Environment): String =
    s"$urlEvents?data.environment=${encodeQueryParam(env.asString)}&data.shutterType=${encodeQueryParam(st.asString)}"

  /**
    * GET
    * /shutter-api/{environment}/outage-pages/{serviceName}
    * Retrieves the current shutter state for the given service in the given environment
    */
  def outagePage(env: Environment, serviceName: String)(
    implicit hc: HeaderCarrier): Future[Option[OutagePage]] = {
    implicit val ssf = OutagePage.reads
    http.GET[Option[OutagePage]](s"${urlOutagePages(env)}/${encodePathParam(serviceName)}")
  }

  /**
    * GET
    * /shutter-api/{environment}/frontend-route-warnings/{serviceName}
    * Retrieves the warnings (if any) for the given service in the given environment, based on parsing the mdtp-frontend-routes
    */
  def frontendRouteWarnings(env: Environment, serviceName: String)(
    implicit hc: HeaderCarrier): Future[Seq[FrontendRouteWarning]] = {
    implicit val r = FrontendRouteWarning.reads
    http
      .GET[Seq[FrontendRouteWarning]](s"${urlFrontendRouteWarnings(env)}/${encodePathParam(serviceName)}")
  }
}

object ShutterConnector {
  case class ShutterEventsFilter(environment: Environment, serviceName: Option[String])

  object ShutterEventsFilter {
    private [shuttering] def asQuery(filter: ShutterEventsFilter): String = {
      val queryParams = Seq(asQueryParam(key = "data.environment", value = filter.environment.asString)) ++
        filter.serviceName.map(asQueryParam(key = "data.serviceName", _))
      queryParams.mkString("&")
    }

    private def asQueryParam(key: String, value: String): String =
      s"$key=${encodeQueryParam(value)}"
  }
}