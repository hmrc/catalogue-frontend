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

import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, Reads, __}
import uk.gov.hmrc.cataloguefrontend.leakdetection.Priority.{High, Low, Medium}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class LeakDetectionConnector @Inject() (
  httpClient: HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  private val url: String = servicesConfig.baseUrl("leak-detection")

  def repositoriesWithLeaks(implicit hc: HeaderCarrier): Future[Seq[RepositoryWithLeaks]] = {
    implicit val rwlr = RepositoryWithLeaks.reads
    httpClient
      .GET[Seq[RepositoryWithLeaks]](url"$url/api/repository")
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty
      }
  }

  def leakDetectionSummaries(ruleId: Option[String], repo: Option[String], team: Option[String])(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionSummary]] = {
    implicit val ldrs: Reads[LeakDetectionSummary] = LeakDetectionSummary.reads
    httpClient
      .GET[Seq[LeakDetectionSummary]](url"$url/api/rules/summary?ruleId=$ruleId&repository=$repo&team=$team")
  }

  def leakDetectionRepoSummaries(ruleId: Option[String], repo: Option[String], team: Option[String], includeNonIssues: Boolean, includeBranches: Boolean)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionRepositorySummary]] = {
    implicit val ldrs: Reads[LeakDetectionRepositorySummary] = LeakDetectionRepositorySummary.reads
    val excludeNonIssues = !includeNonIssues
    httpClient
      .GET[Seq[LeakDetectionRepositorySummary]](url"$url/api/repositories/summary?ruleId=$ruleId&repository=$repo&team=$team&excludeNonIssues=$excludeNonIssues&includeBranches=$includeBranches")
  }

  def leakDetectionReport(repository: String, branch: String)(implicit hc: HeaderCarrier): Future[LeakDetectionReport] = {
    implicit val ldrl: Reads[LeakDetectionReport] = LeakDetectionReport.reads
    httpClient
      .GET[LeakDetectionReport](url"$url/api/$repository/$branch/report")
  }

  def leakDetectionLeaks(reportId: String)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionLeak]] = {
    implicit val ldrl: Reads[LeakDetectionLeak] = LeakDetectionLeak.reads
    httpClient
      .GET[Seq[LeakDetectionLeak]](url"$url/api/report/$reportId/leaks")
  }

  def leakDetectionWarnings(reportId: String)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionWarning]] = {
    implicit val ldrl: Reads[LeakDetectionWarning] = LeakDetectionWarning.reads
    httpClient
      .GET[Seq[LeakDetectionWarning]](url"$url/api/report/$reportId/warnings")
  }

  def leakDetectionRules()(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionRule]] = {
    implicit val ldrl: Reads[LeakDetectionRule] = LeakDetectionRule.reads
    httpClient
      .GET[Seq[LeakDetectionRule]](url"$url/api/rules")
  }

  def rescan(repository: String, branch: String)(implicit hc: HeaderCarrier): Future[LeakDetectionReport] = {
    implicit val ldrl: Reads[LeakDetectionReport] = LeakDetectionReport.reads
    httpClient
      .POSTEmpty[LeakDetectionReport](url"$url/admin/rescan/$repository/$branch")
  }

}

final case class RepositoryWithLeaks(name: String) extends AnyVal

object RepositoryWithLeaks {
  val reads: Reads[RepositoryWithLeaks] =
    implicitly[Reads[String]].map(RepositoryWithLeaks.apply)
}

sealed trait Priority {
  val name: String = this match {
    case High => "high"
    case Medium => "medium"
    case Low => "low"
  }
  protected val ordering: Int = this match {
    case High => 1
    case Medium => 2
    case Low => 3
  }
}

object Priority {
  implicit val order: Ordering[Priority] = Ordering.by(_.ordering)
  val reads: Reads[Priority] = Reads.StringReads.map {
    case "high" => High
    case "medium" => Medium
    case "low" => Low
    case p => throw new RuntimeException(s"Priority type '$p' unknown")
  }

  case object High extends Priority
  case object Medium extends Priority
  case object Low extends Priority
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
  priority: Priority
)

object LeakDetectionRule {
  implicit val pr = Priority.reads
  implicit val reads = Json.reads[LeakDetectionRule]
}

final case class LeakDetectionRepositorySummary(
  repository     : String,
  isArchived     : Boolean,
  firstScannedAt : LocalDateTime,
  lastScannedAt  : LocalDateTime,
  warningCount   : Int,
  excludedCount  : Int,
  unresolvedCount: Int,
  branchSummary  : Option[Seq[LeakDetectionBranchSummary]]
) {
  def totalCount:Int = warningCount + excludedCount + unresolvedCount
}

object LeakDetectionRepositorySummary {
  private implicit val ldbr  = LeakDetectionBranchSummary.reads
  implicit val reads =
    ( (__ \ "repository"     ).read[String]
    ~ (__ \ "isArchived"     ).readNullable[Boolean].map(_.getOrElse(false))
    ~ (__ \ "firstScannedAt" ).read[LocalDateTime]
    ~ (__ \ "lastScannedAt"  ).read[LocalDateTime]
    ~ (__ \ "warningCount"   ).read[Int]
    ~ (__ \ "excludedCount"  ).read[Int]
    ~ (__ \ "unresolvedCount").read[Int]
    ~ (__ \ "branchSummary"  ).readNullable[Seq[LeakDetectionBranchSummary]]
    )(LeakDetectionRepositorySummary.apply _)
}

final case class LeakDetectionBranchSummary(
  branch: String,
  reportId: String,
  scannedAt: LocalDateTime,
  warningCount: Int,
  excludedCount: Int,
  unresolvedCount: Int
) {
  def totalCount: Int = warningCount + excludedCount + unresolvedCount
}

object LeakDetectionBranchSummary {
  val reads = Json.reads[LeakDetectionBranchSummary]
}

final case class UnusedExemption(
                                  ruleId: String,
                                  filePath: String,
                                  text: Option[String]
                                )

object UnusedExemption {
  implicit val format = Json.format[UnusedExemption]
}

final case class LeakDetectionReport(
  repoName: String,
  branch: String,
  _id: String,
  timestamp: LocalDateTime,
  author: String,
  commitId: String,
  exclusions: Map[String, Int],
  unusedExemptions: Seq[UnusedExemption]
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
  priority: Priority,
  isExcluded: Boolean
)

object LeakDetectionLeak {
  implicit val mr    = Match.reads
  implicit val pr    = Priority.reads
  implicit val reads = Json.reads[LeakDetectionLeak]
}

final case class Match(
  start: Int,
  end: Int
)

object Match {
  implicit val reads = Json.reads[Match]
}

final case class LeakDetectionWarning(message: String)

object LeakDetectionWarning {
  implicit val reads = Json.reads[LeakDetectionWarning]
}
