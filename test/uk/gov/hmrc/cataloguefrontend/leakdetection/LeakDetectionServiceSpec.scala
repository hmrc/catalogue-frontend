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

package uk.gov.hmrc.cataloguefrontend.leakdetection

import org.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.HOURS
import scala.concurrent.{ExecutionContext, Future}

class LeakDetectionServiceSpec extends UnitSpec with MockitoSugar {
  import ExecutionContext.Implicits.global

  private implicit val hc: HeaderCarrier = mock[HeaderCarrier]

  "Service" should {

    "return rule summaries with counts" in new Setup {
      when(connector.leakDetectionSummaries(None, None, None)).thenReturn(
        Future.successful(
          Seq(
            LeakDetectionSummary(aRule.copy(id = "rule-1"), Seq(aRepositorySummary.copy(repository = "repo1", unresolvedCount = 3))),
            LeakDetectionSummary(
              aRule.copy(id = "rule-2"),
              Seq(
                aRepositorySummary.copy(repository = "repo1", firstScannedAt = timestamp.minus(3, HOURS), unresolvedCount = 4),
                aRepositorySummary.copy(repository = "repo2", lastScannedAt = timestamp.plus(1, HOURS), unresolvedCount = 1)
              )
            ),
            LeakDetectionSummary(aRule.copy(id = "rule-3"), Seq())
          )
        )
      )

      val results = service.ruleSummaries().futureValue

      results shouldBe Seq(
        LeakDetectionRulesWithCounts(aRule.copy(id = "rule-2"), Some(timestamp.minus(3, HOURS)), Some(timestamp.plus(1, HOURS)), 2, 0, 5),
        LeakDetectionRulesWithCounts(aRule.copy(id = "rule-1"), Some(timestamp), Some(timestamp), 1, 0, 3),
        LeakDetectionRulesWithCounts(aRule.copy(id = "rule-3"), None, None, 0, 0, 0)
      )
    }

    "return repo summaries and" should {
      "include warnings, exemptions and violations" in new Setup {
        givenRepoSummariesWithAllCountCombinations(false)
        val results = service.repoSummaries(None, None, true, true, true, false).futureValue

        results._2.map(_.repository) should contain theSameElementsAs Seq(
          "warnings, exemptions and violations",
          "warnings and exemptions",
          "warnings and violations",
          "warnings",
          "exemptions and violations",
          "exemptions",
          "violations"
        )
      }

      "include warnings and exemptions" in new Setup {
        givenRepoSummariesWithAllCountCombinations(false)
        val results = service.repoSummaries(None, None, true, true, false, false).futureValue

        results._2.map(_.repository) should contain theSameElementsAs Seq(
          "warnings, exemptions and violations",
          "warnings and exemptions",
          "warnings and violations",
          "warnings",
          "exemptions and violations",
          "exemptions"
        )
      }

      "include warnings and violations" in new Setup {
        givenRepoSummariesWithAllCountCombinations(false)
        val results = service.repoSummaries(None, None, true, false, true, false).futureValue

        results._2.map(_.repository) should contain theSameElementsAs Seq(
          "warnings, exemptions and violations",
          "warnings and exemptions",
          "warnings and violations",
          "warnings",
          "exemptions and violations",
          "violations"
        )
      }

      "include warnings" in new Setup {
        givenRepoSummariesWithAllCountCombinations(false)
        val results = service.repoSummaries(None, None, true, false, false, false).futureValue

        results._2.map(_.repository) should contain theSameElementsAs Seq(
          "warnings, exemptions and violations",
          "warnings and exemptions",
          "warnings and violations",
          "warnings"
        )
      }

      "include exemptions and violations" in new Setup {
        givenRepoSummariesWithAllCountCombinations(false)
        val results = service.repoSummaries(None, None, false, true, true, false).futureValue

        results._2.map(_.repository) should contain theSameElementsAs Seq(
          "warnings, exemptions and violations",
          "warnings and exemptions",
          "warnings and violations",
          "exemptions and violations",
          "exemptions",
          "violations"
        )
      }

      "include exemptions" in new Setup {
        givenRepoSummariesWithAllCountCombinations(false)

        val results = service.repoSummaries(None, None, false, true, false, false).futureValue

        results._2.map(_.repository) should contain theSameElementsAs Seq(
          "warnings, exemptions and violations",
          "warnings and exemptions",
          "exemptions and violations",
          "exemptions",
        )
      }

      "include violations" in new Setup {
        givenRepoSummariesWithAllCountCombinations(false)

        val results = service.repoSummaries(None, None, false, false, true, false).futureValue

        results._2.map(_.repository) should contain theSameElementsAs Seq(
          "warnings, exemptions and violations",
          "warnings and violations",
          "exemptions and violations",
          "violations"
        )
      }

      "include no issues" in new Setup {
        givenRepoSummariesWithAllCountCombinations(true)

        val results = service.repoSummaries(None, None, false, false, false, true).futureValue

        results._2.map(_.repository) should contain theSameElementsAs Seq(
          "no issues"
        )
      }
    }

    "return branch summaries and" should {
      "only show branches with issues" in new Setup {
        givenRepoSummariesWithAllCountCombinations("test-repo", false)

        val results = service.branchSummaries("test-repo", false).futureValue

        results.map(_.branch) should contain theSameElementsAs Seq(
          "warnings, exemptions and violations",
          "warnings and exemptions",
          "warnings and violations",
          "warnings",
          "exemptions and violations",
          "exemptions",
          "violations"
        )
      }
      "include all branches" in new Setup {
        givenRepoSummariesWithAllCountCombinations("test-repo", true)
        val results = service.branchSummaries("test-repo", true).futureValue

        results.map(_.branch) should contain theSameElementsAs Seq(
          "warnings, exemptions and violations",
          "warnings and exemptions",
          "warnings and violations",
          "warnings",
          "exemptions and violations",
          "exemptions",
          "violations",
          "no issues"
        )
      }
    }

    "get unresolved leaks for a report and group by rule" in new Setup {
      when(connector.leakDetectionLeaks("reportId")).thenReturn(
        Future.successful(
          Seq(
            LeakDetectionLeak("rule1", "description1", "file1", "fileContent", 1, "/link-to-github/file1", "secret=123", List(Match(1, 6)), Priority.High, false),
            LeakDetectionLeak("rule1", "description1", "file1", "fileContent", 3, "/link-to-github/file1", "secret=12345", List(Match(1, 8)), Priority.High, true),
            LeakDetectionLeak("rule1", "description1", "file1", "fileContent", 9, "/link-to-github/file1", "the secret sauce", List(Match(5, 10)), Priority.High, false),
            LeakDetectionLeak("rule1", "description1", "file2", "fileContent", 3, "/link-to-github/file2", "my_secret: abc", List(Match(4, 9)), Priority.High, false),
            LeakDetectionLeak("rule2", "description2", "file2", "fileName", 5, "/link-to-github/file2", "---BEGIN---", List(Match(4, 8)), Priority.Medium, false)
          )
        )
      )

      val results = service.reportLeaks("reportId").futureValue

      results shouldBe Seq(
        LeakDetectionLeaksByRule(
          "rule1",
          "description1",
          "fileContent",
          Priority.High,
          Seq(
            LeakDetectionLeakDetails("file1", 1, "/link-to-github/file1", "secret=123", List(Match(1, 6))),
            LeakDetectionLeakDetails("file1", 9, "/link-to-github/file1", "the secret sauce", List(Match(5, 10))),
            LeakDetectionLeakDetails("file2", 3, "/link-to-github/file2", "my_secret: abc", List(Match(4, 9)))
          )
        ),
        LeakDetectionLeaksByRule(
          "rule2",
          "description2",
          "fileName",
          Priority.Medium,
          Seq(
            LeakDetectionLeakDetails("file2", 5, "/link-to-github/file2", "---BEGIN---", List(Match(4, 8)))
          )
        )
      )
    }

    "get exemptions for a report and group by rule" in new Setup {
      when(connector.leakDetectionLeaks("reportId")).thenReturn(
        Future.successful(
          Seq(
            LeakDetectionLeak("rule1", "description1", "file1", "fileContent", 1, "/link-to-github/file1", "secret=123", List(Match(1, 6)), Priority.High, false),
            LeakDetectionLeak("rule1", "description1", "file1", "fileContent", 3, "/link-to-github/file1", "secret=12345", List(Match(1, 8)), Priority.High, true),
            LeakDetectionLeak("rule1", "description1", "file1", "fileContent", 9, "/link-to-github/file1", "the secret sauce", List(Match(5, 10)), Priority.High, false),
            LeakDetectionLeak("rule1", "description1", "file2", "fileContent", 3, "/link-to-github/file2", "my_secret: abc", List(Match(4, 9)), Priority.High, false),
            LeakDetectionLeak("rule2", "description2", "file2", "fileName", 5, "/link-to-github/file2", "---BEGIN---", List(Match(4, 8)), Priority.Medium, false)
          )
        )
      )

      val results = service.reportExemptions("reportId").futureValue

      results shouldBe Seq(
        LeakDetectionLeaksByRule(
          "rule1",
          "description1",
          "fileContent",
          Priority.High,
          Seq(
            LeakDetectionLeakDetails("file1", 3, "/link-to-github/file1", "secret=12345", List(Match(1, 8)))
          )
        )
      )
    }
  }

  private trait Setup {
    val connector = mock[LeakDetectionConnector]

    private val configuration =
      Configuration(
        "lds.integrationEnabled" -> "true",
        "lds.noWarningsOn.0"     -> "a-repo-to-ignore"
      )

    val service = new LeakDetectionService(connector, configuration)

    val timestamp = LocalDateTime.now().minus(2, HOURS)

    def aRule              = LeakDetectionRule("", "", "", "", List(), List(), Priority.Low)
    def aRepositorySummary = LeakDetectionRepositorySummary("", true, timestamp, timestamp, 0, 0, 0, None)
    def aBranchSummary = LeakDetectionBranchSummary("", "", timestamp, 0, 0, 0)

    when(connector.leakDetectionRules()).thenReturn(Future.successful(Seq.empty))


    def givenRepoSummariesWithAllCountCombinations(includeNonIssues: Boolean) = when(connector.leakDetectionRepoSummaries(None, None, None, includeNonIssues, false)).thenReturn(
      Future.successful(Seq(
          aRepositorySummary.copy(repository = "warnings, exemptions and violations", warningCount = 1, excludedCount = 1, unresolvedCount = 1),
          aRepositorySummary.copy(repository = "warnings and exemptions", warningCount = 1, excludedCount = 1),
          aRepositorySummary.copy(repository = "warnings and violations", warningCount = 1, unresolvedCount = 1),
          aRepositorySummary.copy(repository = "warnings", warningCount = 1),
          aRepositorySummary.copy(repository = "exemptions and violations", excludedCount = 1, unresolvedCount = 1),
          aRepositorySummary.copy(repository = "exemptions", excludedCount = 1),
          aRepositorySummary.copy(repository = "violations", unresolvedCount = 1),
          aRepositorySummary.copy(repository = "no issues")
        )
      )
    )

    def givenRepoSummariesWithAllCountCombinations(repoName: String, includeNonIssues: Boolean) = when(connector.leakDetectionRepoSummaries(None, Some(repoName), None, includeNonIssues, true)).thenReturn(
      Future.successful(
        Seq(
          aRepositorySummary.copy(repository = "test-repo", branchSummary = Some(Seq(
            aBranchSummary.copy(branch = "warnings, exemptions and violations", warningCount = 1, excludedCount = 1, unresolvedCount = 1),
            aBranchSummary.copy(branch = "warnings and exemptions", warningCount = 1, excludedCount = 1),
            aBranchSummary.copy(branch = "warnings and violations", warningCount = 1, unresolvedCount = 1),
            aBranchSummary.copy(branch = "warnings", warningCount = 1),
            aBranchSummary.copy(branch = "exemptions and violations", excludedCount = 1, unresolvedCount = 1),
            aBranchSummary.copy(branch = "exemptions", excludedCount = 1),
            aBranchSummary.copy(branch = "violations", unresolvedCount = 1),
            aBranchSummary.copy(branch = "no issues")))
          ),
        )
      )
    )
  }
}
