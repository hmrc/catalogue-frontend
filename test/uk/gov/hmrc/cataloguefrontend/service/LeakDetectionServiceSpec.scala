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
        name = TeamName("team0"),
        createdDate = None,
        lastActiveDate = None,
        repos = Some(Map(RepoType.Library -> List("repo1", "repo2")))
      )

      val repoWithLeak = RepositoryWithLeaks("repo1")

      service.teamHasLeaks(team, Seq(repoWithLeak)) shouldBe true
    }

    "determine if a team has no leaks" in new Setup {
      val team = Team(
        name = TeamName("team0"),
        createdDate = None,
        lastActiveDate = None,
        repos = Some(Map(RepoType.Library -> List("repo1", "repo2")))
      )

      val repoWithLeak = RepositoryWithLeaks("repo3")

      service.teamHasLeaks(team, Seq(repoWithLeak)) shouldBe false
    }

    "filter repositories in the exclusion list" in new Setup {
      val team = Team(
        name = TeamName("team0"),
        createdDate = None,
        lastActiveDate = None,
        repos = Some(Map(RepoType.Library -> List("a-repo-to-ignore")))
      )

      val repoToIgnore = RepositoryWithLeaks("a-repo-to-ignore")

      service.teamHasLeaks(team, Seq(repoToIgnore)) shouldBe false
    }

    "return rule summaries with counts" in new Setup {
      when(connector.leakDetectionSummaries(None, None, None)).thenReturn(
        Future.successful(
          Seq(
            LeakDetectionSummary(aRule.copy(id = "rule-1"), Seq(aRepositorySummary.copy(repository = "repo1", unresolvedCount = 3))),
            LeakDetectionSummary(
              aRule.copy(id = "rule-2"),
              Seq(
                aRepositorySummary.copy(repository = "repo1", firstScannedAt = timestamp.minus(3, HOURS), unresolvedCount = 4),
                aRepositorySummary.copy(repository = "repo2", lastScannedAt = timestamp.plus(1, HOURS))
              )
            ),
            LeakDetectionSummary(aRule.copy(id = "rule-3"), Seq())
          )
        )
      )

      val results = service.ruleSummaries().futureValue

      results shouldBe Seq(
        LeakDetectionRulesWithCounts(
          aRule.copy(id = "rule-2"),
          Some(LocalDateTime.ofInstant(timestamp.minus(3, HOURS), ZoneId.systemDefault())),
          Some(LocalDateTime.ofInstant(timestamp.plus(1, HOURS), ZoneId.systemDefault())),
          2,
          5
        ),LeakDetectionRulesWithCounts(
          aRule.copy(id = "rule-1"),
          Some(LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())),
          Some(LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())),
          1,
          3
        ),
        LeakDetectionRulesWithCounts(aRule.copy(id = "rule-3"), None, None, 0, 0)
      )
    }

    "return repository summaries with counts" in new Setup {
      when(connector.leakDetectionSummaries(None, None, None)).thenReturn(
        Future.successful(
          Seq(
            LeakDetectionSummary(aRule.copy(id = "rule-1"), Seq(aRepositorySummary.copy(repository = "repo1", unresolvedCount = 3))),
            LeakDetectionSummary(
              aRule.copy(id = "rule-2"),
              Seq(
                aRepositorySummary.copy(repository = "repo1", firstScannedAt = timestamp.minus(3, HOURS), unresolvedCount = 4),
                aRepositorySummary.copy(repository = "repo2", lastScannedAt = timestamp.plus(1, HOURS))
              )
            ),
            LeakDetectionSummary(aRule.copy(id = "rule-3"), Seq())
          )
        )
      )

      val results = service.repoSummaries(None, None).futureValue

      results._1 shouldBe Seq("rule-1", "rule-2", "rule-3")

      results._2 shouldBe Seq(
        LeakDetectionReposWithCounts(
          "repo1",
          Some(LocalDateTime.ofInstant(timestamp.minus(3, HOURS), ZoneId.systemDefault())),
          Some(LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())),
          7
        ),
        LeakDetectionReposWithCounts(
          "repo2",
          Some(LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())),
          Some(LocalDateTime.ofInstant(timestamp.plus(1, HOURS), ZoneId.systemDefault())),
          1
        )
      )
    }

    "return branch summaries with counts" in new Setup {
      when(connector.leakDetectionSummaries(None, Some("repo1"), None)).thenReturn(
        Future.successful(
          Seq(
            LeakDetectionSummary(
              aRule.copy(id = "rule-1"),
              Seq(
                aRepositorySummary.copy(
                  repository = "repo1",
                  unresolvedCount = 3,
                  branchSummary = Seq(
                    aBranchSummary.copy(branch = "branch1", reportId = "report1", scannedAt = timestamp.minus(5, HOURS), unresolvedCount = 2),
                    aBranchSummary.copy(branch = "branch2", reportId = "report2", scannedAt = timestamp.minus(4, HOURS))
                  )
                )
              )
            ),
            LeakDetectionSummary(
              aRule.copy(id = "rule-2"),
              Seq(
                aRepositorySummary.copy(
                  repository = "repo1",
                  firstScannedAt = timestamp.minus(3, HOURS),
                  unresolvedCount = 8,
                  branchSummary = Seq(
                    aBranchSummary.copy(branch = "branch1", reportId = "report1", scannedAt = timestamp.minus(5, HOURS), unresolvedCount = 1),
                    aBranchSummary.copy(branch = "branch2", reportId = "report2", scannedAt = timestamp.minus(4, HOURS)),
                    aBranchSummary.copy(branch = "branch3", reportId = "report3", scannedAt = timestamp.minus(3, HOURS), unresolvedCount = 6)
                  )
                )
              )
            ),
            LeakDetectionSummary(aRule.copy(id = "rule-3"), Seq())
          )
        )
      )

      val results = service.branchSummaries("repo1").futureValue

      results shouldBe Seq(
        LeakDetectionBranchesWithCounts(
          "branch1",
          "report1",
          LocalDateTime.ofInstant(timestamp.minus(5, HOURS), ZoneId.systemDefault()),
          3
        ),
        LeakDetectionBranchesWithCounts(
          "branch2",
          "report2",
          LocalDateTime.ofInstant(timestamp.minus(4, HOURS), ZoneId.systemDefault()),
          2
        ),
        LeakDetectionBranchesWithCounts(
          "branch3",
          "report3",
          LocalDateTime.ofInstant(timestamp.minus(3, HOURS), ZoneId.systemDefault()),
          6
        )
      )
    }

    "get report details with associated leaks" in new Setup {
      when(connector.leakDetectionReport("repo1", "main")).thenReturn(Future.successful(
        LeakDetectionReport("repo1", "main", "reportId", timestamp, "author", "commitId"))
      )

      when(connector.leakDetectionLeaks("reportId")).thenReturn(Future.successful(Seq(
        LeakDetectionLeak("rule1", "description1", "file1", "fileContent", 1, "/link-to-github/file1", "secret=123", List(Match(1,6)), "high"),
        LeakDetectionLeak("rule1", "description1", "file1", "fileContent", 9, "/link-to-github/file1", "the secret sauce", List(Match(5,10)), "high"),
        LeakDetectionLeak("rule1", "description1", "file2", "fileContent", 3, "/link-to-github/file2", "my_secret: abc", List(Match(4,9)), "high"),
        LeakDetectionLeak("rule2", "description2", "file2", "fileName", 5, "/link-to-github/file2", "---BEGIN---", List(Match(4,8)), "medium")
      )))

      val results = service.report("repo1", "main").futureValue

      results shouldBe LeakDetectionReportWithLeaks("repo1", "main", "reportId", LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()), "author", "commitId", Seq(
        LeakDetectionLeaksByRule("rule1", "description1", "fileContent", "high", Seq(
          LeakDetectionLeakDetails("file1", 1, "/link-to-github/file1", "secret=123", List(Match(1,6))),
          LeakDetectionLeakDetails("file1", 9, "/link-to-github/file1", "the secret sauce", List(Match(5,10))),
          LeakDetectionLeakDetails("file2", 3, "/link-to-github/file2", "my_secret: abc", List(Match(4,9)))
        )),
          LeakDetectionLeaksByRule("rule2", "description2", "fileName", "medium", Seq(
          LeakDetectionLeakDetails("file2", 5, "/link-to-github/file2", "---BEGIN---", List(Match(4,8)))
        ))
      ))
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

    val timestamp = Instant.now.minus(2, HOURS)

    def aRule              = LeakDetectionRule("", "", "", "", List(), List(), "")
    def aRepositorySummary = LeakDetectionRepositorySummary("", timestamp, timestamp, 1, Seq())
    def aBranchSummary     = LeakDetectionBranchSummary("", "", timestamp, 1)
  }
}
