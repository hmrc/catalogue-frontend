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

import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.TeamMember

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


class ReadModelServiceSpec extends FunSpec with Matchers with MockitoSugar {


  val eventService = mock[EventService]
  val userManagementConnector = mock[UserManagementConnector]
  val readModelService = new ReadModelService(eventService, userManagementConnector)

  describe("refreshEventsCache") {

    it("should update the eventsCache correctly (with the events returned from the Repository))") {
      when(eventService.getAllEvents).thenReturn(Future.successful(List(Event(EventType.ServiceOwnerUpdated, 1, Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject]))))

      Await.result(readModelService.refreshEventsCache, 5 seconds)

      readModelService.eventsCache.size shouldBe 1
      readModelService.eventsCache.head shouldBe ("Catalogue", "Joe Black")
    }

  }

  describe("refreshUmpCache") {
    
    it("should update the umpUsersCache correctly (with the users returned from the UMP))") {

      val teamMember = TeamMember(Some("Jack Low"), None, None, None, None, None)
      when(userManagementConnector.getAllUsersFromUMP()).thenReturn(Future.successful(Right(Seq(teamMember))))

      Await.result(readModelService.refreshUmpCache, 5 seconds)

      readModelService.umpUsersCache.size shouldBe 1
      readModelService.umpUsersCache.head shouldBe(teamMember)
    }
  }
}
