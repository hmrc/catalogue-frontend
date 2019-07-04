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

package uk.gov.hmrc.cataloguefrontend.shuttering

import java.time.LocalDateTime

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ShutterService @Inject()(shutterConnector: ShutterConnector)(implicit val ec: ExecutionContext) {

  def getShutterStates: Future[Seq[ShutterState]] =
    Future(Seq(
        ShutterState(
            name         = "abc-frontend"
          , production   = true
          , staging      = false
          , qa           = false
          , externalTest = false
          , development  = false
          )
      , ShutterState(
            name         = "zxy-frontend"
          , production   = false
          , staging      = false
          , qa           = false
          , externalTest = false
          , development  = false
          )
      ))

  def shutterService(serviceName: String, env: String): Future[Unit] =
    Future(())

  def findCurrentState()(implicit hc: HeaderCarrier) : Future[Seq[ShutterEvent]] =
    Future(Seq(
      ShutterEvent(
        name = "abc-frontend"
        ,env  = "production"
        ,user = "test.user"
        ,isShuttered = true
        ,date = LocalDateTime.now().minusDays(2)
      )
      ,ShutterEvent(
        name = "zxy-frontend"
        ,env  = "production"
        ,user = "fake.user"
        ,isShuttered = false
        ,date = LocalDateTime.now()
      )
    ))
    // for {
    //   events <- shutterConnector.latestShutterEvents()
    //   sorted = events.sortBy(_.isShuttered)(Ordering[Boolean].reverse)
    // } yield sorted


}
