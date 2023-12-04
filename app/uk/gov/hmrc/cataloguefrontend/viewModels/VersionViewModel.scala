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

package uk.gov.hmrc.cataloguefrontend.viewModels

import uk.gov.hmrc.cataloguefrontend.connector.model.Version
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhere

case class Row(applicationName: String, index: Int, environments: Seq[EnvironmentField]) {

  val sortedVersions: Seq[Version] =
    environments.collect { case EnvironmentWithVersion(_, version) => version.versionNumber.asVersion }.sorted

  val uniqueVersions: Seq[Version] = sortedVersions.distinct
  val latestVersion: Version = sortedVersions.reverse.head
}


case class VersionViewModel(whatsRunning: Seq[WhatsRunningWhere], environments: Seq[Environment]) {

  val indexedRows: Seq[(WhatsRunningWhere, Int)] = whatsRunning.zipWithIndex

  val toRows: Seq[Row] = indexedRows.map {
    case (WhatsRunningWhere(applicationName, versions), index) =>
      val versionMap: Seq[EnvironmentField] =
        environments.map(env =>
          env -> versions.find(_.environment == env) match {
            case (env, Some(version)) => EnvironmentWithVersion(env, version)
            case _                    => EnvironmentWithoutVersion(env)
          }
        )

      Row(applicationName.asString, index, versionMap)
  }
}
