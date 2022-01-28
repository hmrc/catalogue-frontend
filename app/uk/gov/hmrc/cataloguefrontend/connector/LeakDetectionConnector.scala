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

package uk.gov.hmrc.cataloguefrontend.connector

import play.api.Logger
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class LeakDetectionConnector @Inject() (
  http: HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  private val url: String = servicesConfig.baseUrl("leak-detection")

  def repositoriesWithLeaks(implicit hc: HeaderCarrier): Future[Seq[RepositoryWithLeaks]] = {
    implicit val rwlr = RepositoryWithLeaks.reads
    http
      .GET[Seq[RepositoryWithLeaks]](
          url"$url/api/repository",
          headers = Seq("Accept" -> "application/json")
        )
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty
      }
  }

  def leakDetectionSummaries(rule: Option[String], repo: Option[String], team: Option[String])(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionSummary]] = {
    implicit val ldrs: Reads[LeakDetectionSummary] = LeakDetectionSummary.reads
    http.GET[Seq[LeakDetectionSummary]](
      url"$url/api/leaks/summary?rule=$rule&repository=$repo&team=$team",
      headers = Seq("Accept" -> "application/json")
    )
  }

  def leakDetectionReport(repository: String, branch: String)(implicit hc: HeaderCarrier): Future[LeakDetectionReport] = {
    implicit val ldrl: Reads[LeakDetectionReport] = LeakDetectionReport.reads
    http.GET[LeakDetectionReport](
      url"$url/api/$repository/$branch/report",
      headers = Seq("Accept" -> "application/json")
    )
  }

  def leakDetectionLeaks(reportId: String)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionLeak]] = {
    implicit val ldrl: Reads[LeakDetectionLeak] = LeakDetectionLeak.reads
    http.GET[Seq[LeakDetectionLeak]](
      url"$url/api/report/$reportId/leaks",
      headers = Seq("Accept" -> "application/json")
    )
  }
}

final case class RepositoryWithLeaks(name: String) extends AnyVal

object RepositoryWithLeaks {
  val reads: Reads[RepositoryWithLeaks] =
    implicitly[Reads[String]].map(RepositoryWithLeaks.apply)
}

final case class LeakDetectionSummary(rule: LeakDetectionRule, leaks: Seq[LeakDetectionRepositorySummary])

object LeakDetectionSummary {
  implicit val ldrr                      = LeakDetectionRule.reads
  implicit val ldrsr                     = LeakDetectionRepositorySummary.reads
  val reads: Reads[LeakDetectionSummary] = Json.reads[LeakDetectionSummary]
}

final case class LeakDetectionRule(
  id: String,
  scope: String,
  regex: String,
  description: String,
  ignoredFiles: List[String],
  ignoredExtensions: List[String],
  priority: String
)

object LeakDetectionRule {
  implicit val reads = Json.reads[LeakDetectionRule]
}

final case class LeakDetectionRepositorySummary(
  repository: String,
  firstScannedAt: Instant,
  lastScannedAt: Instant,
  unresolvedCount: Int,
  branchSummary: Seq[LeakDetectionBranchSummary]
)

object LeakDetectionRepositorySummary {
  implicit val ldbr  = LeakDetectionBranchSummary.reads
  implicit val reads = Json.reads[LeakDetectionRepositorySummary]
}

final case class LeakDetectionBranchSummary(
  branch: String,
  reportId: String,
  scannedAt: Instant,
  unresolvedCount: Int
)

object LeakDetectionBranchSummary {
  implicit val reads = Json.reads[LeakDetectionBranchSummary]
}

final case class LeakDetectionReport(
                                      repoName: String,
                                      branch: String,
                                      _id: String,
                                      timestamp: Instant,
                                      author: String,
                                      commitId: String
                                    )

object LeakDetectionReport {
  implicit val reads = Json.reads[LeakDetectionReport]
}

final case class LeakDetectionLeak(
  ruleId: String,
  description: String,
  filePath: String,
  scope: String,
  lineNumber: Int,
  urlToSource: String,
  lineText: String,
  matches: List[Match],
  priority: String
)

object LeakDetectionLeak {
  implicit val mr    = Match.reads
  implicit val reads = Json.reads[LeakDetectionLeak]
}

final case class Match(
  start: Int,
  end: Int
)

object Match {
  implicit val reads = Json.reads[Match]
}
