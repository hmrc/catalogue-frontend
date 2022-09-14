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

package uk.gov.hmrc.cataloguefrontend.vulnerabilities

import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{OFormat, __}

import java.time.Instant
import scala.collection.Seq

case class Vulnerability(
    service                   : String,
    serviceVersion            : String,
    vulnerableComponentName   : String,
    vulnerableComponentVersion: String,
    componentPathInSlug       : String,
    id                        : String,
    score                     : Option[Double],
    description               : String,
    teams                     : Option[Seq[String]],
    references                : Seq[String],
    publishedDate             : Instant,
    scannedDate               : Instant,
    requiresAction            : Option[Boolean],
    assessment                : Option[String],
    evaluatedDate             : Option[Instant],
    ticket                    : Option[String]
)

object Vulnerability {

  val reads: OFormat[Vulnerability] = {

    ( (__ \ "service"                     ).format[String]
      ~ (__ \ "serviceVersion"            ).format[String]
      ~ (__ \ "vulnerableComponentName"   ).format[String]
      ~ (__ \ "vulnerableComponentVersion").format[String]
      ~ (__ \ "componentPathInSlug"       ).format[String]
      ~ (__ \ "id"                        ).format[String]
      ~ (__ \ "score"                     ).formatNullable[Double]
      ~ (__ \ "description"               ).format[String]
      ~ (__ \ "teams"                     ).formatNullable[Seq[String]]
      ~ (__ \ "references"                ).format[Seq[String]]
      ~ (__ \ "publishedDate"             ).format[Instant]
      ~ (__ \ "scannedDate"               ).format[Instant]
      ~ (__ \ "requiresAction"            ).formatNullable[Boolean]
      ~ (__ \ "assessment"                ).formatNullable[String]
      ~ (__ \ "evaluatedDate"             ).formatNullable[Instant]
      ~ (__ \ "ticket"                    ).formatNullable[String]
      ) (apply, unlift(unapply))
  }
}

case class DistinctVulnerability(
    vulnerableComponentName   : String,
    vulnerableComponentVersion: String,
    id                        : String,
    score                     : Option[Double],
    description               : String,
    references                : Seq[String],
    publishedDate             : Instant,
    evaluated                 : Option[Instant],
    ticket                    : Option[String]
)

object DistinctVulnerability {

  val apiFormat: OFormat[DistinctVulnerability] = {

    ( (__ \ "vulnerableComponentName"     ).format[String]
      ~ (__ \ "vulnerableComponentVersion").format[String]
      ~ (__ \ "id"                        ).format[String]
      ~ (__ \ "score"                     ).formatNullable[Double]
      ~ (__ \ "description"               ).format[String]
      ~ (__ \ "references"                ).format[Seq[String]]
      ~ (__ \ "publishedDate"             ).format[Instant]
      ~ (__ \ "evaluated"                 ).formatNullable[Instant]
      ~ (__ \ "ticket"                    ).formatNullable[String]
      )(apply, unlift(unapply))
  }
}

case class VulnerabilityOccurrence(
  service       : String,
  serviceVersion: String,
  assessment    : Option[String],
  requiresAction: Option[Boolean],
  componentPathInSlug: String
)

object VulnerabilityOccurrence {
  val reads: OFormat[VulnerabilityOccurrence] = {
    ( (__ \ "service"         ).format[String]
      ~ (__ \ "serviceVersion").format[String]
      ~ (__ \ "assessment"    ).formatNullable[String]
      ~ (__ \ "requiresAction").formatNullable[Boolean]
      ~ (__ \ "componentPathInSlug").format[String]
      )(apply, unlift(unapply))
  }
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

    ((__ \ "distinctVulnerability").format[DistinctVulnerability]
      ~ (__ \ "occurrences"       ).format[Seq[VulnerabilityOccurrence]]
      ~ (__ \ "teams"             ).format[Seq[String]]
      ) (apply, unlift(unapply))
  }
}
