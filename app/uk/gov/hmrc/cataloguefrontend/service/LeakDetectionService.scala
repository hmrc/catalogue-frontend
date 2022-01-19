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

import java.time.{Instant, LocalDateTime, ZoneId}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LeakDetectionService @Inject() (
  leakDetectionConnector: LeakDetectionConnector,
  configuration: Configuration
)(implicit val ec: ExecutionContext) {

  def urlIfLeaksFound(repoName: String)(implicit hc: HeaderCarrier): Future[Option[String]] =
    repositoriesWithLeaks.map { reposWithLeaks =>
      if (hasLeaks(reposWithLeaks)(repoName))
        Some(s"$leakDetectionPublicUrl/reports/repositories/$repoName")
      else
        None
    }

  private val leakDetectionPublicUrl: String    = configuration.get[String]("lds.publicUrl")
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

  def ruleSummaries()(implicit hc: HeaderCarrier):Future[Seq[LeakDetectionRulesWithCounts]] = {
    def scannedLocalDateTime(i: Instant) = LocalDateTime.ofInstant(i, ZoneId.systemDefault())

    leakDetectionConnector.leakDetectionRuleSummaries.map(_.map(r => LeakDetectionRulesWithCounts(
      r.rule,
      r.leaks.reduceOption(Ordering.by((_: LeakDetectionRepositorySummary).firstScannedAt).min).map(i => scannedLocalDateTime(i.firstScannedAt)),
      r.leaks.reduceOption(Ordering.by((_: LeakDetectionRepositorySummary).lastScannedAt).max).map(i => scannedLocalDateTime(i.lastScannedAt)),
      r.leaks.length,
      r.leaks.map(_.unresolvedCount).sum
    )))
  }
}

final case class LeakDetectionRulesWithCounts(rule: LeakDetectionRule,
                                              firstScannedAt: Option[LocalDateTime],
                                              lastScannedAt: Option[LocalDateTime],
                                              repoCount: Int,
                                              totalCount: Int)

