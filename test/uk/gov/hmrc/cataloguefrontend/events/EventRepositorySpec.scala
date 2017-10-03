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



import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, LoneElement, OptionValues}
import org.scalatestplus.play.OneAppPerTest
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class EventRepositorySpec
  extends UnitSpec
    with LoneElement
    with MongoSpecSupport
    with ScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with OneAppPerTest
    with MockitoSugar {


  val reactiveMongoComponent = new ReactiveMongoComponent() {
    override def mongoConnector = {
      val connector = mock[MongoConnector]
      when(connector.db).thenReturn(mongo)
      connector
    }
  }
  val mongoEventRepository = new EventRepository(reactiveMongoComponent)

  override def beforeEach() {
    await(mongoEventRepository.drop)
  }


  private val timestamp = 1494625868

  "getAllEvents" should {
    "return all the events" in {

      insertEvent(timestamp)
      insertEvent(timestamp + 1)

      val events: Seq[Event] = await(mongoEventRepository.getAllEvents)

      events.size shouldBe 2
      events should contain theSameElementsAs
        Seq(
          Event(EventType.ServiceOwnerUpdated, timestamp = timestamp, Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "joe.black")).as[JsObject]),
          Event(EventType.ServiceOwnerUpdated, timestamp = timestamp + 1, Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "joe.black")).as[JsObject])
        )
    }
  }

  private def insertEvent(theTimestamp: Int) = {
    await(mongoEventRepository.collection.insert(Json.obj(
      "eventType" -> "ServiceOwnerUpdated",
      "data" -> Json.obj(
        "service" -> "Catalogue",
        "username" -> "joe.black"
      ),
      "timestamp" -> theTimestamp,
      "metadata" -> Json.obj()
    )))
  }

  "getEventsByType" should {
    "return all the right events" in {
      val serviceOwnerUpdateEvent = Event(EventType.ServiceOwnerUpdated, timestamp = timestamp, Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject])
      val otherEvent = Event(EventType.Other, timestamp = timestamp, Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject])

      await(mongoEventRepository.add(serviceOwnerUpdateEvent))

      val events: Seq[Event] = await(mongoEventRepository.getEventsByType(EventType.ServiceOwnerUpdated))

      events.size shouldBe 1
      events.head shouldBe Event(EventType.ServiceOwnerUpdated, timestamp = timestamp, Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject])
    }
  }

  "add" should {
    "be able to insert a new record and update it as well" in {
      val event = Event(EventType.ServiceOwnerUpdated, timestamp = timestamp, Json.toJson(ServiceOwnerUpdatedEventData("Catalogue", "Joe Black")).as[JsObject])
      await(mongoEventRepository.add(event))
      val all = await(mongoEventRepository.getAllEvents)

      all.size shouldBe 1
      val savedEvent: Event = all.loneElement

      savedEvent shouldBe event
    }
  }

}
