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

package uk.gov.hmrc.cataloguefrontend.viewModels.whatsRunningWhere

import uk.gov.hmrc.cataloguefrontend.connector.model.Version
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereVersion

trait EnvironmentField {
  val env: Environment
}

case class EnvironmentWithoutVersion(env: Environment) extends EnvironmentField
case class EnvironmentWithVersion(env: Environment, version: WhatsRunningWhereVersion) extends EnvironmentField {

  val toVersion: Version        = version.versionNumber.asVersion
  val isPatchedVersion: Boolean = toVersion.patch > 0
  def toolTipContent(latestVersion: Version): String = {

    val latestMajorVersion: Int = latestVersion.major
    val latestMinorVersion: Int = latestVersion.minor
    val latestPatchVersion: Int = latestVersion.patch

    val thisMajorVersion: Int = toVersion.major
    val thisMinorVersion: Int = toVersion.minor
    val thisPatchVersion: Int = toVersion.patch

    val majorVersionContent = if (thisMajorVersion < latestMajorVersion) {
      val versionDiff: Int = latestMajorVersion - thisMajorVersion
      val plural = if (versionDiff > 1) "'s" else ""
      Some(s"${versionDiff} major version${plural} behind")
    } else None

    val minorVersionContent = if (thisMinorVersion < latestMinorVersion) {
      val versionDiff: Int = latestMinorVersion - thisMinorVersion
      val plural = if (versionDiff > 1) "'s" else ""
      Some(s"${versionDiff} minor version${plural} behind")
    } else None

    val patchAheadVersionContent = if (toVersion.patch > 0) {
      val latestNonPatchedVersion = s"${latestMajorVersion}.${latestMinorVersion}.0"
      val plural = if (thisPatchVersion > 1) "'s" else ""
      Some(s"${thisPatchVersion} patch/hotfix version${plural} ahead latest minor version [${latestNonPatchedVersion}]")
    } else None

    val patchBehindVersionContent = if (latestPatchVersion > 0 && toVersion.patch == 0) {
      val plural = if (latestPatchVersion > 1) "'s" else ""
      Some(s"${latestPatchVersion} version${plural} behind latest patch/hotfix version [${latestVersion.toString}]. Consider releasing new minor version.")
    } else None

    val listOfStringsToConcat = List(majorVersionContent, minorVersionContent, patchAheadVersionContent, patchBehindVersionContent)

    val buildHtml: String =
      s"""
        |<div><strong>Latest version:</strong> ${latestVersion.toString}</div>
        |<div><strong>Current version:</strong> ${toVersion.toString}</div>
        |<ul>
        | ${listOfStringsToConcat.flatten.map(x => s"<li>${x}</li>").mkString}
        |</ul>""".stripMargin
    buildHtml
  }

}
