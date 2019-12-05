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

package uk.gov.hmrc.cataloguefrontend.session

import cats.data.OptionT
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, ReturnDocument, Updates}
import play.api.libs.json.{Format, Json, JsValue, OFormat, Reads, Writes}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}
import uk.gov.hmrc.mongo.cache.collection.{CacheItem, PlayMongoCacheCollection}
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import play.api.mvc.{Request, Session}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SessionStore @Inject()(
    mongoComponent  : MongoComponent
  , servicesConfig  : ServicesConfig
  , timestampSupport: TimestampSupport
  )(implicit ec: ExecutionContext) {

  private val cacheRepository = new PlayMongoCacheCollection(
        mongoComponent   = mongoComponent
      , collectionName   = "sessions"
      , domainFormat     = implicitly[Format[JsValue]]
      , ttl              = servicesConfig.getDuration("mongodb.session.expireAfter")
      , timestampSupport = timestampSupport
      )

  def get[T : Reads](
        session   : Session
      , sessionKey: String
      , dataKey   : String
      ): Future[Option[T]] =
    (for {
       sessionId <- OptionT.fromOption[Future](session.get(sessionKey))
       cache     <- OptionT(cacheRepository.find(sessionId))
       t         <- OptionT.fromOption[Future]((cache.data \ dataKey).asOpt[T])
     } yield t
    ).value

  def put[T : Writes](
        session   : Session
      , sessionKey: String
      , dataKey   : String
      , data      : T
      ): Future[String] = {
    val sessionId = session.get(sessionKey).getOrElse(java.util.UUID.randomUUID.toString)
    val timestamp = timestampSupport.timestamp()
    cacheRepository.collection
      .findOneAndUpdate(
          filter = Filters.equal(CacheItem.id, sessionId)
        , update = Updates.combine(
                       Updates.set(CacheItem.data + "." + dataKey, Codecs.toBson(data))
                     , Updates.set(CacheItem.modifiedAt          , timestamp)
                     , Updates.setOnInsert(CacheItem.id       , sessionId)
                     , Updates.setOnInsert(CacheItem.createdAt, timestamp)
                     )
        , options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      )
      .toFuture()
      .map(_ => sessionId)
    }

  def delete(session: Session, sessionKey: String, dataKey: String): Future[Unit] =
    session.get(sessionKey) match {
      case None            => Future(())
      case Some(sessionId) => cacheRepository.collection
                                .findOneAndUpdate(
                                    filter = Filters.equal(CacheItem.id, sessionId)
                                  , update = Updates.combine(
                                                 Updates.unset(CacheItem.data + "." + dataKey)
                                               , Updates.set(CacheItem.modifiedAt, timestampSupport.timestamp())
                                               )
                                )
                                .toFuture
                                .map(_ => ())
    }
}

object SessionController {
  case class Key[T](asString: String)
}

trait SessionController {
  import SessionController._

  val sessionStore: SessionStore
  val sessionIdKey: String

  def putSession[T : Writes](key: Key[T], data: T)(implicit request: Request[Any], ec: ExecutionContext): Future[(String, String)] =
    sessionStore.put[T](request.session, sessionIdKey, key.asString, data)
      .map(sessionIdKey -> _)

  def getFromSession[T : Reads](key: Key[T])(implicit request: Request[Any]): Future[Option[T]] =
    sessionStore.get[T](request.session, sessionIdKey, key.asString)

  def deleteFromSession[T](key: Key[T])(implicit request: Request[Any]): Future[Unit] =
    sessionStore.delete(request.session, sessionIdKey, key.asString)
}