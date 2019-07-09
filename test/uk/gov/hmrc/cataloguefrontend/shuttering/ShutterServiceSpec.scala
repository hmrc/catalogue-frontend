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
      ShutterEvent(
          name   = "abc-frontend"
        , env    = Environment.Production
        , user   = "test.user"
        , status = ShutterStatus.Shuttered
        , date   = LocalDateTime.now().minusDays(2)
        )
    , ShutterEvent(
          name   = "zxy-frontend"
        , env    = Environment.Production
        , user   = "fake.user"
        , status = ShutterStatus.Unshuttered
        , date   = LocalDateTime.now()
        )
    , ShutterEvent(
          name   = "ijk-frontend"
        , env    = Environment.Production
        , user   = "test.user"
        , status = ShutterStatus.Shuttered
        , date   = LocalDateTime.now().minusDays(1)
        )
    )

  "findCurrentState" should "return a list of shutter events ordered by shutter status" in {

    val mockShutterConnector = mock[ShutterConnector]
    implicit val hc = new HeaderCarrier()

    when(mockShutterConnector.latestShutterEvents()).thenReturn(Future(mockEvents))
    val ss = new ShutterService(mockShutterConnector)

    val Seq(a,b,c) = Await.result(ss.findCurrentState(Environment.Production), Duration(10, "seconds"))

    a.status shouldBe ShutterStatus.Shuttered
    b.status shouldBe ShutterStatus.Shuttered
    c.status shouldBe ShutterStatus.Unshuttered
  }
}
