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

import play.api.libs.json.Json


case class Dependencies(repositoryName: String,
                        libraryDependenciesState: Seq[LibraryDependencyState])

case class Version(major: Int, minor: Int, patch: Int)




case class LibraryDependencyState(libraryName: String, currentVersion: Version, latestVersion: Version)


object Dependencies {
  implicit val cvf = Json.format[Version]
  implicit val ldsf = Json.format[LibraryDependencyState]
  implicit val format = Json.format[Dependencies]

}