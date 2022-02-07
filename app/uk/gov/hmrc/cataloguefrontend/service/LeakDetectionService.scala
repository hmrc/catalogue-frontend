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
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.cataloguefrontend.{routes => appRoutes}
import java.time.{Instant, LocalDateTime, ZoneId}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LeakDetectionService @Inject() (
  leakDetectionConnector: LeakDetectionConnector,
  configuration: Configuration
)(implicit val ec: ExecutionContext) {

  def scannedLocalDateTime(i: Instant) = LocalDateTime.ofInstant(i, ZoneId.systemDefault())

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

  def ruleSummaries()(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionRulesWithCounts]] =
    leakDetectionConnector
      .leakDetectionSummaries(None, None, None)
      .map(
        _.map(r =>
          LeakDetectionRulesWithCounts(
            r.rule,
            r.leaks.reduceOption(Ordering.by((_: LeakDetectionRepositorySummary).firstScannedAt).min).map(i => scannedLocalDateTime(i.firstScannedAt)),
            r.leaks.reduceOption(Ordering.by((_: LeakDetectionRepositorySummary).lastScannedAt).max).map(i => scannedLocalDateTime(i.lastScannedAt)),
            r.leaks.length,
            r.leaks.map(_.unresolvedCount).sum
          )
        ).sortBy(_.totalCount).reverse
      )

  def repoSummaries(rule: Option[String], team: Option[String])(implicit hc: HeaderCarrier): Future[(Seq[String], Seq[LeakDetectionReposWithCounts])] =
    leakDetectionConnector
      .leakDetectionSummaries(rule, None, team)
      .map(summaries =>
        (
          summaries.map(_.rule.id),
          summaries
            .flatMap(_.leaks)
            .groupBy(_.repository)
            .map {
              case (repoName, summaries) =>
                LeakDetectionReposWithCounts(
                  repoName,
                  summaries.reduceOption(Ordering.by((_: LeakDetectionRepositorySummary).firstScannedAt).min).map(i => scannedLocalDateTime(i.firstScannedAt)),
                  summaries.reduceOption(Ordering.by((_: LeakDetectionRepositorySummary).lastScannedAt).max).map(i => scannedLocalDateTime(i.lastScannedAt)),
                  summaries.map(_.unresolvedCount).sum
                )
            }
            .toSeq
            .sortBy(r => r.repoName)
        )
      )

  def branchSummaries(repo: String)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionBranchesWithCounts]] =
    leakDetectionConnector
      .leakDetectionSummaries(None, Some(repo), None)
      .map(
        _.flatMap(_.leaks)
          .groupBy(_.repository)
          .flatMap(_._2)
          .flatMap(_.branchSummary)
          .groupBy(_.branch)
          .map {
            case (branch, summaries) =>
              LeakDetectionBranchesWithCounts(
                branch,
                summaries.head.reportId,
                scannedLocalDateTime(summaries.head.scannedAt),
                summaries.map(_.unresolvedCount).sum
              )
          }
          .toSeq
          .sortBy(_.branch)
      )

  def report(repository: String, branch: String)(implicit hc: HeaderCarrier): Future[LeakDetectionReportWithLeaks] =
    for {
      report <- leakDetectionConnector.leakDetectionReport(repository, branch)
      leaks  <- leakDetectionConnector.leakDetectionLeaks(report._id)
    } yield LeakDetectionReportWithLeaks(
      report.repoName,
      report.branch,
      report._id,
      scannedLocalDateTime(report.timestamp),
      report.author,
      report.commitId,
      leaks
        .groupBy(l => l.ruleId)
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
}

final case class LeakDetectionBranchesWithCounts(branch: String, reportId: String, scannedAt: LocalDateTime, totalCount: Int)

final case class LeakDetectionReposWithCounts(repoName: String, firstScannedAt: Option[LocalDateTime], lastScannedAt: Option[LocalDateTime], totalCount: Int)

final case class LeakDetectionRulesWithCounts(rule: LeakDetectionRule, firstScannedAt: Option[LocalDateTime], lastScannedAt: Option[LocalDateTime], repoCount: Int, totalCount: Int)

final case class LeakDetectionReportWithLeaks(repoName: String, branch: String, reportId: String, scannedAt: LocalDateTime, author: String, commitId: String, leaks: Seq[LeakDetectionLeaksByRule])

final case class LeakDetectionLeaksByRule(ruleId: String, description: String, scope: String, priority: String, leaks: Seq[LeakDetectionLeakDetails])

final case class LeakDetectionLeakDetails(filePath: String, lineNumber: Int, urlToSource: String, lineText: String, matches: List[Match])
