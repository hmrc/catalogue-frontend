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

import java.time.LocalDateTime

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterService @Inject()(shutterConnector: ShutterConnector)(implicit val ec: ExecutionContext) {

  def getShutterStates(implicit hc: HeaderCarrier): Future[Seq[ShutterState]] =
    shutterConnector.shutterStates

  def updateShutterStatus(
      serviceName: String
    , env          : Environment
    , status       : ShutterStatus
    )(implicit hc: HeaderCarrier): Future[Unit] =
      shutterConnector.updateShutterStatus(serviceName, env, status)

  def outagePageByAppAndEnv(serviceName: String, env: Environment)(implicit hc: HeaderCarrier): Future[Option[OutagePage]] =
    shutterConnector.outagePageByAppAndEnv(serviceName, env)

  def findCurrentState(env: Environment)(implicit hc: HeaderCarrier): Future[Seq[ShutterStateChangeEvent]] =
    for {
      events <- shutterConnector.latestShutterEvents(env)
      sorted =  events.sortWith {
                  case (l, r) => l.status.value == ShutterStatusValue.Shuttered ||
                                 l.serviceName <  r.serviceName
                }
    } yield sorted

  /** Creates an [[OutagePageStatus]] for each service based on the contents of [[OutagePage]] */
  def toOutagePageStatus(serviceNames: Seq[String], outagePages: List[OutagePage]): Seq[OutagePageStatus] =
    serviceNames.map { serviceName =>
      outagePages.find(_.serviceName == serviceName) match {
        case Some(outagePage) if outagePage.warnings.nonEmpty =>
          OutagePageStatus(
              serviceName = serviceName
            , warning     = Some(( outagePage.warnings.map(_.message).mkString("<br/>")
                                 , outagePage.warnings.head.name match {
                                     case "UnableToRetrievePage"        => "Default outage page will be displayed."
                                     case "MalformedHTML"               => "Outage page will be sent as is, without updating templates."
                                     case "DuplicateTemplateElementIDs" => "All matching elements will be updated"
                                   }
                                ))
            )
        case Some(outagePage) if outagePage.templatedMessages.isEmpty =>
          OutagePageStatus(
              serviceName = serviceName
            , warning     = Some(( "No templatedMessage Element in outage-page"
                                 , "Outage page will be sent as is."
                                ))
            )
        case Some(outagePage) if outagePage.templatedMessages.length > 1 =>
          OutagePageStatus(
              serviceName = serviceName
            , warning     = Some(( "Multiple templatedMessage Element in outage-page"
                                 , "All matching elements will be updated."
                                ))
            )
        case Some(_) =>
          OutagePageStatus(
              serviceName = serviceName
            , warning     = None
            )
        case None =>
          OutagePageStatus(
              serviceName = serviceName
            , warning     = Some(( "No templatedMessage Element no outage-page"
                                 , "Default outage page will be displayed."
                                ))
            )
      }
    }
}
