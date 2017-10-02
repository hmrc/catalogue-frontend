/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.events

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.TeamMember

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Singleton
class ReadModelService @Inject()(eventService: EventService, userManagementConnector: UserManagementConnector) {

  type ServiceName = String
  type ServiceOwnerUserName = String

  private[events] var eventsCache = Map.empty[ServiceName, ServiceOwnerUserName]
  protected[events] var umpUsersCache = Seq.empty[TeamMember]

  def getDigitalServiceOwner(digitalService: String): Option[TeamMember] = umpUsersCache.find(_.username == eventsCache.get(digitalService))

  def getAllUsers = umpUsersCache

  def refreshEventsCache = {
     val eventualEvents = eventService.getAllEvents

     eventualEvents.map { events =>
      eventsCache = events.toStream.filter(_.eventType == EventType.ServiceOwnerUpdated)
        .sortBy(_.timestamp)
        .map(_.data.as[ServiceOwnerUpdatedEventData]).groupBy(_.service)
        .map { case (service, eventDataList) => (service, eventDataList.last.username) }
      eventsCache
    }

  }

  def refreshUmpCache: Future[Seq[TeamMember]] = {
    userManagementConnector.getAllUsersFromUMP().map {
      case Right(tms) =>
        Logger.info(s"Got ${tms.length} set of UMP users")
        umpUsersCache = tms
        umpUsersCache
      case Left(error) =>
        Logger.error(s"An error occurred getting users from ump: $error")
        Nil
    }
    
  }
}
