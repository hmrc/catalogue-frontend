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

import java.time.Instant
import java.time.temporal.ChronoUnit

import org.scalatest.{FlatSpec, Matchers}
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class ShutterServiceSpec extends FlatSpec with MockitoSugar with Matchers {

  val mockEvents = Seq(
      ShutterStateChangeEvent(
          username    = "test.user"
        , timestamp   = Instant.now().minus(2, ChronoUnit.DAYS)
        , serviceName = "abc-frontend"
        , environment = Environment.Production
        , status      = ShutterStatus.Shuttered
        , cause       = ShutterCause.UserCreated
        )
    , ShutterStateChangeEvent(
          username    = "fake.user"
        , timestamp   = Instant.now()
        , serviceName = "zxy-frontend"
        , environment = Environment.Production
        , status      = ShutterStatus.Unshuttered
        , cause       = ShutterCause.UserCreated
        )
    , ShutterStateChangeEvent(
          username    = "test.user"
        , timestamp   = Instant.now().minus(1, ChronoUnit.DAYS)
        , serviceName = "ijk-frontend"
        , environment = Environment.Production
        , status      = ShutterStatus.Shuttered
        , cause       = ShutterCause.UserCreated
        )
    )

  "findCurrentState" should "return a list of shutter events ordered by shutter status" in {

    val mockShutterConnector = mock[ShutterConnector]
    implicit val hc = new HeaderCarrier()

    when(mockShutterConnector.latestShutterEvents(Environment.Production)).thenReturn(Future(mockEvents))
    val ss = new ShutterService(mockShutterConnector)

    val Seq(a,b,c) = Await.result(ss.findCurrentState(Environment.Production), Duration(10, "seconds"))

    a.status shouldBe ShutterStatus.Shuttered
    b.status shouldBe ShutterStatus.Shuttered
    c.status shouldBe ShutterStatus.Unshuttered
  }
}
