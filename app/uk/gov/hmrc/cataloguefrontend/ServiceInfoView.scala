/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import uk.gov.hmrc.cataloguefrontend.connector.{DeploymentVO, TargetEnvironment}
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependencies, Dependency}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterState, ShutterStatusValue}

object ServiceInfoView {
  def isShuttered(withShutterState: Map[Environment, ShutterState])(inEnvironment: TargetEnvironment): Boolean =
    TargetEnvironment.toEnvironment(inEnvironment).flatMap(withShutterState.get).exists {
      _.status.value == ShutterStatusValue.Shuttered
    }

  // TODO this can go as soon as we have a global Environment type
  def lookupByTargetEnvironment[A](map: Map[String, A], environment: TargetEnvironment): Option[A] =
    map.get(environment.name.toLowerCase)

  /*
   * Capture any curated library dependencies from master / Github that are not referenced by the 'latest' slug,
   * and assume that they represent 'test-only' library dependencies.
   */
  def buildToolsFrom(optMasterDependencies: Option[Dependencies], librariesOfLatestSlug: Seq[Dependency]): Option[Dependencies] =
    optMasterDependencies.map { masterDependencies =>
      val libraryNamesInLatestSlug = librariesOfLatestSlug.map(_.name).toSet
      masterDependencies.copy(
        libraryDependencies = masterDependencies.libraryDependencies.filterNot { library =>
          libraryNamesInLatestSlug.contains(library.name)
        }
      )
    }
}
