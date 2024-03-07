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

import java.time.LocalDate
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.data.format.Formatter
import play.api.data.FormError
import uk.gov.hmrc.cataloguefrontend.connector.RepoType

sealed trait VersionState
object VersionState {
  case object NewVersionAvailable                             extends VersionState
  case object Invalid                                         extends VersionState
  case class BobbyRuleViolated(violation: BobbyRuleViolation) extends VersionState
  case class BobbyRulePending(violation: BobbyRuleViolation)  extends VersionState
}

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
    )(BobbyRuleViolation.apply _)
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
    )(ImportedBy.apply, unlift(ImportedBy.unapply))
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
        Version.isNewVersionAvailable(currentVersion, latestVersion) match {
          case Some(true)  => Some(VersionState.NewVersionAvailable)
          case Some(false) => None
          case None        => Some(VersionState.Invalid)
        }
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
    )(Dependency.apply _)
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
    )(Dependencies.apply _)
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

case class Version(
  major   : Int,
  minor   : Int,
  patch   : Int,
  original: String
) extends Ordered[Version] {

  //!@TODO test
  def diff(other: Version): (Int, Int, Int) =
    (this.major - other.major, this.minor - other.minor, this.patch - other.patch)

  override def compare(other: Version): Int = {
    import Ordered._
    (major, minor, patch, original).compare((other.major, other.minor, other.patch, other.original))
  }

  override def toString: String = original
}

object Version {

  implicit val ordering: Ordering[Version] = new Ordering[Version] {
    def compare(x: Version, y: Version): Int =
      x.compare(y)
  }

  def isNewVersionAvailable(currentVersion: Version, latestVersion: Version): Option[Boolean] =
    latestVersion.diff(currentVersion) match {
      case (major, minor, patch)
          if (major >  0                           ) ||
             (major == 0 && minor >  0             ) ||
             (major == 0 && minor == 0 && patch > 0)
        => Some(true)
      case (major, minor, patch)
        => Some(false)
      case _
        => None
    }

  def apply(s: String): Version = {
    val regex3 = """(\d+)\.(\d+)\.(\d+)(.*)""".r
    val regex2 = """(\d+)\.(\d+)(.*)""".r
    val regex1 = """(\d+)(.*)""".r
    s match {
      case regex3(maj, min, patch, _) => Version(Integer.parseInt(maj), Integer.parseInt(min), Integer.parseInt(patch), s)
      case regex2(maj, min, _)        => Version(Integer.parseInt(maj), Integer.parseInt(min), 0                      , s)
      case regex1(patch, _)           => Version(0                    , 0                    , Integer.parseInt(patch), s)
      case _                          => Version(0                    , 0                    , 0                      , s)
    }
  }

  val format: Format[Version] = new Format[Version] {
    override def reads(json: JsValue) =
      json match {
        case JsString(s) => JsSuccess(Version(s))
        case JsObject(m) =>
          m.get("original") match {
            case Some(JsString(s)) => JsSuccess(Version(s))
            case _                 => JsError("Not a string")
          }
        case _ => JsError("Not a string")
      }

    override def writes(v: Version) =
      JsString(v.original)
  }

  val formFormat: Formatter[Version] = new Formatter[Version] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Version] =
      data
        .get(key)
        .flatMap(str => scala.util.Try(apply(str)).toOption )
        .fold[Either[Seq[FormError], Version]](Left(Seq(FormError(key, "Invalid value"))))(Right.apply)

    override def unbind(key: String, value: Version): Map[String, String] =
      Map(key -> value.original)
  }
}

case class ServiceWithDependency(
  repoName    : String,
  repoVersion : Version,
  teams       : List[TeamName],
  repoType    : RepoType,
  depGroup    : String,
  depArtefact : String,
  depVersion  : Version,
  scopes      : Set[DependencyScope]
)

object ServiceWithDependency {
  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  val reads: Reads[ServiceWithDependency] = {
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
    )(ServiceWithDependency.apply _)
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
    )(GroupArtefacts.apply, unlift(GroupArtefacts.unapply))
}

sealed trait DependencyScope {
  def asString: String
  def displayString = asString match {
    case "it"  => "Integration Test"
    case other => other.capitalize
  }
}
object DependencyScope {
  case object Compile  extends DependencyScope { override val asString = "compile"  }
  case object Provided extends DependencyScope { override val asString = "provided" }
  case object Test     extends DependencyScope { override val asString = "test"     }
  case object It       extends DependencyScope { override val asString = "it"       }
  case object Build    extends DependencyScope { override val asString = "build"    }

  val values: List[DependencyScope] =
    List(Compile, Provided, Test, It, Build)

  def parse(s: String): Either[String, DependencyScope] =
    values
      .find(_.asString == s)
      .toRight(s"Invalid dependency scope - should be one of: ${values.map(_.asString).mkString(", ")}")

  private def toResult[A](e: Either[String, A]): JsResult[A] =
    e match {
      case Right(r) => JsSuccess(r)
      case Left(l)  => JsError(__, l)
    }

  lazy val format: Format[DependencyScope] =
    Format(
      _.validate[String].flatMap(s => toResult(DependencyScope.parse(s))),
      f => JsString(f.asString)
    )

  implicit val dependencyScopeOrdering: Ordering[DependencyScope] = new Ordering[DependencyScope] {
    def compare(x: DependencyScope, y: DependencyScope): Int =
      values.indexOf(x) - values.indexOf(y)
  }
}
