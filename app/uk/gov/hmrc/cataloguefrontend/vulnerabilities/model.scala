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
import play.api.libs.json.{Format, Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.{ServiceName, Version, VersionRange}

import java.time.Instant
import scala.collection.Seq

case class VulnerableComponent(
  component: String,
  version: String
):
//  Note two edge cases which would otherwise break the dependency explorer links are handled below:
//  1. A vulnerable version may have another `.` after the patch version.
//  2. An artefact may have a trailing `_someVersionNumber`.
  def cleansedVersion: String =
    version.split("\\.").take(3).mkString(".")

  def group: String =
    component.stripPrefix("gav://").split(":")(0)

  def artefact: String =
    component.stripPrefix("gav://").split(":")(1).split("_")(0)

  def versionRange: VersionRange =
    val v = Version(cleansedVersion)
    VersionRange(s"[${v.major}.${v.minor}.${v.patch}]")

  def componentWithoutPrefix: Option[String] =
    component.split("://").lift(1)

end VulnerableComponent

object VulnerableComponent {
  val format: Format[VulnerableComponent] =
    ( (__ \ "component").format[String]
    ~ (__ \ "version"  ).format[String]
    )(apply, vc => Tuple.fromProductTyped(vc))
}

case class DistinctVulnerability(
    vulnerableComponentName   : String,
    vulnerableComponentVersion: String,
    vulnerableComponents      : Seq[VulnerableComponent],
    id                        : String,
    score                     : Option[Double],
    description               : String,
    fixedVersions             : Option[Seq[String]],
    references                : Seq[String],
    publishedDate             : Instant,
    firstDetected             : Option[Instant],
    assessment                : Option[String],
    curationStatus            : Option[CurationStatus],
    ticket                    : Option[String]
)

object DistinctVulnerability {

  val reads: Reads[DistinctVulnerability] =
    given Format[VulnerableComponent] = VulnerableComponent.format
    ( (__ \ "vulnerableComponentName"   ).read[String]
    ~ (__ \ "vulnerableComponentVersion").read[String]
    ~ (__ \ "vulnerableComponents"      ).read[Seq[VulnerableComponent]]
    ~ (__ \ "id"                        ).read[String]
    ~ (__ \ "score"                     ).readNullable[Double]
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

case class VulnerabilityOccurrence(
  service            : ServiceName,
  serviceVersion     : String,
  componentPathInSlug: String,
)

object VulnerabilityOccurrence {
  val reads: Reads[VulnerabilityOccurrence] =
    ( (__ \ "service"            ).read[ServiceName]
    ~ (__ \ "serviceVersion"     ).read[String]
    ~ (__ \ "componentPathInSlug").read[String]
    )(apply)
}

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
  def vulnerabilitySum(): Int =
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
