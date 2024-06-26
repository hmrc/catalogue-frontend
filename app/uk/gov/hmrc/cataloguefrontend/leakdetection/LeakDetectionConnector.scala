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
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.TeamName
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class LeakDetectionConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using ec: ExecutionContext):
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  private val url: String = servicesConfig.baseUrl("leak-detection")

  def repositoriesWithLeaks()(using HeaderCarrier): Future[Seq[RepositoryWithLeaks]] =
    given Reads[RepositoryWithLeaks] = RepositoryWithLeaks.reads
    httpClientV2
      .get(url"$url/api/repository")
      .execute[Seq[RepositoryWithLeaks]]
      .recover:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty

  def leakDetectionSummaries(
    ruleId: Option[String],
    repo  : Option[String],
    team  : Option[TeamName]
  )(using
    HeaderCarrier
  ): Future[Seq[LeakDetectionSummary]] =
    given Reads[LeakDetectionSummary] = LeakDetectionSummary.reads
    httpClientV2
      .get(url"$url/api/rules/summary?ruleId=$ruleId&repository=$repo&team=${team.map(_.asString)}")
      .execute[Seq[LeakDetectionSummary]]

  def leakDetectionRepoSummaries(
    ruleId          : Option[String],
    repo            : Option[String],
    team            : Option[TeamName],
    includeNonIssues: Boolean,
    includeBranches : Boolean
  )(using
     HeaderCarrier
  ): Future[Seq[LeakDetectionRepositorySummary]] =
    given Reads[LeakDetectionRepositorySummary] = LeakDetectionRepositorySummary.reads
    val excludeNonIssues = !includeNonIssues
    httpClientV2
      .get(url"$url/api/repositories/summary?ruleId=$ruleId&repository=$repo&team=${team.map(_.asString)}&excludeNonIssues=$excludeNonIssues&includeBranches=$includeBranches")
      .execute[Seq[LeakDetectionRepositorySummary]]

  def leakDetectionDraftReports(ruleId: Option[String])(using HeaderCarrier): Future[Seq[LeakDetectionReport]] =
    given Reads[LeakDetectionReport] = LeakDetectionReport.reads
    httpClientV2
      .get(url"$url/admin/draft?rule=$ruleId")
      .execute[Seq[LeakDetectionReport]]

  def leakDetectionReport(repository: String, branch: String)(using HeaderCarrier): Future[LeakDetectionReport] =
    given Reads[LeakDetectionReport] = LeakDetectionReport.reads
    httpClientV2
      .get(url"$url/api/$repository/$branch/report")
      .execute[LeakDetectionReport]

  def leakDetectionLeaks(reportId: String)(using HeaderCarrier): Future[Seq[LeakDetectionLeak]] =
    given Reads[LeakDetectionLeak] = LeakDetectionLeak.reads
    httpClientV2
      .get(url"$url/api/report/$reportId/leaks")
      .execute[Seq[LeakDetectionLeak]]

  def leakDetectionWarnings(reportId: String)(using HeaderCarrier): Future[Seq[LeakDetectionWarning]] =
    given Reads[LeakDetectionWarning] = LeakDetectionWarning.reads
    httpClientV2
      .get(url"$url/api/report/$reportId/warnings")
      .execute[Seq[LeakDetectionWarning]]

  def leakDetectionRules()(using HeaderCarrier): Future[Seq[LeakDetectionRule]] =
    given Reads[LeakDetectionRule] = LeakDetectionRule.reads
    httpClientV2
      .get(url"$url/api/rules")
      .execute[Seq[LeakDetectionRule]]

  def rescan(repository: String, branch: String)(using HeaderCarrier): Future[LeakDetectionReport] =
    given Reads[LeakDetectionReport] = LeakDetectionReport.reads
    httpClientV2
      .post(url"$url/admin/rescan/$repository/$branch?mode=normal")
      .execute[LeakDetectionReport]

end LeakDetectionConnector

case class RepositoryWithLeaks(name: String) extends AnyVal

object RepositoryWithLeaks {
  val reads: Reads[RepositoryWithLeaks] =
    summon[Reads[String]].map(RepositoryWithLeaks.apply)
}

enum Priority(val name: String):
  case High   extends Priority("high")
  case Medium extends Priority("medium")
  case Low    extends Priority("low")

object Priority:
  given Ordering[Priority] =
    Ordering.by(_.ordinal)

  val reads: Reads[Priority] =
    Reads.StringReads.map:
      case "high"   => High
      case "medium" => Medium
      case "low"    => Low
      case p        => throw RuntimeException(s"Priority type '$p' unknown")

case class LeakDetectionSummary(
  rule : LeakDetectionRule,
  leaks: Seq[LeakDetectionRepositorySummary]
)

object LeakDetectionSummary:
  val reads: Reads[LeakDetectionSummary] =
    given Reads[LeakDetectionRule]              = LeakDetectionRule.reads
    given Reads[LeakDetectionRepositorySummary] = LeakDetectionRepositorySummary.reads
    ( (__ \ "rule" ).read[LeakDetectionRule]
    ~ (__ \ "leaks").read[Seq[LeakDetectionRepositorySummary]]
    )(LeakDetectionSummary.apply)

case class LeakDetectionRule(
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

object LeakDetectionRule:
  val reads: Reads[LeakDetectionRule] =
    ( (__ \ "id"               ).read[String]
    ~ (__ \ "scope"            ).read[String]
    ~ (__ \ "regex"            ).read[String]
    ~ (__ \ "description"      ).read[String]
    ~ (__ \ "ignoredFiles"     ).read[List[String]]
    ~ (__ \ "ignoredExtensions").read[List[String]]
    ~ (__ \ "ignoredContent"   ).read[List[String]]
    ~ (__ \ "priority"         ).read[Priority](Priority.reads)
    ~ (__ \ "draft"            ).read[Boolean]
    )(LeakDetectionRule.apply)

case class LeakDetectionRepositorySummary(
  repository     : String,
  isArchived     : Boolean,
  firstScannedAt : Instant,
  lastScannedAt  : Instant,
  warningCount   : Int,
  excludedCount  : Int,
  unresolvedCount: Int,
  branchSummary  : Option[Seq[LeakDetectionBranchSummary]]
):
  def totalCount: Int =
    warningCount + excludedCount + unresolvedCount

object LeakDetectionRepositorySummary:
  val reads: Reads[LeakDetectionRepositorySummary] =
    given Reads[LeakDetectionBranchSummary]= LeakDetectionBranchSummary.reads
    ( (__ \ "repository"     ).read[String]
    ~ (__ \ "isArchived"     ).read[Boolean]
    ~ (__ \ "firstScannedAt" ).read[Instant]
    ~ (__ \ "lastScannedAt"  ).read[Instant]
    ~ (__ \ "warningCount"   ).read[Int]
    ~ (__ \ "excludedCount"  ).read[Int]
    ~ (__ \ "unresolvedCount").read[Int]
    ~ (__ \ "branchSummary"  ).readNullable[Seq[LeakDetectionBranchSummary]]
    )(LeakDetectionRepositorySummary.apply)

case class LeakDetectionBranchSummary(
  branch         : String,
  reportId       : String,
  scannedAt      : Instant,
  warningCount   : Int,
  excludedCount  : Int,
  unresolvedCount: Int
):
  def totalCount: Int =
    warningCount + excludedCount + unresolvedCount

object LeakDetectionBranchSummary:
  val reads: Reads[LeakDetectionBranchSummary] =
    ( (__ \ "branch"         ).read[String]
    ~ (__ \ "reportId"       ).read[String]
    ~ (__ \ "scannedAt"      ).read[Instant]
    ~ (__ \ "warningCount"   ).read[Int]
    ~ (__ \ "excludedCount"  ).read[Int]
    ~ (__ \ "unresolvedCount").read[Int]
    )(LeakDetectionBranchSummary.apply)

case class UnusedExemption(
  ruleId  : String,
  filePath: String,
  text    : Option[String]
)

object UnusedExemption {
  val reads: Reads[UnusedExemption] =
    ( (__ \ "ruleId"  ).read[String]
    ~ (__ \ "filePath").read[String]
    ~ (__ \ "text"    ).readNullable[String]
    )(UnusedExemption.apply)
}

case class LeakDetectionReport(
  repoName        : String,
  branch          : String,
  id              : String,
  timestamp       : Instant,
  author          : String,
  commitId        : String,
  rulesViolated   : Map[String, Int],
  exclusions      : Map[String, Int],
  unusedExemptions: Seq[UnusedExemption]
)

object LeakDetectionReport:
  val reads: Reads[LeakDetectionReport] =
    given Reads[UnusedExemption] = UnusedExemption.reads
    ( (__ \ "repoName"        ).read[String]
    ~ (__ \ "branch"          ).read[String]
    ~ (__ \ "_id"             ).read[String]
    ~ (__ \ "timestamp"       ).read[Instant]
    ~ (__ \ "author"          ).read[String]
    ~ (__ \ "commitId"        ).read[String]
    ~ (__ \ "rulesViolated"   ).read[Map[String, Int]]
    ~ (__ \ "exclusions"      ).read[Map[String, Int]]
    ~ (__ \ "unusedExemptions").read[Seq[UnusedExemption]]
    )(LeakDetectionReport.apply)

case class LeakDetectionLeak(
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

object LeakDetectionLeak:
  val reads: Reads[LeakDetectionLeak] =
    given Reads[Match]    = Match.reads
    given Reads[Priority] = Priority.reads
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
    )(LeakDetectionLeak.apply)

case class Match(
  start: Int,
  end  : Int
)

object Match:
  val reads: Reads[Match] =
    ( (__ \ "start").read[Int]
    ~ (__ \ "end"  ).read[Int]
    )(Match.apply)

case class LeakDetectionWarning(message: String)

object LeakDetectionWarning:
  val reads: Reads[LeakDetectionWarning] =
    (__ \ "message").read[String].map(LeakDetectionWarning.apply)
