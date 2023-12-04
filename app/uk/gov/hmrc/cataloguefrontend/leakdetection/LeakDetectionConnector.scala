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

import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, Reads, __}
import uk.gov.hmrc.cataloguefrontend.leakdetection.Priority.{High, Low, Medium}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class LeakDetectionConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  private val url: String = servicesConfig.baseUrl("leak-detection")

  def repositoriesWithLeaks(implicit hc: HeaderCarrier): Future[Seq[RepositoryWithLeaks]] = {
    implicit val rwlr = RepositoryWithLeaks.reads
    httpClientV2
      .get(url"$url/api/repository")
      .execute[Seq[RepositoryWithLeaks]]
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty
      }
  }

  def leakDetectionSummaries(
    ruleId: Option[String],
    repo  : Option[String],
    team  : Option[String]
  )(implicit
    hc    : HeaderCarrier
  ): Future[Seq[LeakDetectionSummary]] = {
    implicit val ldrs: Reads[LeakDetectionSummary] = LeakDetectionSummary.reads
    httpClientV2
      .get(url"$url/api/rules/summary?ruleId=$ruleId&repository=$repo&team=$team")
      .execute[Seq[LeakDetectionSummary]]
  }

  def leakDetectionRepoSummaries(
    ruleId          : Option[String],
    repo            : Option[String],
    team            : Option[String],
    includeNonIssues: Boolean,
    includeBranches : Boolean
  )(implicit
    hc: HeaderCarrier
  ): Future[Seq[LeakDetectionRepositorySummary]] = {
    implicit val ldrs: Reads[LeakDetectionRepositorySummary] = LeakDetectionRepositorySummary.reads
    val excludeNonIssues = !includeNonIssues
    httpClientV2
      .get(url"$url/api/repositories/summary?ruleId=$ruleId&repository=$repo&team=$team&excludeNonIssues=$excludeNonIssues&includeBranches=$includeBranches")
      .execute[Seq[LeakDetectionRepositorySummary]]
  }

  def leakDetectionDraftReports(ruleId: Option[String])(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionReport]] = {
    implicit val ldrs: Reads[LeakDetectionReport] = LeakDetectionReport.reads
    httpClientV2
      .get(url"$url/admin/draft?rule=$ruleId")
      .execute[Seq[LeakDetectionReport]]
  }

  def leakDetectionReport(repository: String, branch: String)(implicit hc: HeaderCarrier): Future[LeakDetectionReport] = {
    implicit val ldrl: Reads[LeakDetectionReport] = LeakDetectionReport.reads
    httpClientV2
      .get(url"$url/api/$repository/$branch/report")
      .execute[LeakDetectionReport]
  }

  def leakDetectionLeaks(reportId: String)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionLeak]] = {
    implicit val ldrl: Reads[LeakDetectionLeak] = LeakDetectionLeak.reads
    httpClientV2
      .get(url"$url/api/report/$reportId/leaks")
      .execute[Seq[LeakDetectionLeak]]
  }

  def leakDetectionWarnings(reportId: String)(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionWarning]] = {
    implicit val ldrl: Reads[LeakDetectionWarning] = LeakDetectionWarning.reads
    httpClientV2
      .get(url"$url/api/report/$reportId/warnings")
      .execute[Seq[LeakDetectionWarning]]
  }

  def leakDetectionRules()(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionRule]] = {
    implicit val ldrl: Reads[LeakDetectionRule] = LeakDetectionRule.reads
    httpClientV2
      .get(url"$url/api/rules")
      .execute[Seq[LeakDetectionRule]]
  }

  def rescan(repository: String, branch: String)(implicit hc: HeaderCarrier): Future[LeakDetectionReport] = {
    implicit val ldrl: Reads[LeakDetectionReport] = LeakDetectionReport.reads
    httpClientV2
      .post(url"$url/admin/rescan/$repository/$branch?mode=normal")
      .execute[LeakDetectionReport]
  }
}

final case class RepositoryWithLeaks(name: String) extends AnyVal

object RepositoryWithLeaks {
  val reads: Reads[RepositoryWithLeaks] =
    implicitly[Reads[String]].map(RepositoryWithLeaks.apply)
}

sealed trait Priority {
  val name: String =
    this match {
      case High   => "high"
      case Medium => "medium"
      case Low    => "low"
    }

  protected val ordering: Int =
    this match {
      case High   => 1
      case Medium => 2
      case Low    => 3
    }
}

object Priority {
  implicit val order: Ordering[Priority] =
    Ordering.by(_.ordering)

  val reads: Reads[Priority] =
    Reads.StringReads.map {
      case "high"   => High
      case "medium" => Medium
      case "low"    => Low
      case p        => throw new RuntimeException(s"Priority type '$p' unknown")
    }

  case object High   extends Priority
  case object Medium extends Priority
  case object Low    extends Priority
}

final case class LeakDetectionSummary(
  rule : LeakDetectionRule,
  leaks: Seq[LeakDetectionRepositorySummary]
)

object LeakDetectionSummary {
  val reads: Reads[LeakDetectionSummary] = {
    implicit val ldrr : Reads[LeakDetectionRule]              = LeakDetectionRule.reads
    implicit val ldrsr: Reads[LeakDetectionRepositorySummary] = LeakDetectionRepositorySummary.reads
    ( (__ \ "rule" ).read[LeakDetectionRule]
    ~ (__ \ "leaks").read[Seq[LeakDetectionRepositorySummary]]
    )(LeakDetectionSummary.apply _)
  }
}

final case class LeakDetectionRule(
  id               : String,
  scope            : String,
  regex            : String,
  description      : String,
  ignoredFiles     : List[String],
  ignoredExtensions: List[String],
  ignoredContent   : List[String],
  priority         : Priority,
  draft            : Boolean
)

object LeakDetectionRule {
  val reads: Reads[LeakDetectionRule] = {
    implicit val pr: Reads[Priority] = Priority.reads
    ( (__ \ "id"               ).read[String]
    ~ (__ \ "scope"            ).read[String]
    ~ (__ \ "regex"            ).read[String]
    ~ (__ \ "description"      ).read[String]
    ~ (__ \ "ignoredFiles"     ).read[List[String]]
    ~ (__ \ "ignoredExtensions").read[List[String]]
    ~ (__ \ "ignoredContent"   ).read[List[String]]
    ~ (__ \ "priority"         ).read[Priority]
    ~ (__ \ "draft"            ).read[Boolean]
    )(LeakDetectionRule.apply _)
  }
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
  def totalCount: Int =
    warningCount + excludedCount + unresolvedCount
}

object LeakDetectionRepositorySummary {
  val reads: Reads[LeakDetectionRepositorySummary] = {
    implicit val ldbr: Reads[LeakDetectionBranchSummary]= LeakDetectionBranchSummary.reads
    ( (__ \ "repository"     ).read[String]
    ~ (__ \ "isArchived"     ).read[Boolean]
    ~ (__ \ "firstScannedAt" ).read[LocalDateTime]
    ~ (__ \ "lastScannedAt"  ).read[LocalDateTime]
    ~ (__ \ "warningCount"   ).read[Int]
    ~ (__ \ "excludedCount"  ).read[Int]
    ~ (__ \ "unresolvedCount").read[Int]
    ~ (__ \ "branchSummary"  ).readNullable[Seq[LeakDetectionBranchSummary]]
    )(LeakDetectionRepositorySummary.apply _)
  }
}

final case class LeakDetectionBranchSummary(
  branch         : String,
  reportId       : String,
  scannedAt      : LocalDateTime,
  warningCount   : Int,
  excludedCount  : Int,
  unresolvedCount: Int
) {
  def totalCount: Int =
    warningCount + excludedCount + unresolvedCount
}

object LeakDetectionBranchSummary {
  val reads: Reads[LeakDetectionBranchSummary] =
    Json.reads[LeakDetectionBranchSummary]
}

final case class UnusedExemption(
  ruleId  : String,
  filePath: String,
  text    : Option[String]
)

object UnusedExemption {
  val reads: Reads[UnusedExemption] =
    Json.reads[UnusedExemption]
}

final case class LeakDetectionReport(
  repoName        : String,
  branch          : String,
  id              : String,
  timestamp       : LocalDateTime, // TODO Instant
  author          : String,
  commitId        : String,
  rulesViolated   : Map[String, Int],
  exclusions      : Map[String, Int],
  unusedExemptions: Seq[UnusedExemption]
)

object LeakDetectionReport {
  val reads: Reads[LeakDetectionReport] = {
    implicit val uer: Reads[UnusedExemption] = UnusedExemption.reads
    ( (__ \ "repoName"        ).read[String]
    ~ (__ \ "branch"          ).read[String]
    ~ (__ \ "_id"             ).read[String]
    ~ (__ \ "timestamp"       ).read[LocalDateTime]
    ~ (__ \ "author"          ).read[String]
    ~ (__ \ "commitId"        ).read[String]
    ~ (__ \ "rulesViolated"   ).read[Map[String, Int]]
    ~ (__ \ "exclusions"      ).read[Map[String, Int]]
    ~ (__ \ "unusedExemptions").read[Seq[UnusedExemption]]
    )(LeakDetectionReport.apply _)
  }
}

final case class LeakDetectionLeak(
  ruleId     : String,
  description: String,
  filePath   : String,
  scope      : String,
  lineNumber : Int,
  urlToSource: String,
  lineText   : String,
  matches    : List[Match],
  priority   : Priority,
  isExcluded : Boolean
)

object LeakDetectionLeak {
  val reads: Reads[LeakDetectionLeak] = {
    implicit val mr: Reads[Match]    = Match.reads
    implicit val pr: Reads[Priority] = Priority.reads
    ( (__ \ "ruleId"     ).read[String]
    ~ (__ \ "description").read[String]
    ~ (__ \ "filePath"   ).read[String]
    ~ (__ \ "scope"      ).read[String]
    ~ (__ \ "lineNumber" ).read[Int]
    ~ (__ \ "urlToSource").read[String]
    ~ (__ \ "lineText"   ).read[String]
    ~ (__ \ "matches"    ).read[List[Match]]
    ~ (__ \ "priority"   ).read[Priority]
    ~ (__ \ "isExcluded" ).read[Boolean]
    )(LeakDetectionLeak.apply _)
  }
}

final case class Match(
  start: Int,
  end  : Int
)

object Match {
  val reads: Reads[Match] =
    ( (__ \ "start").read[Int]
    ~ (__ \ "end"  ).read[Int]
    )(Match.apply _)
}

final case class LeakDetectionWarning(message: String)

object LeakDetectionWarning {
  val reads: Reads[LeakDetectionWarning] =
    (__ \ "message").read[String].map(LeakDetectionWarning.apply)
}
