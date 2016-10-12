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

class ReleaseFilteringSpec extends WordSpec with Matchers {

  implicit def localDateToDate(d: LocalDateTime): Date = Date.from(d.atZone(ZoneId.systemDefault()).toInstant)

  "ReleasesFiltering" should {

    "get releases filtered by only service name" in {

      val now: LocalDateTime = LocalDateTime.now()

      val releases = Seq(
        TeamRelease("serv1", Seq("teamA"), productionDate = now, version = "1.0"),
        TeamRelease("serv2", Seq("teamB"), productionDate = now, version = "2.0"),
        TeamRelease("serv1", Seq("teamC"), productionDate = now, version = "3.0"),
        TeamRelease("serv3", Seq("teamD"), productionDate = now, version = "4.0"))

      releases.filter(ReleasesFilter(serviceName = Some("serv1"))) shouldBe Seq(
        TeamRelease("serv1", Seq("teamA"), productionDate = now, version = "1.0"),
        TeamRelease("serv1", Seq("teamC"), productionDate = now, version = "3.0"))

    }

    "get all releases (no filter)" in {

      val now: LocalDateTime = LocalDateTime.now()

      val releases = Seq(
        TeamRelease("serv1", Seq("teamA"), productionDate = now, version = "1.0"),
        TeamRelease("serv2", Seq("teamB"), productionDate = now, version = "2.0"),
        TeamRelease("serv1", Seq("teamC"), productionDate = now, version = "3.0"),
        TeamRelease("serv3", Seq("teamD"), productionDate = now, version = "4.0"))

      releases.filter(ReleasesFilter()) shouldBe releases

    }

    "limit releases to last months' if no filter was provided" in {

      val now: LocalDateTime = LocalDateTime.now()
      val lastMonth: LocalDateTime = now.minusMonths(1).minusDays(1)

      val releases = Seq.tabulate(10)(i => TeamRelease(s"serv$i", Seq("teamA"), productionDate = now, version = s"$i.0")) ++
        Seq.tabulate(5)(i => TeamRelease(s"lastMonthServ$i", Seq("teamB"), productionDate = lastMonth, version = s"$i.0"))

      releases.filter(ReleasesFilter()).size should ===(10)

    }

    "not limit releases if filter was provided" in {

      val now: LocalDateTime = LocalDateTime.now()
      val lastMonth: LocalDateTime = now.minusMonths(1).minusDays(1)


      val releases = Seq.tabulate(10)(i => TeamRelease(s"serv$i", Seq("teamA"), productionDate = now, version = s"$i.0")) ++
        Seq.tabulate(5)(i => TeamRelease(s"lastMonthServ$i", Seq("teamA"), productionDate = lastMonth, version = s"$i.0"))

      releases.filter(ReleasesFilter(to = Some(now))).size should ===(15)

    }

    "get releases filtered only by from date" in {

      val now: LocalDateTime = LocalDateTime.now()

      val releases = Seq(
        TeamRelease("serv3", Seq("teamA"), productionDate = now.minusDays(3), version = "4.0"),
        TeamRelease("serv1", Seq("teamB"), productionDate = now.minusDays(4), version = "3.0"),
        TeamRelease("serv2", Seq("teamC"), productionDate = now.minusDays(10), version = "2.0"),
        TeamRelease("serv1", Seq("teamD"), productionDate = now.minusDays(20), version = "1.0")
      )

      releases.filter(ReleasesFilter(from = Some(now.minusDays(4)))) shouldBe Seq(
        TeamRelease("serv3", Seq("teamA"), productionDate = now.minusDays(3), version = "4.0"),
        TeamRelease("serv1", Seq("teamB"), productionDate = now.minusDays(4), version = "3.0"))

    }

    "get releases filtered only by to date" in {

      val now: LocalDateTime = LocalDateTime.now()

      val releases = Seq(
        TeamRelease("serv3", Seq("teamA"), productionDate = now.minusDays(3), version = "4.0"),
        TeamRelease("serv1", Seq("teamB"), productionDate = now.minusDays(4), version = "3.0"),
        TeamRelease("serv2", Seq("teamC"), productionDate = now.minusDays(10), version = "2.0"),
        TeamRelease("serv1", Seq("teamD"), productionDate = now.minusDays(20), version = "1.0"))

      releases.filter(ReleasesFilter(to = Some(now.minusDays(10)))) shouldBe Seq(
        TeamRelease("serv2", Seq("teamC"), productionDate = now.minusDays(10), version = "2.0"),
        TeamRelease("serv1", Seq("teamD"), productionDate = now.minusDays(20), version = "1.0"))

    }

    "get releases filtered between from and to date" in {

      val now: LocalDateTime = LocalDateTime.now()

      val releases = Seq(
        TeamRelease("serv3", Seq("teamA"), productionDate = now.minusDays(3), version = "4.0"),
        TeamRelease("serv1", Seq("teamB"), productionDate = now.minusDays(4), version = "3.0"),
        TeamRelease("serv2", Seq("teamC"), productionDate = now.minusDays(10), version = "2.0"),
        TeamRelease("serv1", Seq("teamD"), productionDate = now.minusDays(20), version = "1.0"))

      releases.filter(ReleasesFilter(from = Some(now.minusDays(10)), to = Some(now.minusDays(4)))) shouldBe Seq(
        TeamRelease("serv1", Seq("teamB"), productionDate = now.minusDays(4), version = "3.0"),
        TeamRelease("serv2", Seq("teamC"), productionDate = now.minusDays(10), version = "2.0"))

    }

    "get releases filtered by name and between from and to date" in {

      val now: LocalDateTime = LocalDateTime.now()

      val releases = Seq(
        TeamRelease("serv3", Seq("teamA"), productionDate = now.minusDays(3), version = "4.0"),
        TeamRelease("serv1", Seq("teamB"), productionDate = now.minusDays(4), version = "3.0"),
        TeamRelease("serv2", Seq("teamC"), productionDate = now.minusDays(10), version = "2.0"),
        TeamRelease("serv1", Seq("teamD"), productionDate = now.minusDays(20), version = "1.0")
      )

      releases.filter(ReleasesFilter(serviceName = Some("serv2"), from = Some(now.minusDays(10)), to = Some(now.minusDays(4)))) shouldBe Seq(
        TeamRelease("serv2", Seq("teamC"), productionDate = now.minusDays(10), version = "2.0"))

    }

    "get releases filtered by only team name" in {

      val now: LocalDateTime = LocalDateTime.now()

      val releases = Seq(
        TeamRelease("serv1", Seq("teamA"), productionDate = now, version = "1.0"),
        TeamRelease("serv2", Seq("teamA"), productionDate = now, version = "2.0"),
        TeamRelease("serv1", Seq("teamB"), productionDate = now, version = "3.0"),
        TeamRelease("serv3", Seq("teamB"), productionDate = now, version = "4.0"))

      releases.filter(ReleasesFilter(team = Some("teamA"))) shouldBe Seq(
        TeamRelease("serv1", Seq("teamA"), productionDate = now, version = "1.0"),
        TeamRelease("serv2", Seq("teamA"), productionDate = now, version = "2.0"))

    }

  }

}
