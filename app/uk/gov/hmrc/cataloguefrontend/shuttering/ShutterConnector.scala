/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.Logger
import play.api.libs.json.Reads
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterConnector @Inject()(
    http         : HttpClient
  , serviceConfig: ServicesConfig
  )(implicit val ec: ExecutionContext){

  private val urlStates     : String = s"${serviceConfig.baseUrl("shutter-api")}/shutter-api/states"
  private val urlEvents     : String = s"${serviceConfig.baseUrl("shutter-api")}/shutter-api/events"
  private val urlOutagePages: String = s"${serviceConfig.baseUrl("shutter-api")}/shutter-api/outage-pages"

  private implicit val ssr = ShutterState.reads
  private implicit val ser = ShutterEvent.reads

  /**
    * GET
    * /shutter-api/states
    * Retrieves the current shutter states for all applications in all environments
    */
  def shutterStates()(implicit hc: HeaderCarrier): Future[Seq[ShutterState]] =
    http.GET[Seq[ShutterState]](url = urlStates)

  /**
    * GET
    * /shutter-api/events
    * Retrieves the current shutter events for all applications for given environment
    */
  def latestShutterEvents(env: Environment)(implicit hc: HeaderCarrier): Future[Seq[ShutterStateChangeEvent]] =
    http.GET[Seq[ShutterEvent]](url = s"$urlEvents?type=${EventType.ShutterStateChange.asString}&namedFilter=latestByServiceName&data.environment=${env.asString}")
      .map(_.flatMap(_.toShutterStateChangeEvent))

  /**
    * GET
    * /shutter-api/states/{appName}
    * Retrieves the current shutter states for the given application in all environments
    */
  def shutterStateByApp(appName: String)(implicit hc: HeaderCarrier): Future[Option[ShutterState]] =
    http.GET[Option[ShutterState]](s"$urlStates/$appName")


  /**
    * GET
    * /shutter-api/states/{appName}/{environment}
    * Retrieves the current shutter state for the given application in the given environment
    */
  def shutterStatusByAppAndEnv(appName: String, env: Environment)(implicit hc: HeaderCarrier): Future[Option[ShutterStatus]] = {
    implicit val ssf = ShutterStatus.format
    http.GET[Option[ShutterStatus]](s"$urlStates/$appName/${env.asString}")
  }


  /**
    * PUT
    * /shutter-api/states/{appName}/{environment}
    * Shutters/un-shutters the application in the given environment
    */
  def updateShutterStatus(
      appName      : String
    , env          : Environment
    , status       : ShutterStatus
    , reason       : String
    , outageMessage: String
    )(implicit hc: HeaderCarrier): Future[Unit] = {
    implicit val isf = ShutterStatus.format

    implicit val ur = new uk.gov.hmrc.http.HttpReads[Unit] {
      def read(method: String, url: String, response: uk.gov.hmrc.http.HttpResponse): Unit = ()
    }

    // TODO at some point, payload will take reason/outageMessage

    http.PUT[ShutterStatus, Unit](s"$urlStates/$appName/${env.asString}", status)
  }


  /**
    * GET
    * /shutter-api/outage-pages/{appName}/{environment}
    * Retrieves the current shutter state for the given application in the given environment
    */
  def outagePageByAppAndEnv(appName: String, env: Environment)(implicit hc: HeaderCarrier): Future[Option[OutagePage]] = {
    implicit val ssf = OutagePage.reads
    http.GET[Option[OutagePage]](s"$urlOutagePages/$appName/${env.asString}")
  }
}
