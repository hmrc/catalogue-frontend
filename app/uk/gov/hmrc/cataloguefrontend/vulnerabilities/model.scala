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

package uk.gov.hmrc.cataloguefrontend.vulnerabilities

import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.{ServiceName, Version, VersionRange}

import java.time.Instant
import scala.collection.Seq

case class VulnerableComponent(
  component: String,
  version  : Version
):
  import VulnerableComponent._

  val gav: Option[(String, String)] =
    component match
      case gavRegex(grp, aft, sv) => Some((grp, aft))
      case _                      => None

  def versionRange: VersionRange =
    VersionRange(s"[${version.major}.${version.minor}.${version.patch}]")

  def componentWithoutPrefix: String =
    component.split("://").lift(1).getOrElse(component)

end VulnerableComponent

object VulnerableComponent:
  private val gavRegex = raw"gav:\/\/(.*):(.*?)(?:_(\d+(?:\.\d+))?)?".r // format is  gav://group:artefact with optional scala version

  val reads: Reads[VulnerableComponent] =
    given Reads[Version] = Version.format
    ( (__ \ "component").read[String]
    ~ (__ \ "version"  ).read[Version]
    )(apply)

case class DistinctVulnerability(
  vulnerableComponentName   : String,
  vulnerableComponentVersion: String,
  vulnerableComponents      : Seq[VulnerableComponent],
  id                        : String,
  score                     : Option[Double],
  summary                   : String,
  description               : String,
  fixedVersions             : Option[Seq[String]],
  references                : Seq[String],
  publishedDate             : Instant,
  firstDetected             : Option[Instant],
  assessment                : Option[String],
  curationStatus            : Option[CurationStatus],
  ticket                    : Option[String]
):
  import DistinctVulnerability._

  val (dependencyType, component) =
    vulnerableComponentName match
      case componentRegex(tpe, cmpt) => (Some(tpe), cmpt)
      case _                         => (None     , vulnerableComponentName)


object DistinctVulnerability {

  private val componentRegex = raw"(.*):\/\/(.*)".r

  val reads: Reads[DistinctVulnerability] =
    given Reads[VulnerableComponent] = VulnerableComponent.reads
    ( (__ \ "vulnerableComponentName"   ).read[String]
    ~ (__ \ "vulnerableComponentVersion").read[String]
    ~ (__ \ "vulnerableComponents"      ).read[Seq[VulnerableComponent]]
    ~ (__ \ "id"                        ).read[String]
    ~ (__ \ "score"                     ).readNullable[Double]
    ~ (__ \ "summary"                   ).read[String]
    ~ (__ \ "description"               ).read[String]
    ~ (__ \ "fixedVersions"             ).readNullable[Seq[String]]
    ~ (__ \ "references"                ).read[Seq[String]]
    ~ (__ \ "publishedDate"             ).read[Instant]
    ~ (__ \ "firstDetected"             ).readNullable[Instant]
    ~ (__ \ "assessment"                ).readNullable[String]
    ~ (__ \ "curationStatus"            ).readNullable[CurationStatus]
    ~ (__ \ "ticket"                    ).readNullable[String]
    )(apply)
}

case class ImportedBy(
  group   : String,
  artefact: String,
  version : Version
)

object ImportedBy:
  val reads: Reads[ImportedBy] =
    ( (__ \ "group"   ).read[String]
    ~ (__ \ "artefact").read[String]
    ~ (__ \ "version" ).read[Version](Version.format)
    )(ImportedBy.apply)

case class VulnerabilityOccurrence(
  service            : ServiceName,
  serviceVersion     : String,
  componentPathInSlug: String,
  importedBy         : Option[ImportedBy]
)

object VulnerabilityOccurrence:
  val reads: Reads[VulnerabilityOccurrence] =
    ( (__ \ "service"            ).read[ServiceName]
    ~ (__ \ "serviceVersion"     ).read[String]
    ~ (__ \ "componentPathInSlug").read[String]
    ~ (__ \ "importedBy"         ).readNullable[ImportedBy](ImportedBy.reads)
    )(apply)

case class VulnerabilitySummary(
   distinctVulnerability: DistinctVulnerability,
   occurrences          : Seq[VulnerabilityOccurrence],
   teams                : Seq[String]
 )

object VulnerabilitySummary:
  val reads: Reads[VulnerabilitySummary] =
    given Reads[DistinctVulnerability]   = DistinctVulnerability.reads
    given Reads[VulnerabilityOccurrence] = VulnerabilityOccurrence.reads

    ( (__ \ "distinctVulnerability").read[DistinctVulnerability]
    ~ (__ \ "occurrences"          ).read[Seq[VulnerabilityOccurrence]]
    ~ (__ \ "teams"                ).read[Seq[String]]
    )(apply)

case class TotalVulnerabilityCount(
  service             : ServiceName
, actionRequired      : Int
, noActionRequired    : Int
, investigationOngoing: Int
, uncurated           : Int
):
  def vulnerabilitySum: Int =
    actionRequired +
    noActionRequired +
    investigationOngoing +
    uncurated

object TotalVulnerabilityCount:
  val reads: Reads[TotalVulnerabilityCount] =
    ( (__ \ "service"             ).read[ServiceName]
    ~ (__ \ "actionRequired"      ).read[Int]
    ~ (__ \ "noActionRequired"    ).read[Int]
    ~ (__ \ "investigationOngoing").read[Int]
    ~ (__ \ "uncurated"           ).read[Int]
    )(apply)

case class VulnerabilitiesTimelineCount(
  weekBeginning: Instant,
  count        : Int
)

object VulnerabilitiesTimelineCount:
  val reads: Reads[VulnerabilitiesTimelineCount] =
    ( (__ \ "weekBeginning").read[Instant]
    ~ (__ \ "count"        ).read[Int]
    )(VulnerabilitiesTimelineCount.apply)
