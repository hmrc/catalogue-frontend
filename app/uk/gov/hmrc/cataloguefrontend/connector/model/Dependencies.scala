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
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.connector.RepoType
import uk.gov.hmrc.cataloguefrontend.model.{TeamName, Version, VersionRange}
import uk.gov.hmrc.cataloguefrontend.util.{FromString, FromStringEnum}

import java.time.LocalDate

import FromStringEnum._

case class BobbyRuleViolation(
  reason: String,
  range : VersionRange,
  from  : LocalDate
)(using
  // caching the current date is not ideal, but these instances
  // are short lived (backend to page only)
  now: LocalDate = LocalDate.now()
) {
  val isActive: Boolean =
    now.isAfter(from)
}

object BobbyRuleViolation:
  val format =
    ( (__ \ "reason").read[String]
    ~ (__ \ "range" ).read[VersionRange](VersionRange.format)
    ~ (__ \ "from"  ).read[LocalDate]
    )(BobbyRuleViolation.apply)

  given Ordering[BobbyRuleViolation] =
    // ordering by rule which is most strict first
    Ordering
      .by: (v: BobbyRuleViolation) =>
        (v.range.upperBound.map(_.version  ).getOrElse(Version("9999.99.99")),
         v.range.upperBound.map(_.inclusive).getOrElse(false),
         v.from
        )
      .reverse

enum VersionState:
  case NewVersionAvailable                                  extends VersionState
  case BobbyRuleViolated(val violation: BobbyRuleViolation) extends VersionState
  case BobbyRulePending (val violation: BobbyRuleViolation) extends VersionState


case class ImportedBy(
  name          : String,
  group         : String,
  currentVersion: Version
)

object ImportedBy:
  val format =
    given Format[Version] = Version.format
    ( (__ \ "name"          ).format[String]
    ~ (__ \ "group"         ).format[String]
    ~ (__ \ "currentVersion").format[Version]
    )(ImportedBy.apply, ib => Tuple.fromProductTyped(ib))

case class Dependency(
  name               : String,
  group              : String,
  currentVersion     : Version,
  latestVersion      : Option[Version],
  bobbyRuleViolations: Seq[BobbyRuleViolation] = Seq.empty,
  importBy           : Option[ImportedBy]      = None,
  scope              : DependencyScope
):

  val isExternal =
    !group.startsWith("uk.gov.hmrc")

  lazy val (activeBobbyRuleViolations, pendingBobbyRuleViolations) =
    bobbyRuleViolations.partition(_.isActive)

  def versionState: Option[VersionState] =
    if activeBobbyRuleViolations.nonEmpty
    then Some(VersionState.BobbyRuleViolated(activeBobbyRuleViolations.sorted.head))
    else if pendingBobbyRuleViolations.nonEmpty
    then Some(VersionState.BobbyRulePending(pendingBobbyRuleViolations.sorted.head))
    else
      latestVersion.fold[Option[VersionState]](None): latestVersion =>
        if Version.isNewVersionAvailable(currentVersion, latestVersion)
        then Some(VersionState.NewVersionAvailable)
        else None

  def hasBobbyViolations: Boolean =
    versionState match
      case Some(_: VersionState.BobbyRuleViolated) => true
      case Some(_: VersionState.BobbyRulePending)  => true
      case _                                       => false


  def isOutOfDate: Boolean =
    versionState match
      case Some(VersionState.NewVersionAvailable)  => true
      case Some(_: VersionState.BobbyRuleViolated) => true
      case Some(_: VersionState.BobbyRulePending)  => true
      case _                                       => false

object Dependency:
  val reads: Reads[Dependency] =
    given Reads[Version           ] = Version.format
    given Reads[BobbyRuleViolation] = BobbyRuleViolation.format
    given Reads[ImportedBy        ] = ImportedBy.format
    given Reads[DependencyScope   ] = DependencyScope.format
    ( (__ \ "name"               ).read[String]
    ~ (__ \ "group"              ).read[String]
    ~ (__ \ "currentVersion"     ).read[Version]
    ~ (__ \ "latestVersion"      ).readNullable[Version]
    ~ (__ \ "bobbyRuleViolations").read[Seq[BobbyRuleViolation]]
    ~ (__ \ "importBy"           ).readNullable[ImportedBy]
    ~ (__ \ "scope"              ).read[DependencyScope]
    )(Dependency.apply)

case class Dependencies(
  repositoryName        : String,
  libraryDependencies   : Seq[Dependency],
  sbtPluginsDependencies: Seq[Dependency],
  otherDependencies     : Seq[Dependency]
):
  def toDependencySeq: Seq[Dependency] =
    libraryDependencies ++ sbtPluginsDependencies ++ otherDependencies

  def hasBobbyViolations: Boolean =
    toDependencySeq.exists(_.hasBobbyViolations)

  def hasOutOfDateDependencies: Boolean =
    toDependencySeq.exists(_.isOutOfDate)

object Dependencies:
  val reads: Reads[Dependencies] =
    given Reads[Dependency] = Dependency.reads
    ( (__ \ "repositoryName"        ).read[String]
    ~ (__ \ "libraryDependencies"   ).read[Seq[Dependency]]
    ~ (__ \ "sbtPluginsDependencies").read[Seq[Dependency]]
    ~ (__ \ "otherDependencies"     ).read[Seq[Dependency]]
    )(Dependencies.apply)

case class RepoWithDependency(
  repoName    : String,
  repoVersion : Version,
  teams       : List[TeamName],
  repoType    : RepoType,
  depGroup    : String,
  depArtefact : String,
  depVersion  : Version,
  scopes      : Set[DependencyScope]
)

object RepoWithDependency:
  val reads: Reads[RepoWithDependency] =
    given Reads[TeamName       ] = TeamName.format
    given Reads[Version        ] = Version.format
    given Reads[DependencyScope] = DependencyScope.format
    given Reads[RepoType       ] = RepoType.format
    ( (__ \ "repoName"   ).read[String]
    ~ (__ \ "repoVersion").read[Version]
    ~ (__ \ "teams"      ).read[List[TeamName]]
    ~ (__ \ "repoType"   ).read[RepoType]
    ~ (__ \ "depGroup"   ).read[String]
    ~ (__ \ "depArtefact").read[String]
    ~ (__ \ "depVersion" ).read[Version]
    ~ (__ \ "scopes"     ).read[Set[DependencyScope]]
    )(RepoWithDependency.apply)

case class GroupArtefacts(
  group    : String,
  artefacts: List[String]
)

object GroupArtefacts {
  val apiFormat: Format[GroupArtefacts] =
    ( (__ \ "group"    ).format[String]
    ~ (__ \ "artefacts").format[List[String]]
    )(GroupArtefacts.apply, ga => Tuple.fromProductTyped(ga))
}

enum DependencyScope(val asString: String) extends FromString derives Ordering, Writes:
  case Compile  extends DependencyScope("compile" )
  case Provided extends DependencyScope("provided")
  case Test     extends DependencyScope("test"    )
  case It       extends DependencyScope("it"      )
  case Build    extends DependencyScope("build"   )

  def displayString: String =
    asString match
      case "it"  => "Integration Test"
      case other => other.capitalize


object DependencyScope extends FromStringEnum[DependencyScope]
