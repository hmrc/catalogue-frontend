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

import java.time.{LocalDateTime, ZoneId}
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

  def ruleSummaries()(implicit hc: HeaderCarrier):Future[Seq[RuleSummary]] = {
    val orderByScannedAt = Ordering.by((_: Violation).scannedAt)
    def scannedLocalDateTime(v: Violation) = LocalDateTime.ofInstant(v.scannedAt, ZoneId.systemDefault())

    leakDetectionConnector.ruleViolations.map(_.map(r => RuleSummary(
      r.rule,
      r.violations.reduceOption(orderByScannedAt.min).map(scannedLocalDateTime),
      r.violations.reduceOption(orderByScannedAt.max).map(scannedLocalDateTime),
      r.violations.length,
      r.violations.map(_.unresolvedCount).sum
    )))
  }
}

final case class RuleSummary(rule: Rule, earliestScannedAt: Option[LocalDateTime], latestScannedAt: Option[LocalDateTime], repoCount: Int, totalCount: Int)

