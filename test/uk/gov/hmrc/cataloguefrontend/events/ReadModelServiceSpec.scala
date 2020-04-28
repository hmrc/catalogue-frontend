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

package uk.gov.hmrc.cataloguefrontend.events

import org.mockito.MockitoSugar
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.TeamMember

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class ReadModelServiceSpec extends AnyFunSpec with Matchers with MockitoSugar {
  import ExecutionContext.Implicits.global

  val eventService            = mock[EventService]
  val userManagementConnector = mock[UserManagementConnector]
  val readModelService        = new ReadModelService(eventService, userManagementConnector)

  describe("refreshEventsCache") {

    it("should update the eventsCache correctly (with the events returned from the Repository))") {
      when(eventService.getAllEvents)
        .thenReturn(
          Future.successful(
            List(
              Event(
                EventType.ServiceOwnerUpdated,
                1,
                Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject]
              )
            )
          )
        )

      Await.result(readModelService.refreshEventsCache, 5.seconds)

      readModelService.eventsCache.size shouldBe 1
      readModelService.eventsCache.head shouldBe "Catalogue" -> "Joe Black"
    }
  }

  describe("refreshUmpCache") {

    it("should update the umpUsersCache correctly (with the users returned from the UMP))") {

      val teamMember = TeamMember(Some("Jack Low"), None, None, None, None, None)
      when(userManagementConnector.getAllUsersFromUMP)
        .thenReturn(Future.successful(Right(Seq(teamMember))))

      Await.result(readModelService.refreshUmpCache, 5.seconds)

      readModelService.umpUsersCache.size shouldBe 1
      readModelService.umpUsersCache.head shouldBe teamMember
    }
  }
}
