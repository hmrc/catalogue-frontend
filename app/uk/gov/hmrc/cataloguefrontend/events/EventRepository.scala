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

import play.api.libs.json.Json
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.cataloguefrontend.FutureHelpers.withTimerAndCounter
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


trait EventRepository {
  def add(event: Event): Future[Boolean]
  def getEventsByType(eventType: EventType.Value): Future[Seq[Event]]
  def getAllEvents: Future[List[Event]]
  def clearAllData: Future[Boolean]
}


class MongoEventRepository(mongo: () => DB)
  extends ReactiveRepository[Event, BSONObjectID](
    collectionName = "events",
    mongo = mongo,
    domainFormat = Event.format) with EventRepository {


  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(
      Seq(
        collection.indexesManager.ensure(Index(Seq("timestamp" -> IndexType.Descending), name = Some("eventTimestampIdx")))
      )
    )

  override def add(event: Event): Future[Boolean] = {
    withTimerAndCounter("mongo.write") {
      insert(event) map {
        case lastError if lastError.inError => throw lastError
        case _ => true
      }
    }
  }

  override def getEventsByType(eventType: EventType.Value): Future[Seq[Event]] = {

    withTimerAndCounter("mongo.read") {
      find("eventType" -> BSONDocument("$eq" -> eventType.toString)) map {
        case Nil => Nil
        case data => data.sortBy(_.timestamp)
      }
    }
  }


  override def getAllEvents: Future[List[Event]] = findAll()

  override def clearAllData: Future[Boolean] = super.removeAll().map(!_.hasErrors)

}
