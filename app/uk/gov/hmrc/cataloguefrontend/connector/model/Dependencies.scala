/*
 * Copyright 2017 HM Revenue & Customs
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
import play.api.libs.json.{JsPath, Json, Reads}
import uk.gov.hmrc.http.controllers.RestFormats



case class Dependency(
                       name: String,
                       currentVersion: Version,
                       latestVersion: Option[Version],
                       isExternal: Boolean = false
                     )

case class Dependencies(
                         repositoryName: String,
                         libraryDependencies: Seq[Dependency],
                         sbtPluginsDependencies: Seq[Dependency],
                         otherDependencies: Seq[Dependency],
                         lastUpdated: DateTime
                       )

object Dependencies {
  implicit val vf = Json.format[Version]
  implicit val dtr = RestFormats.dateTimeFormats
  implicit val osf = Json.format[Dependency]
  implicit val format = Json.format[Dependencies]
}



case class Version(major: Int, minor: Int, patch: Int) {
  override def toString: String = s"$major.$minor.$patch"

  //!@TODO test
  def -(other: Version): (Int, Int, Int) = {
    (this.major - other.major, this.minor - other.minor, this.patch - other.patch)
  }
  def +(other: Version): Version = {
    Version(this.major + other.major, this.minor + other.minor, this.patch + other.patch)
  }


}