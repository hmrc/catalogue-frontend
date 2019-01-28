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

import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.http.controllers.RestFormats

sealed trait VersionState
case object UpToDate extends VersionState
case object MinorVersionOutOfDate extends VersionState
case object MajorVersionOutOfDate extends VersionState
case object InvalidVersionState extends VersionState

case class Dependency(
  name          : String,
  currentVersion: Version,
  latestVersion : Option[Version],
  isExternal    : Boolean = false) {

  def getVersionState: Option[VersionState] =
    latestVersion.map { latestVersion =>
      latestVersion - currentVersion match {
        case (0, 0, 0)                                   => UpToDate
        case (0, minor, patch) if minor > 0 || patch > 0 => MinorVersionOutOfDate
        case (major, _, _) if major >= 1                 => MajorVersionOutOfDate
        case _                                           => InvalidVersionState
      }
    }

  def isUpToDate: Boolean = {
    this.getVersionState.contains(UpToDate)
  }
}

case class Dependencies(
  repositoryName        : String,
  libraryDependencies   : Seq[Dependency],
  sbtPluginsDependencies: Seq[Dependency],
  otherDependencies     : Seq[Dependency],
  lastUpdated           : DateTime) {

  def hasOutOfDateDependencies: Boolean =
    !(libraryDependencies ++ sbtPluginsDependencies ++ otherDependencies).forall(_.isUpToDate)
}

object Dependencies {
  implicit val vf     = Json.format[Version]
  implicit val dtr    = RestFormats.dateTimeFormats
  implicit val osf    = Json.format[Dependency]
  implicit val format = Json.format[Dependencies]
}

case class Version(major: Int, minor: Int, patch: Int, suffix: Option[String] = None) {
  override def toString: String = s"$major.$minor.$patch" + suffix.map("-"+_).getOrElse("")

  //!@TODO test
  def -(other: Version): (Int, Int, Int) =
    (this.major - other.major, this.minor - other.minor, this.patch - other.patch)
  def +(other: Version): Version =
    Version(this.major + other.major, this.minor + other.minor, this.patch + other.patch)

}


case class ServiceWithDependency(
  slugName    : String,
  slugVersion : String,
  depGroup    : String,
  depArtefact : String,
  depVersion  : String)


object ServiceWithDependency {
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  val reads: Reads[ServiceWithDependency] =
    ( (__ \ "slugName"   ).read[String]
    ~ (__ \ "slugVersion").read[String]
    ~ (__ \ "depGroup"   ).read[String]
    ~ (__ \ "depArtefact").read[String]
    ~ (__ \ "depVersion" ).read[String]
    )(ServiceWithDependency.apply _)
}