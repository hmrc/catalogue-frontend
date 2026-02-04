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

import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.leakdetection.{routes => appRoutes}
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, TeamName}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LeakDetectionService @Inject() (
  leakDetectionConnector       : LeakDetectionConnector,
  configuration                : Configuration,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
)(using
  ExecutionContext
):
  def resolutionUrl: String =
    configuration
      .get[String]("leakDetection.resolution.url")

  def removeSensitiveInfoUrl(): String =
    configuration
      .get[String]("leakDetection.removeSensitiveInfo.url")

  def urlIfLeaksFound(repoName: String)(using HeaderCarrier): Future[Option[String]] =
    repositoriesWithLeaks().map: reposWithLeaks =>
      if   reposWithLeaks.exists(_.name == repoName)
      then Some(appRoutes.LeakDetectionController.branchSummaries(repoName).url)
      else None

  private val ldsIntegrationEnabled: Boolean =
    configuration.get[Boolean]("lds.integrationEnabled")

  def repositoriesWithLeaks()(using HeaderCarrier): Future[Seq[RepositoryWithLeaks]] =
    if ldsIntegrationEnabled
    then leakDetectionConnector.repositoriesWithLeaks()
    else Future.successful(Nil)

  def ruleSummaries()(using HeaderCarrier): Future[Seq[LeakDetectionRulesWithCounts]] =
    leakDetectionConnector
      .leakDetectionSummaries(None, None, None)
      .map:
        _
          .filterNot(_.rule.draft)
          .map: summary =>
            LeakDetectionRulesWithCounts(
              summary.rule,
              summary.leaks.reduceOption[LeakDetectionRepositorySummary](Ordering.by((_: LeakDetectionRepositorySummary).firstScannedAt).min).map(s => s.firstScannedAt),
              summary.leaks.reduceOption[LeakDetectionRepositorySummary](Ordering.by((_: LeakDetectionRepositorySummary).lastScannedAt).max).map(s => s.lastScannedAt),
              summary.leaks.length,
              summary.leaks.map(_.excludedCount).sum,
              summary.leaks.map(_.unresolvedCount).sum
            )
          .sortBy(_.unresolvedCount).reverse

  def repoSummaries(
    ruleId           : Option[String]         = None,
    team             : Option[TeamName]       = None,
    digitalService   : Option[DigitalService] = None,
    includeWarnings  : Boolean,
    includeExemptions: Boolean,
    includeViolations: Boolean,
    includeNonIssues : Boolean
  )(using
    HeaderCarrier
  ): Future[Seq[LeakDetectionRepositorySummary]] =
    for
      summaries         <- leakDetectionConnector.leakDetectionRepoSummaries(
                             ruleId           = ruleId,
                             repo             = None,
                             team             = team,
                             digitalService   = digitalService,
                             includeNonIssues = includeNonIssues,
                             includeBranches  = false
                           )
      filteredSummaries =  summaries.filter: s =>
                                  (includeNonIssues  && (s.totalCount      == 0))
                               || (includeWarnings   && (s.warningCount    >  0))
                               || (includeExemptions && (s.excludedCount   >  0))
                               || (includeViolations && (s.unresolvedCount >  0))
    yield filteredSummaries.sortBy(_.repository.toLowerCase)

  def rules()(using HeaderCarrier): Future[Seq[LeakDetectionRule]] =
    leakDetectionConnector.leakDetectionRules()

  def branchSummaries(repo: String, includeNonIssues: Boolean)(using HeaderCarrier): Future[Seq[LeakDetectionBranchSummary]] =
    leakDetectionConnector
      .leakDetectionRepoSummaries(ruleId = None, repo = Some(repo), team = None, digitalService = None, includeNonIssues = includeNonIssues, includeBranches = true)
      .map:
        _
          .flatMap(_.branchSummary.getOrElse(Seq.empty))
          .filter(b => includeNonIssues || b.totalCount > 0)
          .sortBy(_.branch.toLowerCase)

  def report(repository: String, branch: String)(using HeaderCarrier): Future[LeakDetectionReport] =
    leakDetectionConnector.leakDetectionReport(repository, branch)

  def draftReports(ruleId: Option[String])(using HeaderCarrier): Future[Seq[LeakDetectionReport]] =
    leakDetectionConnector.leakDetectionDraftReports(ruleId)

  def reportLeaks(reportId: String)(using HeaderCarrier): Future[Seq[LeakDetectionLeaksByRule]] =
    buildLeaksByRule(reportId, leak => !leak.isExcluded)

  def reportExemptions(reportId: String)(using HeaderCarrier): Future[Seq[LeakDetectionLeaksByRule]] =
    buildLeaksByRule(reportId, _.isExcluded)

  def reportWarnings(reportId: String)(using HeaderCarrier): Future[Seq[LeakDetectionWarning]] =
    leakDetectionConnector.leakDetectionWarnings(reportId)

  def rescan(repository: String, branch: String)(using HeaderCarrier): Future[LeakDetectionReport] =
   leakDetectionConnector.rescan(repository, branch)

  private def buildLeaksByRule(reportId: String, predicate: LeakDetectionLeak => Boolean)(using HeaderCarrier): Future[Seq[LeakDetectionLeaksByRule]] =
    leakDetectionConnector
      .leakDetectionLeaks(reportId)
      .flatMap: leaks =>
        val filtered = leaks.filter(predicate)
        Future
          .traverse(filtered): leak =>
            fixGithubUrl(leak.urlToSource).map: fixedUrl =>
              leak.copy(urlToSource = fixedUrl)
          .map: fixedLeaks =>
            fixedLeaks
              .groupBy(_.ruleId)
              .map((ruleId, leaks) => mapLeak(ruleId, leaks))
              .toSeq
              .sorted

  private def mapLeak(ruleId: String, leaks: Seq[LeakDetectionLeak]): LeakDetectionLeaksByRule =
    LeakDetectionLeaksByRule(
      ruleId,
      leaks.head.description,
      leaks.head.scope,
      leaks.head.priority,
      leaks
        .map(l => LeakDetectionLeakDetails(l.filePath, l.lineNumber, l.urlToSource, l.lineText, l.matches)) // URLs already fixed
        .sortBy(_.lineNumber)
        .sortBy(_.filePath)
    )

  /**
   * Fixes broken GitHub blame URLs where branch name is encoded as 'n%2Fa'
   * Enhanced version that detects actual default branch from repository info
   * Example: 
   * - Input: https://github.com/hmrc/vault/blame/n%2Fa%2Fbuiltin%2Fcredential%2Faws%2Fbackend_test.go#L475
   * - Output: https://github.com/hmrc/vault/blame/master/builtin/credential/aws/backend_test.go#L475 (if master is default)
   * - Output: https://github.com/hmrc/vault/blame/main/builtin/credential/aws/backend_test.go#L475 (if main is default)
   */
  private def fixGithubUrl(originalUrl: String)(using HeaderCarrier): Future[String] =
    val url = originalUrl.replaceFirst("^https://api\\.github\\.com/repos/", "https://github.com/")

    // Pattern to match GitHub URLs with n%2Fa encoding issue
    val brokenUrlPattern = "(https://github\\.com/([^/]+)/([^/]+)/blame/)n%2Fa(%2F.+)".r

    url match
      case brokenUrlPattern(prefix, org, repo, encodedPath) =>
        // Decode the file path
        val decodedPath = java.net.URLDecoder.decode(encodedPath, "UTF-8")

        // Get the actual default branch from repository info
        getDefaultBranch(repo).map: defaultBranch =>
          s"$prefix$defaultBranch$decodedPath"
      case _ =>
        Future.successful(url)

  /**
   * Gets the default branch for a repository from TeamsAndRepositoriesConnector
   * Falls back to 'main' if repository info not available
   */
  private def getDefaultBranch(repoName: String)(using HeaderCarrier): Future[String] =
    teamsAndRepositoriesConnector.repositoryDetails(repoName)
      .map:
        case Some(repo) if repo.defaultBranch.nonEmpty => repo.defaultBranch
        case _ => "main" // Fallback to 'main' if no info available
      .recover:
        case _: Exception =>
          // If any error occurs (API unavailable, etc.), fallback to 'main'
          "main"

end LeakDetectionService

case class LeakDetectionRulesWithCounts(
  rule           : LeakDetectionRule,
  firstScannedAt : Option[Instant],
  lastScannedAt  : Option[Instant],
  repoCount      : Int,
  excludedCount  : Int,
  unresolvedCount: Int
)

case class LeakDetectionLeaksByRule(
  ruleId     : String,
  description: String,
  scope      : String,
  priority   : Priority,
  leaks      : Seq[LeakDetectionLeakDetails]
)

object LeakDetectionLeaksByRule {
  given Ordering[LeakDetectionLeaksByRule] =
    Ordering.by(l => (l.priority, l.leaks.length * -1)) //length DESC
}

case class LeakDetectionLeakDetails(
  filePath   : String,
  lineNumber : Int,
  urlToSource: String,
  lineText   : String,
  matches    : List[Match]
)
