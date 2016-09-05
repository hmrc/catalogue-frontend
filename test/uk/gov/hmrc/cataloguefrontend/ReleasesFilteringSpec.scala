/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import java.time.{ZoneId, LocalDateTime}
import java.util.Date

import org.scalatest.{Matchers, WordSpec}

import ReleaseFiltering._

class ReleasesFilteringSpec extends WordSpec with Matchers {

  implicit def localDateToDate(d: LocalDateTime): Date = Date.from(d.atZone(ZoneId.systemDefault()).toInstant)

  "ReleasesFiltering" should {

    "get releases filtered by only service name" in {

      val now: LocalDateTime = LocalDateTime.now()

      val releases = Seq(
        Release("serv1", "1.0", now),
        Release("serv2", "2.0", now),
        Release("serv1", "3.0", now),
        Release("serv3", "4.0", now)
      )
      releases.filter(ReleasesFilter(Some("serv1"))) shouldBe Seq(Release("serv1", "1.0", now), Release("serv1", "3.0", now))

    }

    "get releases filtered only by from date" in {

      val now: LocalDateTime = LocalDateTime.now()

      val releases = Seq(
        Release("serv3", "4.0", now.minusDays(3)),
        Release("serv1", "3.0", now.minusDays(4)),
        Release("serv2", "2.0", now.minusDays(10)),
        Release("serv1", "1.0", now.minusDays(20))
      )
      releases.filter(ReleasesFilter(from = Some(now.minusDays(4)))) shouldBe Seq(
        Release("serv3", "4.0", now.minusDays(3)),
        Release("serv1", "3.0", now.minusDays(4))
      )

    }

    "get releases filtered only by to date" in {

      val now: LocalDateTime = LocalDateTime.now()

      val releases = Seq(
        Release("serv3", "4.0", now.minusDays(3)),
        Release("serv1", "3.0", now.minusDays(4)),
        Release("serv2", "2.0", now.minusDays(10)),
        Release("serv1", "1.0", now.minusDays(20))
      )

      releases.filter(ReleasesFilter(to = Some(now.minusDays(10)))) shouldBe Seq(Release("serv2", "2.0", now.minusDays(10)), Release("serv1", "1.0", now.minusDays(20)))

    }

    "get releases filtered between from and to date" in {

      val now: LocalDateTime = LocalDateTime.now()

      val releases = Seq(
        Release("serv3", "4.0", now.minusDays(3)),
        Release("serv1", "3.0", now.minusDays(4)),
        Release("serv2", "2.0", now.minusDays(10)),
        Release("serv1", "1.0", now.minusDays(20))
      )

      releases.filter(ReleasesFilter(from = Some(now.minusDays(10)), to = Some(now.minusDays(4)))) shouldBe Seq(Release("serv1", "3.0", now.minusDays(4)), Release("serv2", "2.0", now.minusDays(10)))

    }


        "get releases filtered by name and between from and to date" in {

          val now: LocalDateTime = LocalDateTime.now()

          val releases = Seq(
            Release("serv3", "4.0", now.minusDays(3)),
            Release("serv1", "3.0", now.minusDays(4)),
            Release("serv2", "2.0", now.minusDays(10)),
            Release("serv1", "1.0", now.minusDays(20))
          )

          releases.filter(ReleasesFilter(serviceName = Some("serv2") ,from = Some(now.minusDays(10)), to = Some(now.minusDays(4)))) shouldBe Seq(Release("serv2", "2.0", now.minusDays(10)))

        }

  }


}
