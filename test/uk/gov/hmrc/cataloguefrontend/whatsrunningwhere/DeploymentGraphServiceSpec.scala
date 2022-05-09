/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.cataloguefrontend.model.Environment._

import java.time._
import java.time.temporal.ChronoUnit.DAYS

class DeploymentGraphServiceSpec extends AnyWordSpecLike with Matchers {

  "DeploymentGraphService.clipTimeline" must {
    val end   = Instant.now()
    val start = end.minus(30, DAYS)


    "handle an empty list of events" in {
      DeploymentGraphService.clipTimeline(Seq.empty, start, end)  mustBe Seq.empty
    }

    "ignore events that started and ended before the date range" in {
      val events = Seq(
        DeploymentTimelineEvent(QA, "0.0.1", "", start.minus(7, DAYS), start.minus(6, DAYS)),
        DeploymentTimelineEvent(QA, "0.0.2", "", start.minus(6, DAYS), end),
      )

      val res = DeploymentGraphService.clipTimeline(events, start, end)
      res.length mustBe 1
      res.head.version mustBe "0.0.2"
    }

    "ignore events that started and ended after the date range" in {
      val events = Seq(
        DeploymentTimelineEvent(QA, "0.0.1", "", end.plus(5, DAYS), end.plus(6, DAYS)),
        DeploymentTimelineEvent(QA, "0.0.2", "", start.plus(1, DAYS), end.plus(5, DAYS)),
      )

      val res = DeploymentGraphService.clipTimeline(events, start, end)
      res.length mustBe 0

    }

    "clip the start date of an event that starts before the range but ends inside it" in {
      val events = Seq(
        DeploymentTimelineEvent(QA, "0.0.1", "", start.minus(1, DAYS), start.plus(25, DAYS)),
        DeploymentTimelineEvent(QA, "0.0.2", "", start.plus(25, DAYS), start.plus(28, DAYS))
      )

      val res = DeploymentGraphService.clipTimeline(events, start, end)
      res.length mustBe 2
      res.head.start mustBe start
      res.head.end mustBe start.plus(25, DAYS)
    }


    "clip the end date of the event that starts before the end, and ends after it" in {

      val events = Seq(
        DeploymentTimelineEvent(QA, "0.0.1", "", start.plus(1, DAYS), end.minus(5, DAYS)),
        DeploymentTimelineEvent(QA, "0.0.2", "", end.minus(5, DAYS), end.plus(5, DAYS))
      )

      val res = DeploymentGraphService.clipTimeline(events, start, end)
      res.length mustBe 2
      res.last.start mustBe start.plus(25, DAYS)
      res.last.end mustBe end
    }
  }
}
