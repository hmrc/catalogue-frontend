/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, RepoType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.test.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import java.time.temporal.ChronoUnit.HOURS
import scala.concurrent.{ExecutionContext, Future}

class LeakDetectionServiceSpec extends UnitSpec with MockitoSugar {
  import ExecutionContext.Implicits.global

  private given HeaderCarrier = mock[HeaderCarrier]

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

      val results: Seq[LeakDetectionRulesWithCounts] = service.ruleSummaries().futureValue

      results shouldBe Seq(
        LeakDetectionRulesWithCounts(aRule.copy(id = "rule-2"), Some(timestamp.minus(3, HOURS)), Some(timestamp.plus(1, HOURS)), 2, 0, 5),
        LeakDetectionRulesWithCounts(aRule.copy(id = "rule-1"), Some(timestamp), Some(timestamp), 1, 0, 3),
        LeakDetectionRulesWithCounts(aRule.copy(id = "rule-3"), None, None, 0, 0, 0)
      )
    }

    "return repo summaries and" should {
      "include warnings, exemptions and violations" in new Setup {
        givenRepoSummariesWithAllCountCombinations(false)
        val results: Seq[LeakDetectionRepositorySummary] = service.repoSummaries(ruleId = None, team = None, digitalService = None, true, true, true, false).futureValue

        results.map(_.repository) should contain theSameElementsAs Seq(
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
        val results: Seq[LeakDetectionRepositorySummary] = service.repoSummaries(ruleId = None, team = None, digitalService = None, true, true, false, false).futureValue

        results.map(_.repository) should contain theSameElementsAs Seq(
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
        val results: Seq[LeakDetectionRepositorySummary] = service.repoSummaries(ruleId = None, team = None, digitalService = None, true, false, true, false).futureValue

        results.map(_.repository) should contain theSameElementsAs Seq(
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
        val results: Seq[LeakDetectionRepositorySummary] = service.repoSummaries(ruleId = None, team = None, digitalService = None, true, false, false, false).futureValue

        results.map(_.repository) should contain theSameElementsAs Seq(
          "warnings, exemptions and violations",
          "warnings and exemptions",
          "warnings and violations",
          "warnings"
        )
      }

      "include exemptions and violations" in new Setup {
        givenRepoSummariesWithAllCountCombinations(false)
        val results: Seq[LeakDetectionRepositorySummary] = service.repoSummaries(ruleId = None, team = None, digitalService = None, false, true, true, false).futureValue

        results.map(_.repository) should contain theSameElementsAs Seq(
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

        val results: Seq[LeakDetectionRepositorySummary] = service.repoSummaries(ruleId = None, team = None, digitalService = None, false, true, false, false).futureValue

        results.map(_.repository) should contain theSameElementsAs Seq(
          "warnings, exemptions and violations",
          "warnings and exemptions",
          "exemptions and violations",
          "exemptions",
        )
      }

      "include violations" in new Setup {
        givenRepoSummariesWithAllCountCombinations(false)

        val results: Seq[LeakDetectionRepositorySummary] = service.repoSummaries(ruleId = None, team = None, digitalService = None, false, false, true, false).futureValue

        results.map(_.repository) should contain theSameElementsAs Seq(
          "warnings, exemptions and violations",
          "warnings and violations",
          "exemptions and violations",
          "violations"
        )
      }

      "include no issues" in new Setup {
        givenRepoSummariesWithAllCountCombinations(true)

        val results: Seq[LeakDetectionRepositorySummary] = service.repoSummaries(ruleId = None, team = None, digitalService = None, false, false, false, true).futureValue

        results.map(_.repository) should contain theSameElementsAs Seq(
          "no issues"
        )
      }
    }

    "return branch summaries and" should {
      "only show branches with issues" in new Setup {
        givenRepoSummariesWithAllCountCombinations("test-repo", false)

        val results: Seq[LeakDetectionBranchSummary] = service.branchSummaries("test-repo", false).futureValue

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
        val results: Seq[LeakDetectionBranchSummary] = service.branchSummaries("test-repo", true).futureValue

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

      val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("reportId").futureValue

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

      val results: Seq[LeakDetectionLeaksByRule] = service.reportExemptions("reportId").futureValue

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

    "fix broken GitHub URLs - edge cases and error handling" should {
      
      "handle repository not found and fallback to main" in new Setup {
        when(teamsAndRepositoriesConnector.repositoryDetails("unknown-repo"))
          .thenReturn(Future.successful(None))
        
        when(connector.leakDetectionLeaks("reportId")).thenReturn(
          Future.successful(
            Seq(
              LeakDetectionLeak("rule1", "description1", "/src/test.scala", "fileContent", 10,
                "https://github.com/hmrc/unknown-repo/blame/n%2Fa%2Fsrc%2Ftest.scala#L10",
                "secret=123", List(Match(1, 6)), Priority.High, false)
            )
          )
        )

        val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("reportId").futureValue

        results.head.leaks.head.urlToSource shouldBe "https://github.com/hmrc/unknown-repo/blame/main/src/test.scala#L10"
      }
      
      "handle async timeout and fallback to main" in new Setup {
        when(teamsAndRepositoriesConnector.repositoryDetails("slow-repo"))
          .thenReturn(Future.failed(new RuntimeException("Timeout")))
        
        when(connector.leakDetectionLeaks("reportId")).thenReturn(
          Future.successful(
            Seq(
              LeakDetectionLeak("rule1", "description1", "/src/test.scala", "fileContent", 10,
                "https://github.com/hmrc/slow-repo/blame/n%2Fa%2Fsrc%2Ftest.scala#L10",
                "secret=123", List(Match(1, 6)), Priority.High, false)
            )
          )
        )

        val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("reportId").futureValue

        results.head.leaks.head.urlToSource shouldBe "https://github.com/hmrc/slow-repo/blame/main/src/test.scala#L10"
      }

      "handle different branch patterns correctly" in new Setup {
        when(teamsAndRepositoriesConnector.repositoryDetails("special-repo"))
          .thenReturn(Future.successful(Some(GitRepository(
            name = "special-repo",
            organisation = None,
            description = "Special repo",
            githubUrl = "https://github.com/hmrc/special-repo",
            createdDate = Instant.now(),
            lastActiveDate = Instant.now(),
            language = Some("JavaScript"),
            isArchived = false,
            defaultBranch = "develop" // Non-standard branch name
          ))))
        
        when(connector.leakDetectionLeaks("reportId")).thenReturn(
          Future.successful(
            Seq(
              // Test various encoded patterns
              LeakDetectionLeak("rule1", "description1", "/test.js", "fileContent", 1,
                "https://github.com/hmrc/special-repo/blame/n%2Fa%2Ftest.js#L1",
                "secret1", List(Match(1, 6)), Priority.High, false),
              LeakDetectionLeak("rule2", "description2", "/nested/path.js", "fileContent", 5,
                "https://github.com/hmrc/special-repo/blame/n%2Fa%2Fnested%2Fpath.js#L5",
                "secret2", List(Match(1, 6)), Priority.Medium, false),
              // Test normal URL that should remain unchanged
              LeakDetectionLeak("rule3", "description3", "/normal.js", "fileContent", 10,
                "https://github.com/hmrc/special-repo/blame/develop/normal.js#L10",
                "secret3", List(Match(1, 6)), Priority.Low, false)
            )
          )
        )

        val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("reportId").futureValue

        val allUrls: Seq[String] = results.flatMap(_.leaks.map(_.urlToSource))
        allUrls should contain("https://github.com/hmrc/special-repo/blame/develop/test.js#L1")
        allUrls should contain("https://github.com/hmrc/special-repo/blame/develop/nested/path.js#L5")
        allUrls should contain("https://github.com/hmrc/special-repo/blame/develop/normal.js#L10")
      }

      "handle malformed URLs gracefully" in new Setup {
        when(connector.leakDetectionLeaks("reportId")).thenReturn(
          Future.successful(
            Seq(
              // Test malformed GitHub URL
              LeakDetectionLeak("rule1", "description1", "/test.scala", "fileContent", 1,
                "https://invalid-url/blame/n%2Fa%2Ftest.scala#L1",
                "secret1", List(Match(1, 6)), Priority.High, false),
              // Test non-GitHub URL
              LeakDetectionLeak("rule2", "description2", "/test2.scala", "fileContent", 2,
                "https://gitlab.com/hmrc/repo/blame/n%2Fa%2Ftest2.scala#L2",
                "secret2", List(Match(1, 6)), Priority.Medium, false),
              // Test URL without proper structure
              LeakDetectionLeak("rule3", "description3", "/test3.scala", "fileContent", 3,
                "https://github.com/blame/n%2Fa%2Ftest3.scala#L3",
                "secret3", List(Match(1, 6)), Priority.Low, false)
            )
          )
        )

        val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("reportId").futureValue

        // Malformed URLs should remain unchanged
        val allUrls: Seq[String] = results.flatMap(_.leaks.map(_.urlToSource))
        allUrls should contain("https://invalid-url/blame/n%2Fa%2Ftest.scala#L1")
        allUrls should contain("https://gitlab.com/hmrc/repo/blame/n%2Fa%2Ftest2.scala#L2")
        allUrls should contain("https://github.com/blame/n%2Fa%2Ftest3.scala#L3")
      }

      "handle multiple repositories in same report" in new Setup {
        // Setup multiple repos with different default branches
        when(teamsAndRepositoriesConnector.repositoryDetails("repo1"))
          .thenReturn(Future.successful(Some(GitRepository(
            name = "repo1",
            organisation = None,
            description = "Repo 1",
            githubUrl = "https://github.com/hmrc/repo1",
            createdDate = Instant.now(),
            lastActiveDate = Instant.now(),
            repoType = RepoType.Service,
            language = Some("Scala"),
            isArchived = false,
            defaultBranch = "master"
          ))))
          
        when(teamsAndRepositoriesConnector.repositoryDetails("repo2"))
          .thenReturn(Future.successful(Some(GitRepository(
            name = "repo2",
            organisation = None,
            description = "Repo 2",
            githubUrl = "https://github.com/hmrc/repo2",
            createdDate = Instant.now(),
            lastActiveDate = Instant.now(),
            repoType = RepoType.Library,
            language = Some("Java"),
            isArchived = false,
            defaultBranch = "main"
          ))))
        
        when(connector.leakDetectionLeaks("reportId")).thenReturn(
          Future.successful(
            Seq(
              LeakDetectionLeak("rule1", "description1", "/src/Test1.scala", "fileContent", 10,
                "https://github.com/hmrc/repo1/blame/n%2Fa%2Fsrc%2FTest1.scala#L10",
                "secret1", List(Match(1, 6)), Priority.High, false),
              LeakDetectionLeak("rule2", "description2", "/src/Test2.java", "fileContent", 20,
                "https://github.com/hmrc/repo2/blame/n%2Fa%2Fsrc%2FTest2.java#L20",
                "secret2", List(Match(1, 6)), Priority.Medium, false)
            )
          )
        )

        val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("reportId").futureValue

        val allUrls: Seq[String] = results.flatMap(_.leaks.map(_.urlToSource))
        allUrls should contain("https://github.com/hmrc/repo1/blame/master/src/Test1.scala#L10")
        allUrls should contain("https://github.com/hmrc/repo2/blame/main/src/Test2.java#L20")
      }
    }

    "fix broken GitHub URLs when mapping leaks with actual default branch detection" in new Setup {
      // Mock repository details to return actual default branches
      when(teamsAndRepositoriesConnector.repositoryDetails("vault"))
        .thenReturn(Future.successful(Some(GitRepository(
          name = "vault",
          organisation = None,
          description = "Test vault repository",
          githubUrl = "https://github.com/hmrc/vault",
          createdDate = Instant.now(),
          lastActiveDate = Instant.now(),
          language = Some("Scala"),
          isArchived = false,
          defaultBranch = "master" // This is the key field we need
        ))))
      
      when(teamsAndRepositoriesConnector.repositoryDetails("test-repo"))
        .thenReturn(Future.successful(Some(GitRepository(
          name = "test-repo",
          organisation = None,
          description = "Test repository",
          githubUrl = "https://github.com/hmrc/test-repo", 
          createdDate = Instant.now(),
          lastActiveDate = Instant.now(),
          language = Some("Scala"),
          isArchived = false,
          defaultBranch = "main" // This is the key field we need
        ))))
          
      when(connector.leakDetectionLeaks("reportId")).thenReturn(
        Future.successful(
          Seq(
            // Test broken URL with n%2Fa encoding - should use actual default branch 'master'
            LeakDetectionLeak("rule1", "description1", "/builtin/credential/aws/backend_test.go", "fileContent", 475, 
              "https://github.com/hmrc/vault/blame/n%2Fa%2Fbuiltin%2Fcredential%2Faws%2Fbackend_test.go#L475", 
              "secret=123", List(Match(1, 6)), Priority.High, false),
            // Test normal URL (should remain unchanged)
            LeakDetectionLeak("rule1", "description1", "/app/views/includes/scripts.html", "fileContent", 9,
              "https://github.com/hmrc/PTA-prototype/blame/main/app/views/includes/scripts.html#L9", 
              "secret=456", List(Match(1, 6)), Priority.High, false),
            // Test another broken URL - should use actual default branch 'main'
            LeakDetectionLeak("rule2", "description2", "/src/main/scala/Test.scala", "fileContent", 100,
              "https://github.com/hmrc/test-repo/blame/n%2Fa%2Fsrc%2Fmain%2Fscala%2FTest.scala#L100",
              "secret=789", List(Match(1, 6)), Priority.Medium, false)
          )
        )
      )

      val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("reportId").futureValue

      results shouldBe Seq(
        LeakDetectionLeaksByRule(
          "rule1",
          "description1",
          "fileContent",
          Priority.High,
          Seq(
            // Normal URL remains unchanged
            LeakDetectionLeakDetails("/app/views/includes/scripts.html", 9, 
              "https://github.com/hmrc/PTA-prototype/blame/main/app/views/includes/scripts.html#L9", 
              "secret=456", List(Match(1, 6))),
            // Fixed URL uses actual default branch 'master' from repository info
            LeakDetectionLeakDetails("/builtin/credential/aws/backend_test.go", 475, 
              "https://github.com/hmrc/vault/blame/master/builtin/credential/aws/backend_test.go#L475", 
              "secret=123", List(Match(1, 6)))
          )
        ),
        LeakDetectionLeaksByRule(
          "rule2",
          "description2",
          "fileContent",
          Priority.Medium,
          Seq(
            // Fixed URL uses actual default branch 'main' from repository info
            LeakDetectionLeakDetails("/src/main/scala/Test.scala", 100,
              "https://github.com/hmrc/test-repo/blame/main/src/main/scala/Test.scala#L100",
              "secret=789", List(Match(1, 6)))
          )
        )
      )
    }

    "URL validation and performance" should {

      "handle batch URL fixing efficiently" in new Setup {
        // Setup multiple repos
        val repoNames: Seq[String] = (1 to 10).map(i => s"batch-repo-$i")
        
        repoNames.foreach { repoName =>
          when(teamsAndRepositoriesConnector.repositoryDetails(repoName))
            .thenReturn(Future.successful(Some(GitRepository(
              name = repoName,
              organisation = None,
              description = s"Batch repo $repoName",
              githubUrl = s"https://github.com/hmrc/$repoName",
              createdDate = Instant.now(),
              lastActiveDate = Instant.now(),
              repoType = RepoType.Service,
              language = Some("Scala"),
              isArchived = false,
              defaultBranch = if (repoName.endsWith("1") || repoName.endsWith("3") || repoName.endsWith("5")) "master" else "main"
            ))))
        }
        
        // Create multiple leaks for batch processing
        val batchLeaks: Seq[LeakDetectionLeak] = repoNames.zipWithIndex.flatMap { case (repoName, index) =>
          Seq(
            LeakDetectionLeak(s"rule-$index", s"description-$index", s"/src/Test$index.scala", "fileContent", index + 1,
              s"https://github.com/hmrc/$repoName/blame/n%2Fa%2Fsrc%2FTest$index.scala#L${index + 1}",
              s"secret-$index", List(Match(1, 6)), Priority.High, false)
          )
        }
        
        when(connector.leakDetectionLeaks("batchReportId")).thenReturn(
          Future.successful(batchLeaks)
        )

        val startTime: Long = System.currentTimeMillis()
        val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("batchReportId").futureValue
        val endTime: Long = System.currentTimeMillis()
        
        // Performance check - should complete within reasonable time
        val processingTime: Long = endTime - startTime
        processingTime should be < 5000L // Should complete within 5 seconds
        
        // Verify all URLs were fixed correctly
        val allUrls: Seq[String] = results.flatMap(_.leaks.map(_.urlToSource))
        allUrls should have size 10
        
        // Check that URLs were fixed according to each repo's default branch
        allUrls.count(_.contains("/blame/master/")) shouldBe 3 // repos ending with 1, 3, 5
        allUrls.count(_.contains("/blame/main/")) shouldBe 7   // remaining repos
      }

      "validate URL formats correctly" in new Setup {
        when(connector.leakDetectionLeaks("validationReportId")).thenReturn(
          Future.successful(
            Seq(
              // Valid GitHub URL with n%2Fa pattern
              LeakDetectionLeak("rule1", "description1", "/valid.scala", "fileContent", 1,
                "https://github.com/hmrc/valid-repo/blame/n%2Fa%2Fvalid.scala#L1",
                "secret1", List(Match(1, 6)), Priority.High, false),
              // URL without line number
              LeakDetectionLeak("rule2", "description2", "/no-line.scala", "fileContent", 2,
                "https://github.com/hmrc/test-repo/blame/n%2Fa%2Fno-line.scala",
                "secret2", List(Match(1, 6)), Priority.Medium, false),
              // URL with complex path
              LeakDetectionLeak("rule3", "description3", "/complex/nested/path.scala", "fileContent", 3,
                "https://github.com/hmrc/complex-repo/blame/n%2Fa%2Fcomplex%2Fnested%2Fpath.scala#L3",
                "secret3", List(Match(1, 6)), Priority.Low, false),
              // URL with special characters in filename
              LeakDetectionLeak("rule4", "description4", "/special-file_name.scala", "fileContent", 4,
                "https://github.com/hmrc/special-repo/blame/n%2Fa%2Fspecial-file_name.scala#L4",
                "secret4", List(Match(1, 6)), Priority.High, false),
              // Empty URL (edge case)
              LeakDetectionLeak("rule5", "description5", "/empty.scala", "fileContent", 5,
                "",
                "secret5", List(Match(1, 6)), Priority.Low, false),
              // URL with query parameters
              LeakDetectionLeak("rule6", "description6", "/query.scala", "fileContent", 6,
                "https://github.com/hmrc/query-repo/blame/n%2Fa%2Fquery.scala?tab=1&view=2#L6",
                "secret6", List(Match(1, 6)), Priority.Medium, false)
            )
          )
        )
        
        // Mock repository details for the valid repos
        when(teamsAndRepositoriesConnector.repositoryDetails("valid-repo"))
          .thenReturn(Future.successful(Some(GitRepository(
            name = "valid-repo", organisation = None, description = "", githubUrl = "",
            createdDate = Instant.now(), lastActiveDate = Instant.now(),
            repoType = RepoType.Service, language = Some("Scala"),
            isArchived = false, defaultBranch = "main"
          ))))
          
        when(teamsAndRepositoriesConnector.repositoryDetails("test-repo"))
          .thenReturn(Future.successful(Some(GitRepository(
            name = "test-repo", organisation = None, description = "", githubUrl = "",
            createdDate = Instant.now(), lastActiveDate = Instant.now(),
            repoType = RepoType.Library, language = Some("Scala"),
            isArchived = false, defaultBranch = "develop"
          ))))
          
        when(teamsAndRepositoriesConnector.repositoryDetails("complex-repo"))
          .thenReturn(Future.successful(Some(GitRepository(
            name = "complex-repo", organisation = None, description = "", githubUrl = "",
            createdDate = Instant.now(), lastActiveDate = Instant.now(),
            language = Some("JavaScript"),
            isArchived = false, defaultBranch = "master"
          ))))
          
        when(teamsAndRepositoriesConnector.repositoryDetails("special-repo"))
          .thenReturn(Future.successful(Some(GitRepository(
            name = "special-repo", organisation = None, description = "", githubUrl = "",
            createdDate = Instant.now(), lastActiveDate = Instant.now(),
            repoType = RepoType.Prototype, language = Some("Python"),
            isArchived = false, defaultBranch = "trunk"
          ))))
          
        when(teamsAndRepositoriesConnector.repositoryDetails("query-repo"))
          .thenReturn(Future.successful(Some(GitRepository(
            name = "query-repo", organisation = None, description = "", githubUrl = "",
            createdDate = Instant.now(), lastActiveDate = Instant.now(),
            repoType = RepoType.Service, language = Some("Go"),
            isArchived = false, defaultBranch = "release"
          ))))

        val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("validationReportId").futureValue
        val allUrls: Seq[String] = results.flatMap(_.leaks.map(_.urlToSource))
        
        // Verify URL transformations
        allUrls should contain("https://github.com/hmrc/valid-repo/blame/main/valid.scala#L1")
        allUrls should contain("https://github.com/hmrc/test-repo/blame/develop/no-line.scala")
        allUrls should contain("https://github.com/hmrc/complex-repo/blame/master/complex/nested/path.scala#L3")
        allUrls should contain("https://github.com/hmrc/special-repo/blame/trunk/special-file_name.scala#L4")
        allUrls should contain("") // Empty URL remains empty
        allUrls should contain("https://github.com/hmrc/query-repo/blame/release/query.scala?tab=1&view=2#L6")
      }

      "handle concurrent URL fixing without race conditions" in new Setup {
        // Setup concurrent repositories
        val concurrentRepos: Seq[String] = (1 to 5).map(i => s"concurrent-repo-$i")
        
        concurrentRepos.foreach { repoName =>
          when(teamsAndRepositoriesConnector.repositoryDetails(repoName))
            .thenReturn({
              // Simulate varying response times
              Thread.sleep(scala.util.Random.between(10, 100))
              Future.successful(Some(GitRepository(
                name = repoName, organisation = None, description = "", githubUrl = "",
                createdDate = Instant.now(), lastActiveDate = Instant.now(),
                repoType = RepoType.Service, language = Some("Scala"),
                isArchived = false, defaultBranch = "main"
              )))
            })
        }
        
        val concurrentLeaks: Seq[LeakDetectionLeak] = concurrentRepos.map { repoName =>
          LeakDetectionLeak("rule1", "description1", "/concurrent.scala", "fileContent", 1,
            s"https://github.com/hmrc/$repoName/blame/n%2Fa%2Fconcurrent.scala#L1",
            "secret", List(Match(1, 6)), Priority.High, false)
        }
        
        when(connector.leakDetectionLeaks("concurrentReportId")).thenReturn(
          Future.successful(concurrentLeaks)
        )

        val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("concurrentReportId").futureValue
        val allUrls: Seq[String] = results.flatMap(_.leaks.map(_.urlToSource))
        
        // All URLs should be fixed to use 'main' branch
        allUrls should have size 5
        allUrls.foreach { url =>
          url should include("/blame/main/concurrent.scala#L1")
          url should not include "n%2Fa"
        }
      }
    }

    "async error handling and timeouts" should {

      "handle partial repository failures gracefully" in new Setup {
        // Setup mixed success/failure scenarios
        when(teamsAndRepositoriesConnector.repositoryDetails("success-repo"))
          .thenReturn(Future.successful(Some(GitRepository(
            name = "success-repo", organisation = None, description = "", githubUrl = "",
            createdDate = Instant.now(), lastActiveDate = Instant.now(),
            repoType = RepoType.Service, language = Some("Scala"),
            isArchived = false, defaultBranch = "custom"
          ))))
          
        when(teamsAndRepositoriesConnector.repositoryDetails("failure-repo"))
          .thenReturn(Future.failed(new RuntimeException("Connection timeout")))
          
        when(teamsAndRepositoriesConnector.repositoryDetails("not-found-repo"))
          .thenReturn(Future.successful(None))
        
        when(connector.leakDetectionLeaks("mixedReportId")).thenReturn(
          Future.successful(
            Seq(
              LeakDetectionLeak("rule1", "description1", "/success.scala", "fileContent", 1,
                "https://github.com/hmrc/success-repo/blame/n%2Fa%2Fsuccess.scala#L1",
                "secret1", List(Match(1, 6)), Priority.High, false),
              LeakDetectionLeak("rule2", "description2", "/failure.scala", "fileContent", 2,
                "https://github.com/hmrc/failure-repo/blame/n%2Fa%2Ffailure.scala#L2",
                "secret2", List(Match(1, 6)), Priority.Medium, false),
              LeakDetectionLeak("rule3", "description3", "/not-found.scala", "fileContent", 3,
                "https://github.com/hmrc/not-found-repo/blame/n%2Fa%2Fnot-found.scala#L3",
                "secret3", List(Match(1, 6)), Priority.Low, false)
            )
          )
        )

        val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("mixedReportId").futureValue
        val allUrls: Seq[String] = results.flatMap(_.leaks.map(_.urlToSource))
        
        // Success case should use actual default branch
        allUrls should contain("https://github.com/hmrc/success-repo/blame/custom/success.scala#L1")
        
        // Failure cases should fallback to 'main'
        allUrls should contain("https://github.com/hmrc/failure-repo/blame/main/failure.scala#L2")
        allUrls should contain("https://github.com/hmrc/not-found-repo/blame/main/not-found.scala#L3")
      }

      "handle service unavailable scenarios" in new Setup {
        when(teamsAndRepositoriesConnector.repositoryDetails("unavailable-repo"))
          .thenReturn(Future.failed(new java.net.ConnectException("Service unavailable")))
        
        when(connector.leakDetectionLeaks("unavailableReportId")).thenReturn(
          Future.successful(
            Seq(
              LeakDetectionLeak("rule1", "description1", "/unavailable.scala", "fileContent", 1,
                "https://github.com/hmrc/unavailable-repo/blame/n%2Fa%2Funavailable.scala#L1",
                "secret", List(Match(1, 6)), Priority.High, false)
            )
          )
        )

        val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("unavailableReportId").futureValue
        
        // Should gracefully fallback to 'main' when service is unavailable
        results.head.leaks.head.urlToSource shouldBe "https://github.com/hmrc/unavailable-repo/blame/main/unavailable.scala#L1"
      }

      "handle null and invalid responses from repository service" in new Setup {
        when(teamsAndRepositoriesConnector.repositoryDetails("null-branch-repo"))
          .thenReturn(Future.successful(Some(GitRepository(
            name = "null-branch-repo", organisation = None, description = "", githubUrl = "",
            createdDate = Instant.now(), lastActiveDate = Instant.now(),
            repoType = RepoType.Service, language = Some("Scala"),
            isArchived = false, defaultBranch = null // Null default branch
          ))))
          
        when(teamsAndRepositoriesConnector.repositoryDetails("empty-branch-repo"))
          .thenReturn(Future.successful(Some(GitRepository(
            name = "empty-branch-repo", organisation = None, description = "", githubUrl = "",
            createdDate = Instant.now(), lastActiveDate = Instant.now(),
            repoType = RepoType.Service, language = Some("Scala"),
            isArchived = false, defaultBranch = "" // Empty default branch
          ))))
        
        when(connector.leakDetectionLeaks("invalidDataReportId")).thenReturn(
          Future.successful(
            Seq(
              LeakDetectionLeak("rule1", "description1", "/null-branch.scala", "fileContent", 1,
                "https://github.com/hmrc/null-branch-repo/blame/n%2Fa%2Fnull-branch.scala#L1",
                "secret1", List(Match(1, 6)), Priority.High, false),
              LeakDetectionLeak("rule2", "description2", "/empty-branch.scala", "fileContent", 2,
                "https://github.com/hmrc/empty-branch-repo/blame/n%2Fa%2Fempty-branch.scala#L2",
                "secret2", List(Match(1, 6)), Priority.Medium, false)
            )
          )
        )

        val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("invalidDataReportId").futureValue
        val allUrls: Seq[String] = results.flatMap(_.leaks.map(_.urlToSource))
        
        // Both should fallback to 'main' when default branch is null or empty
        allUrls should contain("https://github.com/hmrc/null-branch-repo/blame/main/null-branch.scala#L1")
        allUrls should contain("https://github.com/hmrc/empty-branch-repo/blame/main/empty-branch.scala#L2")
      }

      "handle async operations timing consistently" in new Setup {
        // Setup repos with predictable delays
        when(teamsAndRepositoriesConnector.repositoryDetails("fast-repo"))
          .thenReturn(Future.successful(Some(GitRepository(
            name = "fast-repo", organisation = None, description = "", githubUrl = "",
            createdDate = Instant.now(), lastActiveDate = Instant.now(),
            repoType = RepoType.Service, language = Some("Scala"),
            isArchived = false, defaultBranch = "fast"
          ))))
          
        when(teamsAndRepositoriesConnector.repositoryDetails("slow-repo"))
          .thenReturn({
            Thread.sleep(200) // Simulate slow response
            Future.successful(Some(GitRepository(
              name = "slow-repo", organisation = None, description = "", githubUrl = "",
              createdDate = Instant.now(), lastActiveDate = Instant.now(),
              repoType = RepoType.Service, language = Some("Scala"),
              isArchived = false, defaultBranch = "slow"
            )))
          })
        
        when(connector.leakDetectionLeaks("timingReportId")).thenReturn(
          Future.successful(
            Seq(
              LeakDetectionLeak("rule1", "description1", "/fast.scala", "fileContent", 1,
                "https://github.com/hmrc/fast-repo/blame/n%2Fa%2Ffast.scala#L1",
                "secret1", List(Match(1, 6)), Priority.High, false),
              LeakDetectionLeak("rule2", "description2", "/slow.scala", "fileContent", 2,
                "https://github.com/hmrc/slow-repo/blame/n%2Fa%2Fslow.scala#L2",
                "secret2", List(Match(1, 6)), Priority.Medium, false)
            )
          )
        )

        val startTime: Long = System.currentTimeMillis()
        val results: Seq[LeakDetectionLeaksByRule] = service.reportLeaks("timingReportId").futureValue
        val endTime: Long = System.currentTimeMillis()
        
        val processingTime: Long = endTime - startTime
        processingTime should be < 1000L // Should complete within 1 second despite slow repo
        
        val allUrls = results.flatMap(_.leaks.map(_.urlToSource))
        allUrls should contain("https://github.com/hmrc/fast-repo/blame/fast/fast.scala#L1")
        allUrls should contain("https://github.com/hmrc/slow-repo/blame/slow/slow.scala#L2")
      }
    }
  }

  private trait Setup {
    val connector: LeakDetectionConnector = mock[LeakDetectionConnector]
    val teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]

    private val configuration: Configuration =
      Configuration(
        "lds.integrationEnabled" -> "true"
      )

    val service: LeakDetectionService = LeakDetectionService(connector, configuration, teamsAndRepositoriesConnector)

    val timestamp: Instant = Instant.now().minus(2, HOURS)

    def aRule: LeakDetectionRule = LeakDetectionRule("", "", "", "", List(), List(), List(), Priority.Low, false)
    def aRepositorySummary: LeakDetectionRepositorySummary = LeakDetectionRepositorySummary("", true, timestamp, timestamp, 0, 0, 0, None)
    private def aBranchSummary: LeakDetectionBranchSummary = LeakDetectionBranchSummary("", "", timestamp, 0, 0, 0)

    when(connector.leakDetectionRules())
      .thenReturn(Future.successful(Seq.empty))

    def givenRepoSummariesWithAllCountCombinations(includeNonIssues: Boolean): org.mockito.stubbing.OngoingStubbing[scala.concurrent.Future[Seq[LeakDetectionRepositorySummary]]] =
      when(connector.leakDetectionRepoSummaries(ruleId = None, repo = None, team = None, digitalService = None, includeNonIssues, includeBranches =false))
        .thenReturn(Future.successful(
          Seq(
            aRepositorySummary.copy(repository = "warnings, exemptions and violations", warningCount = 1, excludedCount = 1, unresolvedCount = 1),
            aRepositorySummary.copy(repository = "warnings and exemptions", warningCount = 1, excludedCount = 1),
            aRepositorySummary.copy(repository = "warnings and violations", warningCount = 1, unresolvedCount = 1),
            aRepositorySummary.copy(repository = "warnings", warningCount = 1),
            aRepositorySummary.copy(repository = "exemptions and violations", excludedCount = 1, unresolvedCount = 1),
            aRepositorySummary.copy(repository = "exemptions", excludedCount = 1),
            aRepositorySummary.copy(repository = "violations", unresolvedCount = 1),
            aRepositorySummary.copy(repository = "no issues")
          )
      ))

    def givenRepoSummariesWithAllCountCombinations(repoName: String, includeNonIssues: Boolean): org.mockito.stubbing.OngoingStubbing[scala.concurrent.Future[Seq[LeakDetectionRepositorySummary]]] =
      when(connector.leakDetectionRepoSummaries(ruleId = None, repo = Some(repoName), team = None, digitalService = None, includeNonIssues, includeBranches = true))
        .thenReturn(Future.successful(
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
      ))
  }
}
