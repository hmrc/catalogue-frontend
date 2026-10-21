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

package uk.gov.hmrc.cataloguefrontend.vulnerabilities.v2

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.*

import java.time.{Duration, Instant}
import uk.gov.hmrc.cataloguefrontend.model.ServiceName
import uk.gov.hmrc.cataloguefrontend.model.Version

case class TeamVulnerabilitySummary(
  shortestRemainingSlaDays: Option[Int],
  hasBreachedSla          : Boolean,
  byPriority              : OccurrencesByPriority,
  byState                 : OccurrencesByState,
  affectedServices        : Int
)

object TeamVulnerabilitySummary:
  implicit val format: Format[TeamVulnerabilitySummary] = Json.format[TeamVulnerabilitySummary]

case class DeploymentOccurrence(
  occurrenceId   : String,
  vulnerabilityId: String,
  component      : String,
  fixedVersions  : Seq[String],
  priority       : OccurrencePriority,
  state          : OccurrenceState,
  deadline       : Option[Instant],
  breached       : Boolean,
  nearBreach     : Boolean
):
  def daysRemaining(now: Instant = Instant.now()): Option[Long] =
    deadline.map(d => Math.floorDiv(Duration.between(now, d).getSeconds, 86400L))

object DeploymentOccurrence:
  val reads: Reads[DeploymentOccurrence] =
    given Reads[OccurrencePriority] = OccurrencePriority.reads
    given Reads[OccurrenceState]    = OccurrenceState.reads

    ( (__ \ "occurrenceId"      ).read[String]
    ~ (__ \ "vulnerabilityId"   ).read[String]
    ~ (__ \ "component"         ).read[String]
    ~ (__ \ "fixedVersions"     ).read[Seq[String]]
    ~ (__ \ "priority"          ).read[OccurrencePriority]
    ~ (__ \ "state"             ).read[OccurrenceState]
    ~ (__ \ "sla" \ "deadline"  ).readNullable[Instant]
    ~ (__ \ "sla" \ "breached"  ).read[Boolean]
    ~ (__ \ "sla" \ "nearBreach").read[Boolean]
    )(DeploymentOccurrence.apply)


case class DeploymentVulnerabilityReport(
  serviceName       : String,
  appVersion        : String,
  scanStatus        : String,
  lastSuccessfulScan: Option[Instant],
  occurrences       : Seq[DeploymentOccurrence],
  total             : Int
):
  def successful: Boolean = scanStatus == "succeeded"

  def breachedCount: Int =
    actionableOccurrences.count(_.breached)

  def nearBreachCount: Int =
    actionableOccurrences.count(o => o.nearBreach && !o.breached)

  def actionableOccurrences: Seq[DeploymentOccurrence] =
    occurrences.filter(_.state == OccurrenceState.Actionable)

  def actionableCount: Int =
    actionableOccurrences.size

  def priorityCounts: Seq[(OccurrencePriority, Int)] =
    occurrences.groupBy(_.priority).toSeq
      .map((priority, values) => priority -> values.size)
      .sortBy(_._1.ordinal)

  def stateCounts: Seq[(OccurrenceState, Int)] =
    occurrences.groupBy(_.state).toSeq
      .map((state, values) => state -> values.size)
      .sortBy(_._1.ordinal)

object DeploymentVulnerabilityReport:
  val reads: Reads[DeploymentVulnerabilityReport] =
    given Reads[DeploymentOccurrence] = DeploymentOccurrence.reads

    ( (__ \ "serviceName"       ).read[String]
    ~ (__ \ "appVersion"        ).read[String]
    ~ (__ \ "scanStatus"        ).read[String]
    ~ (__ \ "lastSuccessfulScan").readNullable[Instant]
    ~ (__ \ "results"           ).read[Seq[DeploymentOccurrence]]
    ~ (__ \ "page" \ "total"    ).read[Int]
    )(DeploymentVulnerabilityReport.apply)

enum OccurrencePriority(val value: String, val label: String, val colour: String):
  case Immediate extends OccurrencePriority("immediate", "Immediate", "danger"   )
  case Expedite  extends OccurrencePriority("expedite" , "Expedite" , "warning"  )
  case Schedule  extends OccurrencePriority("schedule" , "Schedule" , "info"     )
  case Monitor   extends OccurrencePriority("monitor"  , "Monitor"  , "secondary")

object OccurrencePriority:
  def fromValue(value: String): Option[OccurrencePriority] =
    OccurrencePriority.values.find(_.value == value)

  implicit val reads: Reads[OccurrencePriority] =
    Reads.StringReads.flatMap: value =>
      fromValue(value) match
        case Some(priority) => Reads.pure(priority)
        case None           => Reads(_ => JsError(s"Unknown occurrence priority: $value"))

case class OccurrencesByPriority(immediate: Int, expedite: Int, schedule: Int, monitor: Int)

object OccurrencesByPriority:
  implicit val format: Format[OccurrencesByPriority] = Json.format[OccurrencesByPriority]

enum OccurrenceState(val value: String, val label: String, val colour: String):
  case Actionable     extends OccurrenceState("actionable"    , "Actionable"      , "danger"   )
  case NotApplicable  extends OccurrenceState("notApplicable" , "Not applicable"  , "secondary")
  case NoFixAvailable extends OccurrenceState("noFixAvailable", "No fix available", "warning"  )
  case RiskAccepted   extends OccurrenceState("riskAccepted"  , "Risk accepted"   , "warning"  )
  case Resolved       extends OccurrenceState("resolved"      , "Resolved"        , "success"  )

object OccurrenceState:
  def fromValue(value: String): Option[OccurrenceState] =
    OccurrenceState.values.find(_.value == value)

  implicit val reads: Reads[OccurrenceState] =
    Reads.StringReads.flatMap: value =>
      fromValue(value) match
        case Some(state) => Reads.pure(state)
        case None        => Reads(_ => JsError(s"Unknown occurrence state: $value"))

case class OccurrencesByState(
  actionable    : Int,
  notApplicable : Int,
  noFixAvailable: Int,
  riskAccepted  : Int,
  resolved      : Int
)

object OccurrencesByState:
  implicit val format: Format[OccurrencesByState] = Json.format[OccurrencesByState]

case class ServiceVulnerabilitiesReport(
  serviceName: ServiceName,
  reports    : Seq[VersionsVulnerabilitiesReport]
)

object ServiceVulnerabilitiesReport:
  implicit val reads: Reads[ServiceVulnerabilitiesReport] = Json.reads[ServiceVulnerabilitiesReport]

case class VersionsVulnerabilitiesReport(
  version         : Version,
  summary         : ServiceVulnerabilitySummary,
  vulnerabilities : Seq[ServiceVulnerability]
)

object VersionsVulnerabilitiesReport:
  implicit val reads: Reads[VersionsVulnerabilitiesReport] = Json.reads[VersionsVulnerabilitiesReport]

case class ServiceVulnerabilitySummary(
  byPriority               : OccurrencesByPriority,
  byState                  : OccurrencesByState,
  hasBreachedSla           : Boolean,
  shortestRemainingSlaDays : Option[Int]
)

object ServiceVulnerabilitySummary:
  implicit val reads: Reads[ServiceVulnerabilitySummary] = Json.reads[ServiceVulnerabilitySummary]

case class ServiceVulnerability(
  id            : String,
  component     : String,
  priority      : OccurrencePriority,
  state         : OccurrenceState,
  daysRemaining : Int,
  slaBreached   : Boolean
)

object ServiceVulnerability:
  implicit val reads: Reads[ServiceVulnerability] = Json.reads[ServiceVulnerability]