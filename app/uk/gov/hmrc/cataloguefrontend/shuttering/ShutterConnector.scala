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
import scala.util.control.NonFatal

@Singleton
class ShutterConnector @Inject()(
    http         : HttpClient
  , serviceConfig: ServicesConfig
  )(implicit val ec: ExecutionContext){

  private val urlStates: String = s"${serviceConfig.baseUrl("shutter-api")}/shutter-api/states"
  private val urlEvents: String = s"${serviceConfig.baseUrl("shutter-api")}/shutter-api/events"

  private implicit val shutterStateReads: Reads[ShutterState] = ShutterState.reads
  private implicit val shutterEventReads: Reads[ShutterEvent] = ShutterEvent.reads

  /**
    * GET
    * /shutter-api/states
    * Retrieves the current shutter states for all applications in all environments
    */
  def shutterStates()(implicit hc: HeaderCarrier): Future[Seq[ShutterState]] =
    http.GET[Seq[ShutterState]](url = urlStates)
    .recover {
      case NonFatal(ex) =>
        Logger.error(s"An error occurred when connecting to $urlStates: ${ex.getMessage}", ex)
        Seq.empty
    }


  /**
    * GET
    * /shutter-api/events
    * Retrieves the current shutter events for all applications in all environments
    */
  def latestShutterEvents()(implicit hc: HeaderCarrier): Future[Seq[ShutterEvent]] =
    http.GET[Seq[ShutterEvent]](url = urlEvents)
      .recover {
        case NonFatal(ex) =>
          Logger.error(s"An error occurred when connecting to $urlEvents: ${ex.getMessage}", ex)
          Seq.empty
      }


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
  def shutterStateByAppAndEnv(appName: String, env: Environment)(implicit hc: HeaderCarrier): Future[Option[IsShuttered]] = {
    implicit val isf = IsShuttered.format
    http.GET[Option[IsShuttered]](s"$urlStates/$appName/${env.asString}")
  }


  /**
    * PUT
    * /shutter-api/states/{appName}/{environment}
    * Shutters/un-shutters the application in the given environment
    */
  def updateShutterState(appName: String, env: Environment, isShuttered: IsShuttered)(implicit hc: HeaderCarrier): Future[Unit] = {
    implicit val isf = IsShuttered.format

    implicit val nr = new uk.gov.hmrc.http.HttpReads[Unit] {
      def read(method: String, url: String, response: uk.gov.hmrc.http.HttpResponse): Unit = ()
    }

    http.PUT[IsShuttered, Unit](s"$urlStates/$appName/${env.asString}", isShuttered)
  }
}
