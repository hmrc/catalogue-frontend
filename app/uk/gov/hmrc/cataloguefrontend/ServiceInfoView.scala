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
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependencies, Dependency, Version}
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterState, ShutterStatusValue, Environment => ShutteringEnvironment}

object ServiceInfoView {
  def isShuttered(withShutterState: Map[ShutteringEnvironment, ShutterState])(inEnvironment: TargetEnvironment): Boolean =
    TargetEnvironment.toShutteringEnvironment(inEnvironment).flatMap(withShutterState.get).exists {
      _.status.value == ShutterStatusValue.Shuttered
    }

  def slugDependencies(
      withDeployments : Map[String, Seq[DeploymentVO]]
    , withDependencies: Map[Version, Seq[Dependency]]
    )(forEnvironment: TargetEnvironment
    ): Seq[Dependency] =
    lookupVersion(withDeployments)(forEnvironment)
      .flatMap(withDependencies.get)
      .getOrElse(Seq.empty)

  def deploymentVersion(withDeployments: Map[String, Seq[DeploymentVO]])
                       (forEnvironment: TargetEnvironment): Option[Version] =
    lookupVersion(withDeployments)(forEnvironment)

  private def lookupVersion(
      deploymentsByEnvironmentName: Map[String, Seq[DeploymentVO]]
    )(forEnvironment: TargetEnvironment
    ): Option[Version] =
    // a single environment may have multiple versions during a deployment
    // return the lowest
    deploymentsByEnvironmentName.get(forEnvironment.name.toLowerCase)
      .flatMap(_.map(_.version).sorted.headOption)

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
