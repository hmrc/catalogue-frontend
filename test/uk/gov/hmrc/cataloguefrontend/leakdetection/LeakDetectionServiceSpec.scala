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
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
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
          Some(timestamp.minus(3, HOURS)),
          Some(timestamp.plus(1, HOURS)),
          2,
          5
        ),
        LeakDetectionRulesWithCounts(
          aRule.copy(id = "rule-1"),
          Some(timestamp),
          Some(timestamp),
          1,
          3
        ),
        LeakDetectionRulesWithCounts(aRule.copy(id = "rule-3"), None, None, 0, 0)
      )
    }

    "get leaks for a report and group by rule" in new Setup {
      when(connector.leakDetectionLeaks("reportId")).thenReturn(
        Future.successful(
          Seq(
            LeakDetectionLeak("rule1", "description1", "file1", "fileContent", 1, "/link-to-github/file1", "secret=123", List(Match(1, 6)), "high"),
            LeakDetectionLeak("rule1", "description1", "file1", "fileContent", 9, "/link-to-github/file1", "the secret sauce", List(Match(5, 10)), "high"),
            LeakDetectionLeak("rule1", "description1", "file2", "fileContent", 3, "/link-to-github/file2", "my_secret: abc", List(Match(4, 9)), "high"),
            LeakDetectionLeak("rule2", "description2", "file2", "fileName", 5, "/link-to-github/file2", "---BEGIN---", List(Match(4, 8)), "medium")
          )
        )
      )

      val results = service.reportLeaks("reportId").futureValue

      results shouldBe Seq(
        LeakDetectionLeaksByRule(
          "rule1",
          "description1",
          "fileContent",
          "high",
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
          "medium",
          Seq(
            LeakDetectionLeakDetails("file2", 5, "/link-to-github/file2", "---BEGIN---", List(Match(4, 8)))
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

    def aRule              = LeakDetectionRule("", "", "", "", List(), List(), "")
    def aRepositorySummary = LeakDetectionRepositorySummary("", timestamp, timestamp, 0, 1, Seq())
    def aBranchSummary     = LeakDetectionBranchSummary("", "", timestamp, 0, 1)
  }
}
