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

case class VersionRow(applicationName: String, index: Int, environments: Seq[EnvironmentField]) {

  val sortedVersions: Seq[Version] =
    environments.collect { case EnvironmentWithVersion(_, version) => version.versionNumber.asVersion }.sorted

  val uniqueVersions: Seq[Version] = sortedVersions.distinct
  val latestVersion: Version = sortedVersions.reverse.head

  def versionOpacity(version: Version): Double = {
    val versionOpacity = if (version.major < latestVersion.major) 1 else 0.50

    val age = uniqueVersions.indexOf(version)
    (1 - (age.toDouble / uniqueVersions.length)) * versionOpacity
  }
}
