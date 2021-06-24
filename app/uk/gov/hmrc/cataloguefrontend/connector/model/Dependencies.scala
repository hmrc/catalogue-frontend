/*
 * Copyright 2021 HM Revenue & Customs
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

import java.time.{Instant, LocalDate}

import com.github.ghik.silencer.silent
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.http.controllers.RestFormats

sealed trait VersionState
object VersionState {
  case object UpToDate extends VersionState
  case object MinorVersionOutOfDate extends VersionState
  case object MajorVersionOutOfDate extends VersionState
  case object Invalid extends VersionState
  case class BobbyRuleViolated(violation: BobbyRuleViolation) extends VersionState
  case class BobbyRulePending(violation: BobbyRuleViolation) extends VersionState
}

// TODO avoid caching LocalDate, and provide to isActive function
case class BobbyRuleViolation(
  reason: String,
  range: BobbyVersionRange,
  from: LocalDate
)(implicit now: LocalDate = LocalDate.now()) {
  def isActive: Boolean = now.isAfter(from)
}

object BobbyRuleViolation {
  val format = {
    implicit val bvf = BobbyVersionRange.format
    ((__ \ "reason").read[String]
      ~ (__ \ "range").read[BobbyVersionRange]
      ~ (__ \ "from").read[LocalDate])(BobbyRuleViolation.apply _)
  }

  implicit val ordering = new Ordering[BobbyRuleViolation] {
    // ordering by rule which is most strict first
    def compare(x: BobbyRuleViolation, y: BobbyRuleViolation): Int = {
      implicit val vo: Ordering[Version] = Version.ordering.reverse
      implicit val bo: Ordering[Boolean] = Ordering.Boolean.reverse
      implicit val ldo: Ordering[LocalDate] = new Ordering[LocalDate] {
        def compare(x: LocalDate, y: LocalDate) = x.compareTo(y)
      }.reverse
      (x.range.upperBound.map(_.version), x.range.upperBound.map(_.inclusive), x.from)
        .compare(
          (y.range.upperBound.map(_.version), y.range.upperBound.map(_.inclusive), y.from)
        )
    }
  }
}

case class Dependency(
  name: String,
  group: String,
  currentVersion: Version,
  latestVersion: Option[Version],
  bobbyRuleViolations: Seq[BobbyRuleViolation] = Seq.empty
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
      latestVersion.map(latestVersion => Version.getVersionState(currentVersion, latestVersion))

  def isOutOfDate: Boolean =
    !versionState.contains(VersionState.UpToDate)
}

case class Dependencies(
  repositoryName: String,
  libraryDependencies: Seq[Dependency],
  sbtPluginsDependencies: Seq[Dependency],
  otherDependencies: Seq[Dependency],
  lastUpdated: Instant
) {

  def toSeq: Seq[Dependency] =
    libraryDependencies ++ sbtPluginsDependencies ++ otherDependencies

  def hasOutOfDateDependencies: Boolean =
    toSeq.exists(_.isOutOfDate)
}

object Dependencies {
  object Implicits {
    @silent("never used") private implicit val dtr = RestFormats.dateTimeFormats
    private implicit val vf                        = Version.format
    private implicit val brvr                      = BobbyRuleViolation.format

    implicit val readsDependency: Reads[Dependency] = Json.reads[Dependency]
    implicit val reads: Reads[Dependencies]         = Json.reads[Dependencies]
  }
}

case class BobbyVersion(version: Version, inclusive: Boolean)

// TODO rename as VersionRange?
/** Iso to Either[Qualifier, (Option[LowerBound], Option[UpperBound])]*/
case class BobbyVersionRange(
  lowerBound: Option[BobbyVersion],
  upperBound: Option[BobbyVersion],
  qualifier: Option[String],
  range: String
) {

  def rangeDescr: Option[(String, String)] = {
    def comp(v: BobbyVersion) = if (v.inclusive) " <= " else " < "
    if (lowerBound.isDefined || upperBound.isDefined)
      Some((lowerBound.map(v => s"${v.version} ${comp(v)}").getOrElse("0.0.0 <="), upperBound.map(v => s"${comp(v)} ${v.version}").getOrElse("<= ∞")))
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
          Version
            .parse(v)
            .map(BobbyVersion(_, inclusive = true))
            .map { fixed =>
              BobbyVersionRange(
                lowerBound = Some(fixed),
                upperBound = Some(fixed),
                qualifier = None,
                range = trimmedRange
              )
            }
        case fixedUpper(v) =>
          Version
            .parse(v)
            .map(BobbyVersion(_, inclusive = trimmedRange.endsWith("]")))
            .map { ub =>
              BobbyVersionRange(
                lowerBound = None,
                upperBound = Some(ub),
                qualifier = None,
                range = trimmedRange
              )
            }
        case fixedLower(v) =>
          Version
            .parse(v)
            .map(BobbyVersion(_, inclusive = trimmedRange.startsWith("[")))
            .map { lb =>
              BobbyVersionRange(
                lowerBound = Some(lb),
                upperBound = None,
                qualifier = None,
                range = trimmedRange
              )
            }
        case rangeRegex(v1, v2) =>
          for {
            lb <- Version.parse(v1).map(BobbyVersion(_, inclusive = trimmedRange.startsWith("[")))
            ub <- Version.parse(v2).map(BobbyVersion(_, inclusive = trimmedRange.endsWith("]")))
          } yield BobbyVersionRange(
            lowerBound = Some(lb),
            upperBound = Some(ub),
            qualifier = None,
            range = trimmedRange
          )
        case qualifier(q) if q.length() > 1 =>
          Some(
            BobbyVersionRange(
              lowerBound = None,
              upperBound = None,
              qualifier = Some(q),
              range = trimmedRange
            )
          )
      }
      .flatten
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

case class Version(major: Int, minor: Int, patch: Int, original: String) extends Ordered[Version] {

  //!@TODO test
  def diff(other: Version): (Int, Int, Int) =
    (this.major - other.major, this.minor - other.minor, this.patch - other.patch)

  override def compare(other: Version): Int = {
    import Ordered._
    (major, minor, patch).compare((other.major, other.minor, other.patch))
  }

  override def toString: String = original
}

object Version {

  implicit val ordering = new Ordering[Version] {
    def compare(x: Version, y: Version): Int =
      x.compare(y)
  }

  def getVersionState(currentVersion: Version, latestVersion: Version): VersionState =
    latestVersion.diff(currentVersion) match {
      case (0, 0, 0) => VersionState.UpToDate
      case (0, minor, patch)
          if minor > 0 ||
            minor == 0 && patch > 0 =>
        VersionState.MinorVersionOutOfDate
      case (major, _, _) if major >= 1 => VersionState.MajorVersionOutOfDate
      case _                           => VersionState.Invalid // this is really `Ahead`..
    }

  def parse(s: String): Option[Version] = {
    val regex3 = """(\d+)\.(\d+)\.(\d+)(.*)""".r
    val regex2 = """(\d+)\.(\d+)(.*)""".r
    val regex1 = """(\d+)(.*)""".r
    s match {
      case regex3(maj, min, patch, _) => Some(Version(Integer.parseInt(maj), Integer.parseInt(min), Integer.parseInt(patch), s))
      case regex2(maj, min, _)        => Some(Version(Integer.parseInt(maj), Integer.parseInt(min), 0, s))
      case regex1(patch, _)           => Some(Version(0, 0, Integer.parseInt(patch), s))
      case _                          => None
    }
  }

  def apply(version: String): Version =
    parse(version).getOrElse(sys.error(s"Could not parse version $version"))

  val format: Format[Version] = new Format[Version] {
    override def reads(json: JsValue) = {
      def parseStr(s: String) = Version.parse(s).map(v => JsSuccess(v)).getOrElse(JsError("Could not parse version"))
      json match {
        case JsString(s) => parseStr(s)
        case JsObject(m) =>
          m.get("original") match {
            case Some(JsString(s)) => parseStr(s)
            case _                 => JsError("Not a string")
          }
        case _ => JsError("Not a string")
      }
    }

    override def writes(v: Version) =
      JsString(v.original)
  }
}

case class ServiceWithDependency(
  slugName: String,
  slugVersion: String,
  teams: List[TeamName],
  depGroup: String,
  depArtefact: String,
  depVersion: String,
  depSemanticVersion: Option[Version]
)

object ServiceWithDependency {
  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  val reads: Reads[ServiceWithDependency] = {
    implicit val tnf = TeamName.format
    ((__ \ "slugName").read[String]
      ~ (__ \ "slugVersion").read[String]
      ~ (__ \ "teams").read[List[TeamName]]
      ~ (__ \ "depGroup").read[String]
      ~ (__ \ "depArtefact").read[String]
      ~ (__ \ "depVersion").read[String]
      ~ (__ \ "depVersion").read[String].map(Version.parse))(ServiceWithDependency.apply _)
  }
}

case class GroupArtefacts(group: String, artefacts: List[String])

object GroupArtefacts {
  val apiFormat: OFormat[GroupArtefacts] =
    ((__ \ "group").format[String]
      ~ (__ \ "artefacts").format[List[String]])(GroupArtefacts.apply, unlift(GroupArtefacts.unapply))
}

sealed trait DependencyScope {
  def asString: String
  def displayString = asString.capitalize
}
object DependencyScope {
  case object Compile extends DependencyScope { override val asString = "compile" }
  case object Test extends DependencyScope { override val asString = "test" }
  case object Build extends DependencyScope { override val asString = "build" }

  val values: List[DependencyScope] =
    List(Compile, Test, Build)

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
}
