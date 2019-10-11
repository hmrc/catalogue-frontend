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
import play.api.libs.json.{Json, OFormat, Reads, Writes}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.cache.model.Cache
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import play.api.mvc.{Request, Session}
import reactivemongo.play.json._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SessionStore @Inject()(
    mongo         : ReactiveMongoComponent
  , servicesConfig: ServicesConfig
  )(implicit ec: ExecutionContext) {

  private val cacheRepository = new CacheMongoRepository(
        collName           = "sessions"
      , expireAfterSeconds = servicesConfig.getDuration("mongodb.session.expireAfter").toSeconds
      )(mongo.mongoConnector.db, ec)

  def get[T : Reads](
        session   : Session
      , sessionKey: String
      , dataKey   : String
      ): Future[Option[T]] =
    (for {
       sessionId <- OptionT.fromOption[Future](session.get(sessionKey))
       cache     <- OptionT(cacheRepository.findById(sessionId))
       data      <- OptionT.fromOption[Future](cache.data)
       t         <- OptionT.fromOption[Future]((data \ dataKey).asOpt[T])
     } yield t
    ).value

  def put[T : Writes](
        session   : Session
      , sessionKey: String
      , dataKey   : String
      , data      : T
      ): Future[String] = {
    val sessionId = session.get(sessionKey).getOrElse(java.util.UUID.randomUUID.toString)
    cacheRepository.createOrUpdate(
        id      = sessionId
      , key     = dataKey
      , toCache = Json.toJson(data)
      )
      .map(_ => sessionId)
    }

  def delete(session: Session, sessionKey: String, dataKey: String): Future[Unit] =
    session.get(sessionKey) match {
      case None            => Future(())
      case Some(sessionId) => cacheRepository.collection.update(ordered = false).one(
                                  q = Json.obj(cacheRepository.Id -> sessionId)
                                , u = Json.obj("$unset" -> Json.obj(s"${Cache.DATA_ATTRIBUTE_NAME}.$dataKey" -> ""))
                                )
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