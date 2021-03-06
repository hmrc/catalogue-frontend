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

package uk.gov.hmrc.cataloguefrontend.events

import org.mockito.ArgumentCaptor
import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsObject, JsString}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class EventServiceSpec extends AnyFunSpec with Matchers with MockitoSugar with ScalaFutures {

  describe("EventService") {
    val testStartTime   = System.currentTimeMillis()
    val eventRepository = mock[EventRepository]
    val eventService    = new EventService(eventRepository)

    it("should save service owner update data by delegating to the event repository") {
      val serviceOwnerUsername = "joe.black"
      val serviceName          = "service-abc"
      val eventData            = ServiceOwnerUpdatedEventData(serviceName, serviceOwnerUsername)
      eventService.saveServiceOwnerUpdatedEvent(eventData)

      val argumentCaptor = ArgumentCaptor.forClass(classOf[Event])
      verify(eventRepository).add(argumentCaptor.capture)

      argumentCaptor.getValue.eventType shouldBe EventType.ServiceOwnerUpdated
      argumentCaptor.getValue.data shouldBe JsObject(
        Seq("service" -> JsString(serviceName), "username" -> JsString(serviceOwnerUsername)))
      argumentCaptor.getValue.metadata  shouldBe JsObject(Seq.empty)
      argumentCaptor.getValue.timestamp should be > testStartTime
      argumentCaptor.getValue.timestamp should be < System.currentTimeMillis()

    }

    it("should get all events by delegating to the event repository") {
      val mockedEvents = (1 to 5).map(_ => mock[Event]).toList
      when(eventRepository.getAllEvents)
        .thenReturn(Future.successful(mockedEvents))

      val events = eventService.getAllEvents
      Await.ready(events, 5 seconds)

      events.futureValue shouldBe mockedEvents
    }
  }
}
