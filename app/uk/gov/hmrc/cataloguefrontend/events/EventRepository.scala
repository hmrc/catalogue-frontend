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

import javax.inject.{Inject, Singleton}
import com.mongodb.BasicDBObject
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes._
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import uk.gov.hmrc.cataloguefrontend.FutureHelpers
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EventRepository @Inject()(
    mongoComponent: MongoComponent
  , futureHelpers : FutureHelpers
  )(implicit ec: ExecutionContext
  ) extends PlayMongoRepository[Event](
      mongoComponent = mongoComponent
    , collectionName = "events"
    , domainFormat   = Event.format
    , indexes        = Seq(
                         IndexModel(descending("timestamp"), IndexOptions().name("eventTimestampIdx"))
                       )
    ) {

  def add(event: Event): Future[Boolean] =
    futureHelpers.withTimerAndCounter("mongo.write") {
      collection
        .insertOne(event)
        .toFuture
        .map(_ => true)
    }

  def getEventsByType(eventType: EventType.Value): Future[Seq[Event]] =
    futureHelpers.withTimerAndCounter("mongo.read") {
      collection
        .find(
            filter = and(equal("eventType", eventType.toString))
         )
         .sort(ascending("timestamp"))
        .toFuture
    }

  def getAllEvents: Future[List[Event]] =
    collection
      .find()
      .sort(ascending("timestamp"))
      .toFuture
      .map(_.toList)

  def clearAllData: Future[Boolean] =
    collection
      .deleteMany(new BasicDBObject())
      .toFuture
      .map(_.wasAcknowledged())
}
