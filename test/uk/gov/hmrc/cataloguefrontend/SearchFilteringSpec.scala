/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.{LocalDateTime, ZoneId}
import java.util.Date

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cataloguefrontend.SearchFiltering._
import uk.gov.hmrc.cataloguefrontend.connector.Team
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.service.TeamRelease

class SearchFilteringSpec extends WordSpec with Matchers {

  implicit def localDateToDate(d: LocalDateTime): Date = Date.from(d.atZone(ZoneId.systemDefault()).toInstant)

  "DeploymentsFiltering" should {

    "get deployments filtered by only service name" in {

      val now: LocalDateTime = LocalDateTime.now()

      val deployments = Seq(
        TeamRelease("serv1", Seq(TeamName("teamA")), productionDate = now, version = "1.0"),
        TeamRelease("serv2", Seq(TeamName("teamB")), productionDate = now, version = "2.0"),
        TeamRelease("serv1", Seq(TeamName("teamC")), productionDate = now, version = "3.0"),
        TeamRelease("serv3", Seq(TeamName("teamD")), productionDate = now, version = "4.0")
      )

      deployments.filter(DeploymentsFilter(serviceName    = Some("serv1"))) shouldBe Seq(
        TeamRelease("serv1", Seq(TeamName("teamA")), productionDate = now, version = "1.0"),
        TeamRelease("serv1", Seq(TeamName("teamC")), productionDate = now, version = "3.0"))

    }

    "get deployments filtered by partial service name and case insensitive" in {

      val now: LocalDateTime = LocalDateTime.now()

      val deployments = Seq(
        TeamRelease("serv1"              , Seq(TeamName("teamA")), productionDate = now, version = "1.0"),
        TeamRelease("serv2"              , Seq(TeamName("teamB")), productionDate = now, version = "2.0"),
        TeamRelease("filter-out-this-one", Seq(TeamName("teamD")), productionDate = now, version = "4.0")
      )

      deployments.filter(DeploymentsFilter(serviceName    = Some("SERV"))) should contain theSameElementsAs Seq(
        TeamRelease("serv1", Seq(TeamName("teamA")), productionDate = now, version = "1.0"),
        TeamRelease("serv2", Seq(TeamName("teamB")), productionDate = now, version = "2.0"))

    }

    "get all deployments (no filter)" in {

      val now: LocalDateTime = LocalDateTime.now()

      val deployments = Seq(
        TeamRelease("serv1", Seq(TeamName("teamA")), productionDate = now, version = "1.0"),
        TeamRelease("serv2", Seq(TeamName("teamB")), productionDate = now, version = "2.0"),
        TeamRelease("serv1", Seq(TeamName("teamC")), productionDate = now, version = "3.0"),
        TeamRelease("serv3", Seq(TeamName("teamD")), productionDate = now, version = "4.0")
      )

      deployments.filter(DeploymentsFilter()) shouldBe deployments

    }

    "limit deployments to last months' if no filter was provided" in {

      val now: LocalDateTime       = LocalDateTime.now()
      val lastMonth: LocalDateTime = now.minusMonths(1).minusDays(1)

      val deployments = Seq.tabulate(10)(i =>
        TeamRelease(s"serv$i", Seq(TeamName("teamA")), productionDate = now, version = s"$i.0")) ++
        Seq.tabulate(5)(i =>
          TeamRelease(s"lastMonthServ$i", Seq(TeamName("teamB")), productionDate = lastMonth, version = s"$i.0"))

      deployments.filter(DeploymentsFilter()).size should ===(10)

    }

    "not limit deployments if filter was provided" in {

      val now: LocalDateTime       = LocalDateTime.now()
      val lastMonth: LocalDateTime = now.minusMonths(1).minusDays(1)

      val deployments = Seq.tabulate(10)(i =>
        TeamRelease(s"serv$i", Seq(TeamName("teamA")), productionDate = now, version = s"$i.0")) ++
        Seq.tabulate(5)(i =>
          TeamRelease(s"lastMonthServ$i", Seq(TeamName("teamA")), productionDate = lastMonth, version = s"$i.0"))

      deployments.filter(DeploymentsFilter(to = Some(now))).size should ===(15)

    }

    "get deployments filtered only by from date" in {

      val now: LocalDateTime = LocalDateTime.now()

      val deployments = Seq(
        TeamRelease("serv3", Seq(TeamName("teamA")), productionDate = now.minusDays(3), version  = "4.0"),
        TeamRelease("serv1", Seq(TeamName("teamB")), productionDate = now.minusDays(4), version  = "3.0"),
        TeamRelease("serv2", Seq(TeamName("teamC")), productionDate = now.minusDays(10), version = "2.0"),
        TeamRelease("serv1", Seq(TeamName("teamD")), productionDate = now.minusDays(20), version = "1.0")
      )

      deployments.filter(DeploymentsFilter(from           = Some(now.minusDays(4)))) shouldBe Seq(
        TeamRelease("serv3", Seq(TeamName("teamA")), productionDate = now.minusDays(3), version = "4.0"),
        TeamRelease("serv1", Seq(TeamName("teamB")), productionDate = now.minusDays(4), version = "3.0")
      )

    }

    "get deployments filtered only by to date" in {

      val now: LocalDateTime = LocalDateTime.now()

      val deployments = Seq(
        TeamRelease("serv3", Seq(TeamName("teamA")), productionDate = now.minusDays(3), version  = "4.0"),
        TeamRelease("serv1", Seq(TeamName("teamB")), productionDate = now.minusDays(4), version  = "3.0"),
        TeamRelease("serv2", Seq(TeamName("teamC")), productionDate = now.minusDays(10), version = "2.0"),
        TeamRelease("serv1", Seq(TeamName("teamD")), productionDate = now.minusDays(20), version = "1.0")
      )

      deployments.filter(DeploymentsFilter(to             = Some(now.minusDays(10)))) shouldBe Seq(
        TeamRelease("serv2", Seq(TeamName("teamC")), productionDate = now.minusDays(10), version = "2.0"),
        TeamRelease("serv1", Seq(TeamName("teamD")), productionDate = now.minusDays(20), version = "1.0")
      )

    }

    "get deployments filtered between from and to date" in {

      val now: LocalDateTime = LocalDateTime.now()

      val deployments = Seq(
        TeamRelease("serv3", Seq(TeamName("teamA")), productionDate = now.minusDays(3), version  = "4.0"),
        TeamRelease("serv1", Seq(TeamName("teamB")), productionDate = now.minusDays(4), version  = "3.0"),
        TeamRelease("serv2", Seq(TeamName("teamC")), productionDate = now.minusDays(10), version = "2.0"),
        TeamRelease("serv1", Seq(TeamName("teamD")), productionDate = now.minusDays(20), version = "1.0")
      )

      deployments.filter(DeploymentsFilter(from           = Some(now.minusDays(10)), to = Some(now.minusDays(4)))) shouldBe Seq(
        TeamRelease("serv1", Seq(TeamName("teamB")), productionDate = now.minusDays(4), version   = "3.0"),
        TeamRelease("serv2", Seq(TeamName("teamC")), productionDate = now.minusDays(10), version  = "2.0")
      )

    }

    "get deployments filtered by name and between from and to date" in {

      val now: LocalDateTime = LocalDateTime.now()

      val deployments = Seq(
        TeamRelease("serv3", Seq(TeamName("teamA")), productionDate = now.minusDays(3), version  = "4.0"),
        TeamRelease("serv1", Seq(TeamName("teamB")), productionDate = now.minusDays(4), version  = "3.0"),
        TeamRelease("serv2", Seq(TeamName("teamC")), productionDate = now.minusDays(10), version = "2.0"),
        TeamRelease("serv1", Seq(TeamName("teamD")), productionDate = now.minusDays(20), version = "1.0")
      )

      deployments.filter(DeploymentsFilter(
        serviceName                                       = Some("serv2"),
        from                                              = Some(now.minusDays(10)),
        to                                                = Some(now.minusDays(4)))) shouldBe Seq(
        TeamRelease("serv2", Seq(TeamName("teamC")), productionDate = now.minusDays(10), version = "2.0"))

    }

    "get deployments filtered by only team name" in {

      val now: LocalDateTime = LocalDateTime.now()

      val deployments = Seq(
        TeamRelease("serv1", Seq(TeamName("teamA")), productionDate = now, version = "1.0"),
        TeamRelease("serv2", Seq(TeamName("teamA")), productionDate = now, version = "2.0"),
        TeamRelease("serv1", Seq(TeamName("teamB")), productionDate = now, version = "3.0"),
        TeamRelease("serv3", Seq(TeamName("teamB")), productionDate = now, version = "4.0")
      )

      deployments.filter(DeploymentsFilter(team           = Some("teamA"))) shouldBe Seq(
        TeamRelease("serv1", Seq(TeamName("teamA")), productionDate = now, version = "1.0"),
        TeamRelease("serv2", Seq(TeamName("teamA")), productionDate = now, version = "2.0"))

    }
    "get deployments filtered by partial team name and case insensitive" in {

      val now: LocalDateTime = LocalDateTime.now()

      val deployments = Seq(
        TeamRelease("serv1", Seq(TeamName("teamA"))              , productionDate = now, version = "1.0"),
        TeamRelease("serv2", Seq(TeamName("teamA"))              , productionDate = now, version = "2.0"),
        TeamRelease("serv1", Seq(TeamName("teamB"))              , productionDate = now, version = "3.0"),
        TeamRelease("serv3", Seq(TeamName("filter-out-this-one")), productionDate = now, version = "4.0")
      )

      deployments.filter(DeploymentsFilter(team = Some("TEAM"))).toList should contain theSameElementsAs Seq(
        TeamRelease("serv1", Seq(TeamName("teamA")), productionDate = now, version = "1.0"),
        TeamRelease("serv2", Seq(TeamName("teamA")), productionDate = now, version = "2.0"),
        TeamRelease("serv1", Seq(TeamName("teamB")), productionDate = now, version = "3.0")
      )

    }

  }

  "TeamResult.filter" should {
    val teams = Seq(
      Team(TeamName("CATO"), None, None, None, None),
      Team(TeamName("Auth"), None, None, None, None),
      Team(TeamName("API Platform"), None, None, None, None),
      Team(TeamName("API Services"), None, None, None, None),
      Team(TeamName("ALA"), None, None, None, None),
      Team(TeamName("ATED"), None, None, None, None)
    )

    "return the right team given an exact match on the team name" in {
      teams.filter(TeamFilter(Some("Auth"))) shouldBe Seq(Team(TeamName("Auth"), None, None, None, None))
    }

    "return the right sequence of teams given a partial match" in {
      teams.filter(TeamFilter(Some("API"))) shouldBe
        Seq(
          Team(TeamName("API Platform"), None, None, None, None),
          Team(TeamName("API Services"), None, None, None, None)
        )
    }

    "return the right sequence of teams given a case insensitive partial match" in {
      teams.filter(TeamFilter(Some("api"))) shouldBe
        Seq(
          Team(TeamName("API Platform"), None, None, None, None),
          Team(TeamName("API Services"), None, None, None, None)
        )
    }
  }
}
