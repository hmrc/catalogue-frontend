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

package uk.gov.hmrc.cataloguefrontend.connector.model

import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.{VersionRange, Version}
import uk.gov.hmrc.cataloguefrontend.connector.RepoType

import java.time.{Instant, LocalDate}

case class BobbyReport(
  repoName    : String
, repoVersion : Version
, repoType    : RepoType
, violations  : Seq[BobbyReport.Violation]
, lastUpdated : Instant
)

object BobbyReport:
  val reads: Reads[BobbyReport] =
    given Reads[Violation] = Violation.reads
    ( (__ \ "repoName"   ).read[String]
    ~ (__ \ "repoVersion").read[Version](Version.format)
    ~ (__ \ "repoType"   ).read[RepoType]
    ~ (__ \ "violations" ).read[Seq[Violation]]
    ~ (__ \ "lastUpdated").read[Instant]
    )(BobbyReport.apply _)

  case class Violation(
    depGroup   : String
  , depArtefact: String
  , depVersion : Version
  , depScopes  : Set[DependencyScope]
  , range      : VersionRange
  , reason     : String
  , from       : LocalDate
  , exempt     : Boolean
  )

  object Violation:
    val reads: Reads[Violation] =
      given Reads[VersionRange] = VersionRange.format
      ( (__ \ "depGroup"   ).read[String]
      ~ (__ \ "depArtefact").read[String]
      ~ (__ \ "depVersion" ).read[Version](Version.format)
      ~ (__ \ "depScopes"  ).read[Set[DependencyScope]]
      ~ (__ \ "range"      ).read[VersionRange]
      ~ (__ \ "reason"     ).read[String]
      ~ (__ \ "from"       ).read[LocalDate]
      ~ (__ \ "exempt"     ).read[Boolean]
      )(Violation.apply _)
