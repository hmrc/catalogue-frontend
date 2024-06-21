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
import play.api.libs.json.{OFormat, Reads, __}
import uk.gov.hmrc.cataloguefrontend.connector.model.{BobbyVersionRange, Version}
import uk.gov.hmrc.cataloguefrontend.model.ServiceName

import java.time.Instant
import scala.collection.Seq

case class VulnerableComponent(
  component: String,
  version: String
) {
//  Note two edge cases which would otherwise break the dependency explorer links are handled below:
//  1. A vulnerable version may have another `.` after the patch version.
//  2. An artefact may have a trailing `_someVersionNumber`.
  def cleansedVersion: String =
    version.split("\\.").take(3).mkString(".")

  def group: String =
    component.stripPrefix("gav://").split(":")(0)

  def artefact: String =
    component.stripPrefix("gav://").split(":")(1).split("_")(0)

  def bobbyRange: BobbyVersionRange = {
    val v = Version(cleansedVersion)
    val vString = s"${v.major}.${v.minor}.${v.patch}"
    BobbyVersionRange(s"[$vString]")
  }

  def componentWithoutPrefix: Option[String] =
    component.split("://").lift(1)
}

object VulnerableComponent {
  val format: OFormat[VulnerableComponent] =
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

  val apiFormat: OFormat[DistinctVulnerability] = {
    implicit val csf = CurationStatus.format
    implicit val vcf = VulnerableComponent.format
    ( (__ \ "vulnerableComponentName"   ).format[String]
    ~ (__ \ "vulnerableComponentVersion").format[String]
    ~ (__ \ "vulnerableComponents"      ).format[Seq[VulnerableComponent]]
    ~ (__ \ "id"                        ).format[String]
    ~ (__ \ "score"                     ).formatNullable[Double]
    ~ (__ \ "description"               ).format[String]
    ~ (__ \ "fixedVersions"             ).formatNullable[Seq[String]]
    ~ (__ \ "references"                ).format[Seq[String]]
    ~ (__ \ "publishedDate"             ).format[Instant]
    ~ (__ \ "firstDetected"             ).formatNullable[Instant]
    ~ (__ \ "assessment"                ).formatNullable[String]
    ~ (__ \ "curationStatus"            ).formatNullable[CurationStatus]
    ~ (__ \ "ticket"                    ).formatNullable[String]
    )(apply, dv => Tuple.fromProductTyped(dv))
  }
}

case class VulnerabilityOccurrence(
  service            : ServiceName,
  serviceVersion     : String,
  componentPathInSlug: String,
)

object VulnerabilityOccurrence {
  val reads: OFormat[VulnerabilityOccurrence] =
    ( (__ \ "service"            ).format[String].inmap(ServiceName.apply, _.asString)
    ~ (__ \ "serviceVersion"     ).format[String]
    ~ (__ \ "componentPathInSlug").format[String]
    )(apply, vo => Tuple.fromProductTyped(vo))
}

case class VulnerabilitySummary(
   distinctVulnerability: DistinctVulnerability,
   occurrences          : Seq[VulnerabilityOccurrence],
   teams                : Seq[String]
 )

object VulnerabilitySummary {
  val apiFormat: OFormat[VulnerabilitySummary] = {
    implicit val dvf = DistinctVulnerability.apiFormat
    implicit val vof = VulnerabilityOccurrence.reads

    ( (__ \ "distinctVulnerability").format[DistinctVulnerability]
    ~ (__ \ "occurrences"          ).format[Seq[VulnerabilityOccurrence]]
    ~ (__ \ "teams"                ).format[Seq[String]]
    ) (apply, vs => Tuple.fromProductTyped(vs))
  }
}

case class TotalVulnerabilityCount(
  service             : ServiceName
, actionRequired      : Int
, noActionRequired    : Int
, investigationOngoing: Int
, uncurated           : Int
)

object TotalVulnerabilityCount {
  val reads: Reads[TotalVulnerabilityCount] =
    ( (__ \ "service"               ).read[String].map(ServiceName.apply)
    ~ (__ \ "actionRequired"        ).read[Int]
    ~ (__ \ "noActionRequired"      ).read[Int]
    ~ (__ \ "investigationOngoing"  ).read[Int]
    ~ (__ \ "uncurated"             ).read[Int]
    )(apply)
}

case class VulnerabilitiesTimelineCount(
 weekBeginning : Instant,
 count         : Int
)

object VulnerabilitiesTimelineCount {
  val reads: Reads[VulnerabilitiesTimelineCount] =
    ( (__ \ "weekBeginning").read[Instant]
    ~ (__ \ "count"        ).read[Int]
    )(VulnerabilitiesTimelineCount.apply)
}
