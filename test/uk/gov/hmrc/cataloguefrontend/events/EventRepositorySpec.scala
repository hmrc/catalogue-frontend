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
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.IndexModel
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, LoneElement, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.FutureHelpers
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global

class EventRepositorySpec
    extends UnitSpec
    with LoneElement
    with DefaultPlayMongoRepositorySupport[Event]
    with ScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with GuiceOneAppPerSuite
    with MockitoSugar {

  val futureHelpers: FutureHelpers = app.injector.instanceOf[FutureHelpers]

  override lazy val repository = new EventRepository(mongoComponent, futureHelpers)

  private val timestamp = 1494625868

  "getAllEvents" should {
    "return all the events" in {

      insertEvent(timestamp)
      insertEvent(timestamp + 1)

      val events: Seq[Event] = repository.getAllEvents.futureValue

      events.size shouldBe 2
      events      should contain theSameElementsAs
        Seq(
          Event(
            eventType = EventType.ServiceOwnerUpdated,
            timestamp = timestamp,
            data      = Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "joe.black")).as[JsObject]),
          Event(
            EventType.ServiceOwnerUpdated,
            timestamp = timestamp + 1,
            Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "joe.black")).as[JsObject])
        )
    }
  }

  private def insertEvent(theTimestamp: Int) =
    insert(
      Event(
        eventType = EventType.ServiceOwnerUpdated,
        timestamp = theTimestamp,
        data      = Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "joe.black")).as[JsObject]))
      .futureValue

  "getEventsByType" should {
    "return all the right events" in {
      val serviceOwnerUpdateEvent = Event(
        eventType = EventType.ServiceOwnerUpdated,
        timestamp = timestamp,
        data      = Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject])
      val otherEvent = Event(
        eventType = EventType.Other,
        timestamp = timestamp,
        data      = Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject])

      repository.add(serviceOwnerUpdateEvent).futureValue

      val events: Seq[Event] = repository.getEventsByType(EventType.ServiceOwnerUpdated).futureValue

      events.size shouldBe 1
      events.head shouldBe Event(
        eventType = EventType.ServiceOwnerUpdated,
        timestamp = timestamp,
        data      = Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject])
    }
  }

  "add" should {
    "be able to insert a new record and update it as well" in {
      val event = Event(
        eventType = EventType.ServiceOwnerUpdated,
        timestamp = timestamp,
        data      = Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject])
      repository.add(event).futureValue
      val all = repository.getAllEvents.futureValue

      all.size shouldBe 1
      val savedEvent: Event = all.loneElement

      savedEvent shouldBe event
    }
  }
}
