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

package uk.gov.hmrc.cataloguefrontend.service

import javax.inject._
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cataloguefrontend.{DeploymentVO, ServiceDeploymentInformation}
import uk.gov.hmrc.cataloguefrontend.connector.ServiceDependenciesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.{GroupArtefacts, ServiceWithDependency, Version, VersionOp}
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DependenciesService @Inject()(serviceDependenciesConnector: ServiceDependenciesConnector) {

  import ExecutionContext.Implicits.global

  def search(serviceName: String, serviceDeploymentInformation: Either[Throwable, ServiceDeploymentInformation])
            (implicit hc: HeaderCarrier): Future[Seq[ServiceDependencies]] = {
    val deployments = getDeployments(serviceDeploymentInformation)

    serviceDependenciesConnector.getSlugDependencies(serviceName).map {
      _.map { serviceDependency =>
        val environmentMappingName =
          deployments
            .find(deploymentVO => serviceDependency.version.map(_ == deploymentVO.version).getOrElse(false))
            .map(_.environmentMapping.name)

        environmentMappingName match {
          case Some(_) => serviceDependency.copy(environment = environmentMappingName)
          case None    => serviceDependency
        }
      }
    }
  }

  private def getDeployments(serviceDeploymentInformation: Either[Throwable, ServiceDeploymentInformation]): Seq[DeploymentVO] =
    serviceDeploymentInformation match {
      case Left(t) => Nil
      case Right(sdi) => sdi.deployments
    }

  def getServicesWithDependency(
      group    : String,
      artefact : String,
      versionOp: VersionOp,
      version  : Version)(implicit hc: HeaderCarrier): Future[Seq[ServiceWithDependency]] =
    serviceDependenciesConnector
      .getServicesWithDependency(group, artefact)
      .map { l =>
        versionOp match {
          case VersionOp.Gte => l.filter(_.depSemanticVersion.map(_ >= version).getOrElse(true)) // include invalid semanticVersion in results
          case VersionOp.Lte => l.filter(_.depSemanticVersion.map(_ <= version).getOrElse(true))
          case VersionOp.Eq  => l.filter(_.depSemanticVersion == Some(version))
        }
      }
      .map(_
        .sortBy(_.slugName)
        .sorted(Ordering.by((_: ServiceWithDependency).depSemanticVersion).reverse))

  def getGroupArtefacts(implicit hc: HeaderCarrier): Future[List[GroupArtefacts]] =
    serviceDependenciesConnector
      .getGroupArtefacts
      .map(_.sortBy(_.group))
}

object DependenciesService {

  def sortDependencies(dependencies: Seq[ServiceDependency]): Seq[ServiceDependency] =
    dependencies.sortBy(serviceDependency => (serviceDependency.group, serviceDependency.artifact))
}

case class ServiceDependency(path: String, group: String, artifact: String, version: String, meta: String = "")
case class ServiceDependencies(uri: String,
                               name: String,
                               version: Option[String],
                               runnerVersion: String,
                               classpath: String,
                               dependencies: Seq[ServiceDependency],
                               environment: Option[String] = None) {
  val isEmpty: Boolean = dependencies.isEmpty
  val nonEmpty: Boolean = dependencies.nonEmpty
}

object ServiceDependencies {

  implicit val dependencyReads: Reads[ServiceDependency] = Json.using[Json.WithDefaultValues].reads[ServiceDependency]
  implicit val serviceDependenciesReads: Reads[ServiceDependencies] = Json.using[Json.WithDefaultValues].reads[ServiceDependencies]

}
