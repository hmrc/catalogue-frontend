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

package uk.gov.hmrc.cataloguefrontend

import uk.gov.hmrc.cataloguefrontend.connector.TargetEnvironment
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependencies, Dependency}
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterState
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterStatusValue, Environment => ShutteringEnvironment}

object ServiceInfoView {
  def isShuttered(withShutterState: Map[ShutteringEnvironment, ShutterState])(inEnvironment: TargetEnvironment): Boolean =
    TargetEnvironment.toShutteringEnvironment(inEnvironment).flatMap(withShutterState.get).exists {
      _.status.value == ShutterStatusValue.Shuttered
    }

  def libraryDependenciesOf(optDependencies: Option[Dependencies]): Seq[Dependency] =
    optDependencies.map(_.libraryDependencies).getOrElse(Seq.empty)

  def slugDependencies(withDeployments: Map[String, Seq[DeploymentVO]], withDependencies: Map[String, Seq[Dependency]])
                      (forEnvironment: TargetEnvironment): Seq[Dependency] =
    lookupDeployment(withDeployments)(forEnvironment).flatMap { deployment =>
      withDependencies.get(deployment.version)
    }.getOrElse(Seq.empty)

  def deploymentVersion(withDeployments: Map[String, Seq[DeploymentVO]])
                       (forEnvironment: TargetEnvironment): Option[String] =
    lookupDeployment(withDeployments)(forEnvironment).map(_.version)

  private def lookupDeployment(deploymentsByEnvironmentName: Map[String, Seq[DeploymentVO]])
                              (forEnvironment: TargetEnvironment): Option[DeploymentVO] =
    deploymentsByEnvironmentName.get(forEnvironment.name.toLowerCase).flatMap {
      _.headOption
    }

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

  /*
   * Note that we ignore libraryDependencies obtained from parsing master / GitHub, and replace them with those obtained
   * from the 'latest' slug.  This provides consistency in that 'Platform Dependencies' is always populated from a slug,
   * regardless of the selected tab.
   */
  def platformDependenciesFrom(optMasterDependencies: Option[Dependencies], librariesOfLatestSlug: Seq[Dependency]): Option[Dependencies] =
    optMasterDependencies.map {
      _.copy(
        libraryDependencies = librariesOfLatestSlug
      )
    }
}
