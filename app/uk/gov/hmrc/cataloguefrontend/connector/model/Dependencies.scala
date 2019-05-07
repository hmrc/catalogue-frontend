/*
 * Copyright 2019 HM Revenue & Customs
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

import org.joda.time.DateTime
import play.api.libs.json.{Format, JsError, JsObject, JsPath, JsString, JsSuccess, JsValue, Json, OFormat, Reads, __}
import play.api.libs.functional.syntax._
import uk.gov.hmrc.http.controllers.RestFormats

sealed trait VersionState
object VersionState {
  case object UpToDate              extends VersionState
  case object MinorVersionOutOfDate extends VersionState
  case object MajorVersionOutOfDate extends VersionState
  case object Invalid               extends VersionState
  case object BobbyRuleViolated     extends VersionState
  case object BobbyRulePending      extends VersionState
}

case class Dependency(
  name          : String,
  currentVersion: Version,
  latestVersion : Option[Version],
  bobbyRuleViolations: Seq[BobbyRuleViolation] = Seq.empty,
  isExternal    : Boolean = false) {

  lazy val (activeBobbyRuleViolations, pendingBobbyRuleViolations) =
    bobbyRuleViolations.partition(_.isActive)

  def versionState: Option[VersionState] =
    if (activeBobbyRuleViolations.nonEmpty)
      Some(VersionState.BobbyRuleViolated)
    else if (pendingBobbyRuleViolations.nonEmpty)
      Some(VersionState.BobbyRulePending)
    else
      latestVersion.map(latestVersion => Version.getVersionState(currentVersion, latestVersion))

  def isOutOfDate: Boolean =
    !versionState.contains(VersionState.UpToDate)
}

case class Dependencies(
  repositoryName        : String,
  libraryDependencies   : Seq[Dependency],
  sbtPluginsDependencies: Seq[Dependency],
  otherDependencies     : Seq[Dependency],
  lastUpdated           : DateTime) {

  def toSeq: Seq[Dependency] =
    libraryDependencies ++ sbtPluginsDependencies ++ otherDependencies

  def hasOutOfDateDependencies: Boolean =
    toSeq.exists(_.isOutOfDate)
}

case class BobbyVersion(version: Version, inclusive: Boolean)

case class BobbyVersionRange(
    lowerBound: Option[BobbyVersion]
  , upperBound: Option[BobbyVersion]
  , qualifier : Option[String]
  , range     : String
  ) {

  def rangeDescr: Option[(String, String)] = {
    def comp(v: BobbyVersion) = if (v.inclusive) " <= " else " < "
    if (lowerBound.isDefined || upperBound.isDefined) {
       Some(( lowerBound.map(v => s"${v.version} ${comp(v)}").getOrElse("")
            , upperBound.map(v => s"${comp(v)} ${v.version}").getOrElse("")
           ))
    } else None
  }
}

object BobbyVersionRange {

  private val fixed      = """^\[(\d+\.\d+.\d+)\]""".r
  private val fixedUpper = """^\(,?(\d+\.\d+.\d+)[\]\)]""".r
  private val fixedLower = """^[\[\(](\d+\.\d+.\d+),[\]\)]""".r
  private val rangeRegex = """^[\[\(](\d+\.\d+.\d+),(\d+\.\d+.\d+)[\]\)]""".r
  private val qualifier  = """^\[[-\*]+(.*)\]""".r

  def apply(range: String): BobbyVersionRange = {
    val trimmedRange = range.replaceAll(" ", "")

    trimmedRange match {
      case fixed(v) =>
        val fixed = Version.parse(v).map(BobbyVersion(_, inclusive = true))
        BobbyVersionRange(lowerBound = fixed, upperBound = fixed, qualifier = None, range = trimmedRange)
      case fixedUpper(v) =>
        BobbyVersionRange(
          lowerBound = None,
          upperBound = Version.parse(v).map(BobbyVersion(_, inclusive = trimmedRange.endsWith("]"))),
          qualifier  = None,
          range      = trimmedRange)
      case fixedLower(v) =>
        BobbyVersionRange(
          lowerBound = Version.parse(v).map(BobbyVersion(_, inclusive = trimmedRange.startsWith("["))),
          upperBound = None,
          qualifier  = None,
          range      = trimmedRange)
      case rangeRegex(v1, v2) =>
        BobbyVersionRange(
          lowerBound = Version.parse(v1).map(BobbyVersion(_, inclusive = trimmedRange.startsWith("["))),
          upperBound = Version.parse(v2).map(BobbyVersion(_, inclusive = trimmedRange.endsWith("]"))),
          qualifier  = None,
          range      = trimmedRange
        )
      case qualifier(q) if q.length() > 1 =>
        BobbyVersionRange(lowerBound = None, upperBound = None, qualifier = Some(q), range = trimmedRange)
      case _ => BobbyVersionRange(lowerBound = None, upperBound = None, qualifier = None, range = trimmedRange)
    }
  }

  val reads: Reads[BobbyVersionRange] =
    JsPath.read[String].map(BobbyVersionRange.apply)

}

// TODO avoid caching LocalDate, and provide to isActive function
case class BobbyRuleViolation(reason: String, range: BobbyVersionRange, from: LocalDate)(implicit now: LocalDate = LocalDate.now()) {
  def isActive: Boolean = now.isAfter(from)
}


object Dependencies {
  val reads: Reads[Dependencies] = {
    implicit val dtr = RestFormats.dateTimeFormats
    implicit val bvr = BobbyVersionRange.reads
    implicit val brvf =
      ( (__ \ "reason" ).read[String]
      ~ (__ \ "range"  ).read[BobbyVersionRange]
      ~ (__ \ "from"   ).read[LocalDate]
      )(BobbyRuleViolation.apply _)

    implicit val osf = {
      implicit val vf = Version.format
      Json.reads[Dependency]
    }
    Json.reads[Dependencies]
  }

  def collectViolations(dependenciesSeq: Seq[Dependencies], f: Dependency => Seq[BobbyRuleViolation]): Seq[(String, BobbyRuleViolation)] =
    dependenciesSeq
      .flatMap(
          _.toSeq
           .flatMap(dependency => f(dependency).map(v => (dependency.name, v))))
      .toSet
      .toList
      .sortBy { (e: (String, BobbyRuleViolation)) => e._1 }
}

case class Version(
    major: Int,
    minor: Int,
    patch: Int,
    original: String)
  extends Ordered[Version] {

  //!@TODO test
  def diff(other: Version): (Int, Int, Int) =
    (this.major - other.major, this.minor - other.minor, this.patch - other.patch)

  override def compare(other: Version): Int =
    if (major == other.major)
      if (minor == other.minor)
        patch - other.patch
      else
        minor - other.minor
    else
      major - other.major

  override def toString: String = original
}

object Version {
  def getVersionState(currentVersion: Version, latestVersion: Version): VersionState =
    latestVersion.diff(currentVersion) match {
      case (0, 0, 0)                                   => VersionState.UpToDate
      case (0, minor, patch) if minor > 0 || patch > 0 => VersionState.MinorVersionOutOfDate
      case (major, _, _) if major >= 1                 => VersionState.MajorVersionOutOfDate
      case _                                           => VersionState.Invalid
    }

  def parse(s: String): Option[Version] = {
    val regex3 = """(\d+)\.(\d+)\.(\d+)(.*)""".r
    val regex2 = """(\d+)\.(\d+)(.*)""".r
    val regex1 = """(\d+)(.*)""".r
    s match {
      case regex3(maj, min, patch, _) => Some(Version(Integer.parseInt(maj), Integer.parseInt(min), Integer.parseInt(patch), s))
      case regex2(maj, min,  _)       => Some(Version(Integer.parseInt(maj), Integer.parseInt(min), 0                      , s))
      case regex1(patch,  _)          => Some(Version(0                    , 0                    , Integer.parseInt(patch), s))
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
        case JsObject(m) => m.get("original") match {
                              case Some(JsString(s)) => parseStr(s)
                              case _                 => JsError("Not a string")
                            }
        case _           => JsError("Not a string")
      }
    }

    override def writes(v: Version) =
      JsString(v.original)
  }
}


trait VersionOp {
  def s: String
}
object VersionOp {
  case object Gte extends VersionOp { val s = ">=" }
  case object Lte extends VersionOp { val s = "<=" }
  case object Eq  extends VersionOp { val s = "==" }

  def parse(s: String): Option[VersionOp] =
    s match {
      case Gte.s => Some(Gte)
      case Lte.s => Some(Lte)
      case Eq.s  => Some(Eq)
      case _     => None
    }
}


case class ServiceWithDependency(
  slugName          : String,
  slugVersion       : String,
  teams             : List[String],
  depGroup          : String,
  depArtefact       : String,
  depVersion        : String,
  depSemanticVersion: Option[Version])


object ServiceWithDependency {
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  val reads: Reads[ServiceWithDependency] =
    ( (__ \ "slugName"   ).read[String]
    ~ (__ \ "slugVersion").read[String]
    ~ (__ \ "teams"      ).read[List[String]]
    ~ (__ \ "depGroup"   ).read[String]
    ~ (__ \ "depArtefact").read[String]
    ~ (__ \ "depVersion" ).read[String]
    ~ (__ \ "depVersion" ).read[String].map(Version.parse)
    )(ServiceWithDependency.apply _)
}

case class GroupArtefacts(group: String, artefacts: List[String])

object GroupArtefacts {
  val apiFormat: OFormat[GroupArtefacts] =
    ( (__ \ "group"      ).format[String]
    ~ (__ \ "artefacts"  ).format[List[String]]
    )(GroupArtefacts.apply, unlift(GroupArtefacts.unapply))
}