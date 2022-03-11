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
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.leakdetection.{routes => appRoutes}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Instant, LocalDateTime, ZoneId, ZoneOffset}
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
  private val repositoriesToIgnore: Seq[String] = configuration.get[Seq[String]]("lds.noWarningsOn")

  def repositoriesWithLeaks(implicit hc: HeaderCarrier): Future[Seq[RepositoryWithLeaks]] =
    if (ldsIntegrationEnabled)
      leakDetectionConnector.repositoriesWithLeaks
    else
      Future.successful(Nil)

  def hasLeaks(reposWithLeaks: Seq[RepositoryWithLeaks])(repoName: String): Boolean =
    reposWithLeaks.exists(_.name == repoName)

  def teamHasLeaks(team: Team, reposWithLeaks: Seq[RepositoryWithLeaks]): Boolean = {
    val teamRepos: Seq[String] = team.repos
      .map(_.values.toList.flatten)
      .getOrElse(Nil)
      .filterNot(repositoriesToIgnore.contains)
    teamRepos.intersect(reposWithLeaks.map(_.name)).nonEmpty
  }

  implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(_.toInstant(ZoneOffset.UTC))
  def ruleSummaries()(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionRulesWithCounts]] =
    leakDetectionConnector
      .leakDetectionSummaries(None, None, None)
      .map(
        _.map(summary =>
          LeakDetectionRulesWithCounts(
            summary.rule,
            summary.leaks.reduceOption(Ordering.by((_: LeakDetectionRepositorySummary).firstScannedAt).min).map(s => s.firstScannedAt),
            summary.leaks.reduceOption(Ordering.by((_: LeakDetectionRepositorySummary).lastScannedAt).max).map(s => s.lastScannedAt),
            summary.leaks.length,
            summary.leaks.map(_.excludedCount).sum,
            summary.leaks.map(_.unresolvedCount).sum
          )
        ).sortBy(_.unresolvedCount).reverse
      )

  def repoSummaries(rule: Option[String], team: Option[String])(implicit hc: HeaderCarrier): Future[(Seq[String], Seq[LeakDetectionRepositorySummary])] =
    for {
      rules <- leakDetectionConnector.leakDetectionRules
      summaries <- leakDetectionConnector.leakDetectionRepoSummaries(rule, None, team)
    } yield (rules.map(_.id), summaries.sortBy(_.repository))

  def branchSummaries(repo: String)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionBranchSummary]] =
    leakDetectionConnector
      .leakDetectionRepoSummaries(None, Some(repo), None)
      .map(_.flatMap(_.branchSummary).sortBy(_.branch))

  def report(repository: String, branch: String)(implicit hc: HeaderCarrier): Future[LeakDetectionReport] =
    leakDetectionConnector.leakDetectionReport(repository, branch)

  def reportLeaks(reportId: String)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionLeaksByRule]] =
    leakDetectionConnector.leakDetectionLeaks(reportId).map(_.groupBy(l => l.ruleId)
      .map {
        case (ruleId, leaks) =>
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
      }
      .toSeq
      .sortBy(_.leaks.length)
      .reverse
      .sortBy(_.priority)
    )

  def reportWarnings(reportId: String)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionWarning]] =
    leakDetectionConnector.leakDetectionWarnings(reportId)
}

final case class LeakDetectionRulesWithCounts(rule: LeakDetectionRule, firstScannedAt: Option[LocalDateTime], lastScannedAt: Option[LocalDateTime], repoCount: Int, excludedCount: Int, unresolvedCount: Int)
final case class LeakDetectionLeaksByRule(ruleId: String, description: String, scope: String, priority: String, leaks: Seq[LeakDetectionLeakDetails])
final case class LeakDetectionLeakDetails(filePath: String, lineNumber: Int, urlToSource: String, lineText: String, matches: List[Match])
