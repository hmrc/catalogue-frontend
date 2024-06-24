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
import uk.gov.hmrc.cataloguefrontend.model.{TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.util.{FromString, FromStringEnum}

import java.time.LocalDate

// TODO avoid caching LocalDate, and provide to isActive function
case class BobbyRuleViolation(
  reason: String,
  range : BobbyVersionRange,
  from  : LocalDate
)(implicit now: LocalDate = LocalDate.now()) {
  def isActive: Boolean =
    now.isAfter(from)
}

object BobbyRuleViolation {
  val format = {
    implicit val bvf = BobbyVersionRange.format
    ( (__ \ "reason").read[String]
    ~ (__ \ "range" ).read[BobbyVersionRange]
    ~ (__ \ "from"  ).read[LocalDate]
    )(BobbyRuleViolation.apply)
  }

  implicit val ordering: Ordering[BobbyRuleViolation] =
    new Ordering[BobbyRuleViolation] {
      // ordering by rule which is most strict first
      def compare(x: BobbyRuleViolation, y: BobbyRuleViolation): Int = {
        implicit val vo: Ordering[Version] = Version.ordering.reverse
        implicit val bo: Ordering[Boolean] = Ordering.Boolean.reverse
        implicit val ldo: Ordering[LocalDate] =
          new Ordering[LocalDate] {
            def compare(x: LocalDate, y: LocalDate) = x.compareTo(y)
          }.reverse

        (x.range.upperBound.map(_.version), x.range.upperBound.map(_.inclusive), x.from)
          .compare(
            (y.range.upperBound.map(_.version), y.range.upperBound.map(_.inclusive), y.from)
          )
      }
    }
}

enum VersionState:
  case NewVersionAvailable                                  extends VersionState
  case BobbyRuleViolated(val violation: BobbyRuleViolation) extends VersionState
  case BobbyRulePending(val violation: BobbyRuleViolation)  extends VersionState


case class ImportedBy(
  name          : String,
  group         : String,
  currentVersion: Version
)

object ImportedBy {
  val format = {
    implicit val vf = Version.format
    ( (__ \ "name"          ).format[String]
    ~ (__ \ "group"         ).format[String]
    ~ (__ \ "currentVersion").format[Version]
    )(ImportedBy.apply, ib => Tuple.fromProductTyped(ib))
  }
}

case class Dependency(
  name               : String,
  group              : String,
  currentVersion     : Version,
  latestVersion      : Option[Version],
  bobbyRuleViolations: Seq[BobbyRuleViolation] = Seq.empty,
  importBy           : Option[ImportedBy]      = None,
  scope              : DependencyScope
) {

  val isExternal =
    !group.startsWith("uk.gov.hmrc")

  lazy val (activeBobbyRuleViolations, pendingBobbyRuleViolations) =
    bobbyRuleViolations.partition(_.isActive)

  def versionState: Option[VersionState] =
    if (activeBobbyRuleViolations.nonEmpty)
      Some(VersionState.BobbyRuleViolated(activeBobbyRuleViolations.sorted.head))
    else if (pendingBobbyRuleViolations.nonEmpty)
      Some(VersionState.BobbyRulePending(pendingBobbyRuleViolations.sorted.head))
    else
      latestVersion.fold[Option[VersionState]](None){ latestVersion =>
        if Version.isNewVersionAvailable(currentVersion, latestVersion)
          then Some(VersionState.NewVersionAvailable)
        else None
      }

  def hasBobbyViolations: Boolean =
    versionState match {
      case Some(_: VersionState.BobbyRuleViolated) => true
      case Some(_: VersionState.BobbyRulePending)  => true
      case _                                       => false
    }


  // rename reportable or something
  def isOutOfDate: Boolean =
    versionState match {
      case Some(VersionState.NewVersionAvailable)  => true
      case Some(_: VersionState.BobbyRuleViolated) => true
      case Some(_: VersionState.BobbyRulePending)  => true
      case _                                       => false
    }
}

object Dependency {
  val reads: Reads[Dependency] = {
    implicit val svf  = Version.format
    implicit val brvr = BobbyRuleViolation.format
    implicit val ibf  = ImportedBy.format
    implicit val sf   = DependencyScope.format
    ( (__ \ "name"               ).read[String]
    ~ (__ \ "group"              ).read[String]
    ~ (__ \ "currentVersion"     ).read[Version]
    ~ (__ \ "latestVersion"      ).readNullable[Version]
    ~ (__ \ "bobbyRuleViolations").read[Seq[BobbyRuleViolation]]
    ~ (__ \ "importBy"           ).readNullable[ImportedBy]
    ~ (__ \ "scope"              ).read[DependencyScope]
    )(Dependency.apply)
  }
}

case class Dependencies(
  repositoryName        : String,
  libraryDependencies   : Seq[Dependency],
  sbtPluginsDependencies: Seq[Dependency],
  otherDependencies     : Seq[Dependency]
) {
  def toDependencySeq: Seq[Dependency] =
    libraryDependencies ++ sbtPluginsDependencies ++ otherDependencies

  def hasBobbyViolations: Boolean =
    toDependencySeq.exists(_.hasBobbyViolations)

  def hasOutOfDateDependencies: Boolean =
    toDependencySeq.exists(_.isOutOfDate)
}

object Dependencies {
  val reads: Reads[Dependencies] = {
    implicit val dr = Dependency.reads
    ( (__ \ "repositoryName"        ).read[String]
    ~ (__ \ "libraryDependencies"   ).read[Seq[Dependency]]
    ~ (__ \ "sbtPluginsDependencies").read[Seq[Dependency]]
    ~ (__ \ "otherDependencies"     ).read[Seq[Dependency]]
    )(Dependencies.apply)
  }
}

case class BobbyVersion(version: Version, inclusive: Boolean)

// TODO rename as VersionRange?
/** Iso to Either[Qualifier, (Option[LowerBound], Option[UpperBound])]*/
case class BobbyVersionRange(
  lowerBound: Option[BobbyVersion],
  upperBound: Option[BobbyVersion],
  qualifier : Option[String],
  range     : String
) {

  def rangeDescr: Option[(String, String)] = {
    def comp(v: BobbyVersion) = if (v.inclusive) " <= " else " < "
    if (lowerBound.isDefined || upperBound.isDefined)
      Some((
        lowerBound.map(v => s"${v.version} ${comp(v)}").getOrElse("0.0.0 <="),
        upperBound.map(v => s"${comp(v)} ${v.version}").getOrElse("<= ∞")
      ))
    else None
  }

  override def toString: String = range
}

object BobbyVersionRange {

  private val fixed      = """^\[(\d+\.\d+.\d+)\]""".r
  private val fixedUpper = """^[\[\(],?(\d+\.\d+.\d+)[\]\)]""".r
  private val fixedLower = """^[\[\(](\d+\.\d+.\d+),[\]\)]""".r
  private val rangeRegex = """^[\[\(](\d+\.\d+.\d+),(\d+\.\d+.\d+)[\]\)]""".r
  private val qualifier  = """^\[[-\*]+(.*)\]""".r

  def parse(range: String): Option[BobbyVersionRange] = {
    val trimmedRange = range.replaceAll(" ", "")

    PartialFunction
      .condOpt(trimmedRange) {
        case fixed(v) =>
          val fixed = BobbyVersion(Version(v), inclusive = true)
            BobbyVersionRange(
              lowerBound = Some(fixed),
              upperBound = Some(fixed),
              qualifier  = None,
              range      = trimmedRange
            )
        case fixedUpper(v) =>
          val ub = BobbyVersion(Version(v), inclusive = trimmedRange.endsWith("]"))
          BobbyVersionRange(
            lowerBound = None,
            upperBound = Some(ub),
            qualifier  = None,
            range      = trimmedRange
          )
        case fixedLower(v) =>
          val lb = BobbyVersion(Version(v), inclusive = trimmedRange.startsWith("["))
          BobbyVersionRange(
            lowerBound = Some(lb),
            upperBound = None,
            qualifier  = None,
            range      = trimmedRange
          )
        case rangeRegex(v1, v2) =>
          val lb = BobbyVersion(Version(v1), inclusive = trimmedRange.startsWith("["))
          val ub = BobbyVersion(Version(v2), inclusive = trimmedRange.endsWith("]"))
          BobbyVersionRange(
            lowerBound = Some(lb),
            upperBound = Some(ub),
            qualifier  = None,
            range      = trimmedRange
          )
        case qualifier(q) if q.length > 1 =>
          BobbyVersionRange(
            lowerBound = None,
            upperBound = None,
            qualifier  = Some(q),
            range      = trimmedRange
          )
      }
  }

  def apply(range: String): BobbyVersionRange =
    parse(range).getOrElse(sys.error(s"Could not parse range $range"))

  val format: Format[BobbyVersionRange] = new Format[BobbyVersionRange] {
    override def reads(json: JsValue) =
      json match {
        case JsString(s) => parse(s).map(v => JsSuccess(v)).getOrElse(JsError("Could not parse range"))
        case _           => JsError("Not a string")
      }

    override def writes(v: BobbyVersionRange) =
      JsString(v.range)
  }
}

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

object RepoWithDependency {
  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  val reads: Reads[RepoWithDependency] = {
    implicit val tnf = TeamName.format
    implicit val vf  = Version.format
    implicit val dsf = DependencyScope.format
    implicit val rTf = RepoType.format
    ( (__ \ "repoName"   ).read[String]
    ~ (__ \ "repoVersion").read[Version]
    ~ (__ \ "teams"      ).read[List[TeamName]]
    ~ (__ \ "repoType"   ).read[RepoType]
    ~ (__ \ "depGroup"   ).read[String]
    ~ (__ \ "depArtefact").read[String]
    ~ (__ \ "depVersion" ).read[Version]
    ~ (__ \ "scopes"     ).read[Set[DependencyScope]]
    )(RepoWithDependency.apply)
  }
}

case class GroupArtefacts(
  group    : String,
  artefacts: List[String]
)

object GroupArtefacts {
  val apiFormat: OFormat[GroupArtefacts] =
    ( (__ \ "group"    ).format[String]
    ~ (__ \ "artefacts").format[List[String]]
    )(GroupArtefacts.apply, ga => Tuple.fromProductTyped(ga))
}

enum DependencyScope(val asString: String) extends FromString:
  case Compile  extends DependencyScope("compile" )
  case Provided extends DependencyScope("provided")
  case Test     extends DependencyScope("test"    )
  case It       extends DependencyScope("it"      )
  case Build    extends DependencyScope("build"   )

  def displayString: String =
    asString match {
      case "it"  => "Integration Test"
      case other => other.capitalize
    }


object DependencyScope extends FromStringEnum[DependencyScope]
