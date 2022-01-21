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

package uk.gov.hmrc.cataloguefrontend.service

import org.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import java.time.temporal.ChronoUnit.HOURS
import java.time.{Instant, LocalDateTime, ZoneId}
import scala.concurrent.{ExecutionContext, Future}

class LeakDetectionServiceSpec extends UnitSpec with MockitoSugar {
  import ExecutionContext.Implicits.global

  private implicit val hc: HeaderCarrier = mock[HeaderCarrier]

  "Service" should {
    "determine if at least one of team's repos has leaks" in new Setup {
      val team = Team(
        name           = TeamName("team0"),
        createdDate    = None,
        lastActiveDate = None,
        repos          = Some(Map(RepoType.Library -> List("repo1", "repo2")))
      )

      val repoWithLeak = RepositoryWithLeaks("repo1")

      service.teamHasLeaks(team, Seq(repoWithLeak)) shouldBe true
    }

    "determine if a team has no leaks" in new Setup {
      val team = Team(
        name            = TeamName("team0"),
        createdDate     = None,
        lastActiveDate  = None,
        repos           = Some(Map(RepoType.Library -> List("repo1", "repo2")))
      )

      val repoWithLeak = RepositoryWithLeaks("repo3")

      service.teamHasLeaks(team, Seq(repoWithLeak)) shouldBe false
    }

    "filter repositories in the exclusion list" in new Setup {
      val team = Team(
        name            = TeamName("team0"),
        createdDate     = None,
        lastActiveDate  = None,
        repos           = Some(Map(RepoType.Library -> List("a-repo-to-ignore")))
      )

      val repoToIgnore = RepositoryWithLeaks("a-repo-to-ignore")

      service.teamHasLeaks(team, Seq(repoToIgnore)) shouldBe false
    }

    "return rule summaries with counts" in new Setup {
      val timestamp = Instant.now.minus(2, HOURS)
      def aRule = LeakDetectionRule("", "", "", "",List(), List(), "")
      def aRepositorySummary = LeakDetectionRepositorySummary("", timestamp, timestamp, 1)

      when(connector.leakDetectionRuleSummaries).thenReturn(
        Future.successful(Seq(
          LeakDetectionRuleSummary(aRule.copy(id = "rule-1"), Seq(aRepositorySummary.copy(repository = "repo1", unresolvedCount = 3))),
          LeakDetectionRuleSummary(aRule.copy(id = "rule-2"), Seq(
            aRepositorySummary.copy(repository = "repo1", firstScannedAt = timestamp.minus(3, HOURS), unresolvedCount = 4),
            aRepositorySummary.copy(repository = "repo2", lastScannedAt = timestamp.plus(1, HOURS)))),
          LeakDetectionRuleSummary(aRule.copy(id = "rule-3"), Seq()),
        )))

      val results = service.ruleSummaries().futureValue

     results shouldBe Seq(
       LeakDetectionRulesWithCounts(aRule.copy(id = "rule-1"),
         Some(LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())),
         Some(LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())),
         1, 3),
       LeakDetectionRulesWithCounts(aRule.copy(id = "rule-2"),
         Some(LocalDateTime.ofInstant(timestamp.minus(3, HOURS), ZoneId.systemDefault())),
         Some(LocalDateTime.ofInstant(timestamp.plus(1, HOURS), ZoneId.systemDefault())),
         2, 5),
       LeakDetectionRulesWithCounts(aRule.copy(id = "rule-3"), None, None, 0, 0)
     )
    }
  }

  private trait Setup {
    val connector = mock[LeakDetectionConnector]

    private val configuration =
      Configuration(
        "lds.publicUrl"          -> "",
        "lds.integrationEnabled" -> "true",
        "lds.noWarningsOn.0"     -> "a-repo-to-ignore"
      )

    val service = new LeakDetectionService(connector, configuration)
  }
}
