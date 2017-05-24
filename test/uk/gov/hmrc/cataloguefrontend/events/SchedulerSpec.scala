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

import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import org.scalatestplus.play.OneAppPerSuite

import scala.concurrent.duration._

class SchedulerSpec extends FunSpec with Matchers with MockitoSugar with OneAppPerSuite {

  describe("event read model update") {
    it("should be scheduled for specified intervals") {
      val modelService = mock[ReadModelService]
      val scheduler = new Scheduler with DefaultSchedulerDependencies {
        override def readModelService: ReadModelService = {
          modelService
        }
      }

      scheduler.startUpdatingEventsReadModel(100 milliseconds)

      verify(modelService, Mockito.after(550).atLeast(4)).refreshEventsCache
      verify(modelService, times(0)).refreshUmpCache
    }
  }

  describe("ump cache read model update") {
    it("should be scheduled for specified intervals") {
      val modelService = mock[ReadModelService]
      val scheduler = new Scheduler with DefaultSchedulerDependencies {
        override def readModelService: ReadModelService = {
          modelService
        }
      }

      scheduler.startUpdatingUmpCacheReadModel(100 milliseconds)

      verify(modelService, Mockito.after(550).atLeast(4)).refreshUmpCache
      verify(modelService, times(0)).refreshEventsCache
    }
  }

}
