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

import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.IndexModel
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, LoneElement, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.FutureHelpers
import uk.gov.hmrc.mongo.test.DefaultMongoCollectionSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class EventRepositorySpec
    extends UnitSpec
    with LoneElement
    with DefaultMongoCollectionSupport
    with ScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with GuiceOneAppPerSuite
    with MockitoSugar {

  val futureHelpers: FutureHelpers = app.injector.instanceOf[FutureHelpers]

  private lazy val eventRepository = new EventRepository(mongoComponent, futureHelpers)
  override protected lazy val collectionName: String = eventRepository.collectionName
  override protected lazy val indexes: Seq[IndexModel] = eventRepository.indexes

  private val timestamp = 1494625868

  "getAllEvents" should {
    "return all the events" in {

      insertEvent(timestamp)
      insertEvent(timestamp + 1)

      val events: Seq[Event] = await(eventRepository.getAllEvents)

      events.size shouldBe 2
      events      should contain theSameElementsAs
        Seq(
          Event(
            EventType.ServiceOwnerUpdated,
            timestamp = timestamp,
            Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "joe.black")).as[JsObject]),
          Event(
            EventType.ServiceOwnerUpdated,
            timestamp = timestamp + 1,
            Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "joe.black")).as[JsObject])
        )
    }
  }

  private def insertEvent(theTimestamp: Int) =
    await(
      insert(
        Document(
            "eventType" -> "ServiceOwnerUpdated"
          , "data"      -> Document(
                             "service"  -> "Catalogue"
                           , "username" -> "joe.black"
                           )
          , "timestamp" -> theTimestamp
          , "metadata"  -> Document()
          )))

  "getEventsByType" should {
    "return all the right events" in {
      val serviceOwnerUpdateEvent = Event(
        EventType.ServiceOwnerUpdated,
        timestamp = timestamp,
        Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject])
      val otherEvent = Event(
        EventType.Other,
        timestamp = timestamp,
        Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject])

      await(eventRepository.add(serviceOwnerUpdateEvent))

      val events: Seq[Event] = await(eventRepository.getEventsByType(EventType.ServiceOwnerUpdated))

      events.size shouldBe 1
      events.head shouldBe Event(
        EventType.ServiceOwnerUpdated,
        timestamp = timestamp,
        Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject])
    }
  }

  "add" should {
    "be able to insert a new record and update it as well" in {
      val event = Event(
        EventType.ServiceOwnerUpdated,
        timestamp = timestamp,
        Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject])
      await(eventRepository.add(event))
      val all = await(eventRepository.getAllEvents)

      all.size shouldBe 1
      val savedEvent: Event = all.loneElement

      savedEvent shouldBe event
    }
  }
}
