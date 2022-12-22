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

import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.leakdetection.{routes => appRoutes}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LeakDetectionService @Inject() (
  leakDetectionConnector: LeakDetectionConnector,
  configuration: Configuration
)(implicit val ec: ExecutionContext) {

  def resolutionUrl: String =
    configuration
      .get[String]("leakDetection.resolution.url")

  def urlIfLeaksFound(repoName: String)(implicit hc: HeaderCarrier): Future[Option[String]] =
    repositoriesWithLeaks.map { reposWithLeaks =>
      if (hasLeaks(reposWithLeaks)(repoName))
        Some(appRoutes.LeakDetectionController.branchSummaries(repoName).url)
      else
        None
    }

  private val ldsIntegrationEnabled: Boolean    = configuration.get[Boolean]("lds.integrationEnabled")

  def repositoriesWithLeaks(implicit hc: HeaderCarrier): Future[Seq[RepositoryWithLeaks]] =
    if (ldsIntegrationEnabled)
      leakDetectionConnector.repositoriesWithLeaks
    else
      Future.successful(Nil)

  def hasLeaks(reposWithLeaks: Seq[RepositoryWithLeaks])(repoName: String): Boolean =
    reposWithLeaks.exists(_.name == repoName)

  implicit val localDateOrdering: Ordering[LocalDateTime] =
    Ordering.by(_.toInstant(ZoneOffset.UTC))

  def ruleSummaries()(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionRulesWithCounts]] =
    leakDetectionConnector
      .leakDetectionSummaries(None, None, None)
      .map(
        _.filterNot(_.rule.draft)
          .map(summary =>
            LeakDetectionRulesWithCounts(
              summary.rule,
              summary.leaks.reduceOption[LeakDetectionRepositorySummary](Ordering.by((_: LeakDetectionRepositorySummary).firstScannedAt).min).map(s => s.firstScannedAt),
              summary.leaks.reduceOption[LeakDetectionRepositorySummary](Ordering.by((_: LeakDetectionRepositorySummary).lastScannedAt).max).map(s => s.lastScannedAt),
              summary.leaks.length,
              summary.leaks.map(_.excludedCount).sum,
              summary.leaks.map(_.unresolvedCount).sum
            )
        ).sortBy(_.unresolvedCount).reverse
      )

  def repoSummaries(
    ruleId           : Option[String],
    team             : Option[String],
    includeWarnings  : Boolean,
    includeExemptions: Boolean,
    includeViolations: Boolean,
    includeNonIssues : Boolean
  )(implicit
    hc: HeaderCarrier
  ): Future[Seq[LeakDetectionRepositorySummary]] =
    for {
      summaries         <- leakDetectionConnector.leakDetectionRepoSummaries(
                             ruleId           = ruleId,
                             repo             = None,
                             team             = team,
                             includeNonIssues = includeNonIssues,
                             includeBranches  = false
                           )
      filteredSummaries =  filterSummaries(summaries, includeWarnings, includeExemptions, includeViolations, includeNonIssues)
    } yield filteredSummaries.sortBy(_.repository.toLowerCase)

  def rules()(implicit headerCarrier: HeaderCarrier) =
    leakDetectionConnector.leakDetectionRules()

  def branchSummaries(repo: String, includeNonIssues: Boolean)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionBranchSummary]] =
    leakDetectionConnector
      .leakDetectionRepoSummaries(ruleId = None, repo = Some(repo), team = None, includeNonIssues = includeNonIssues, includeBranches = true)
      .map(_
        .flatMap(_.branchSummary.getOrElse(Seq.empty))
        .filter(b => includeNonIssues || b.totalCount > 0)
        .sortBy(_.branch.toLowerCase)
      )

  def report(repository: String, branch: String)(implicit hc: HeaderCarrier): Future[LeakDetectionReport] =
    leakDetectionConnector.leakDetectionReport(repository, branch)

  def draftReports(ruleId: Option[String])(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionReport]] =
    leakDetectionConnector.leakDetectionDraftReports(ruleId)

  def reportLeaks(reportId: String)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionLeaksByRule]] =
    leakDetectionConnector.leakDetectionLeaks(reportId)
      .map(_
        .filterNot(_.isExcluded)
        .groupBy(_.ruleId)
        .map {case (ruleId, leaks) => mapLeak(ruleId, leaks)}
        .toSeq
        .sorted
      )

  def reportExemptions(reportId: String)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionLeaksByRule]] =
    leakDetectionConnector.leakDetectionLeaks(reportId)
      .map(_
        .filter(_.isExcluded)
        .groupBy(_.ruleId)
        .map { case (ruleId, leaks) => mapLeak(ruleId, leaks) }
        .toSeq
        .sorted
      )

  def reportWarnings(reportId: String)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionWarning]] =
    leakDetectionConnector.leakDetectionWarnings(reportId)

  def rescan(repository: String, branch: String)(implicit hc: HeaderCarrier) =
   leakDetectionConnector.rescan(repository, branch)

  private def mapLeak(ruleId: String, leaks: Seq[LeakDetectionLeak]): LeakDetectionLeaksByRule =
    LeakDetectionLeaksByRule(
      ruleId,
      leaks.head.description,
      leaks.head.scope,
      leaks.head.priority,
      leaks
        .map(l => LeakDetectionLeakDetails(l.filePath, l.lineNumber, l.urlToSource, l.lineText, l.matches))
        .sortBy(_.lineNumber)
        .sortBy(_.filePath)
    )

  private def filterSummaries(summaries: Seq[LeakDetectionRepositorySummary], includeWarnings: Boolean, includeExemptions: Boolean, includeViolations: Boolean, includeNonIssues: Boolean): Seq[LeakDetectionRepositorySummary] = {
    def filter(s: LeakDetectionRepositorySummary) =
      (includeNonIssues && s.totalCount == 0 ||
        includeWarnings && (s.warningCount > 0)) ||
        (includeExemptions && (s.excludedCount > 0)) ||
        (includeViolations && (s.unresolvedCount > 0))

    summaries.filter(filter)
  }
}

final case class LeakDetectionRulesWithCounts(
  rule           : LeakDetectionRule,
  firstScannedAt : Option[LocalDateTime],
  lastScannedAt  : Option[LocalDateTime],
  repoCount      : Int,
  excludedCount  : Int,
  unresolvedCount: Int
)

final case class LeakDetectionLeaksByRule(
  ruleId     : String,
  description: String,
  scope      : String,
  priority   : Priority,
  leaks      : Seq[LeakDetectionLeakDetails]
)

object LeakDetectionLeaksByRule {
  implicit val order: Ordering[LeakDetectionLeaksByRule] =
    Ordering.by(l => (l.priority, l.leaks.length * -1)) //length DESC
}

final case class LeakDetectionLeakDetails(
  filePath   : String,
  lineNumber : Int,
  urlToSource: String,
  lineText   : String,
  matches    : List[Match]
)
