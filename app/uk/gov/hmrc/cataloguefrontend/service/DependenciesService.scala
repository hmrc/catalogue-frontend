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
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class DependenciesService @Inject()(serviceDependenciesConnector: ServiceDependenciesConnector) {

  def search(serviceName: String, serviceDeploymentInformation: Either[Throwable, ServiceDeploymentInformation])
            (implicit hc: HeaderCarrier): Future[Seq[ServiceDependencies]] = {
    val deployments = getDeployments(serviceDeploymentInformation)

    serviceDependenciesConnector.getSlugDependencies(serviceName).map { item =>
      item.map { serviceDependency =>
        val environmentMappingName = deployments.find {
          deploymentVO => serviceDependency.version.nonEmpty && deploymentVO.version == serviceDependency.version.get
        } .map {
          deploymentVO => deploymentVO.environmentMapping.name
        }

        environmentMappingName match {
          case Some(_) => serviceDependency.copy(environment = environmentMappingName)
          case None => serviceDependency
        }
      }
    }
  }

  private def getDeployments(serviceDeploymentInformation: Either[Throwable, ServiceDeploymentInformation]): Seq[DeploymentVO] =
    serviceDeploymentInformation match {
      case Left(t) => Nil
      case Right(sdi) => sdi.deployments
    }

}

object DependenciesService {

  def sortDependencies(dependencies: Seq[ServiceDependency]): Seq[ServiceDependency] = {
    dependencies.sortBy(serviceDependency => (serviceDependency.group, serviceDependency.artifact))
  }

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
