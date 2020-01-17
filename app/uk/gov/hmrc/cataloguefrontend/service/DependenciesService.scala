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

package uk.gov.hmrc.cataloguefrontend.service

import javax.inject._
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cataloguefrontend.connector.model._
import uk.gov.hmrc.cataloguefrontend.connector.{DeploymentVO, ServiceDependenciesConnector, SlugInfoFlag}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DependenciesService @Inject()(
  serviceDependenciesConnector: ServiceDependenciesConnector
)(implicit val ec: ExecutionContext) {

  def search(serviceName: String, deployments: Seq[DeploymentVO])
            (implicit hc: HeaderCarrier): Future[Seq[ServiceDependencies]] = {
    serviceDependenciesConnector.getSlugDependencies(serviceName).map {
      _.map { serviceDependency =>
        val environmentMappingName =
          deployments
            .find(deploymentVO => serviceDependency.version.contains(deploymentVO.version))
            .map(_.environmentMapping.name)

        environmentMappingName match {
          case Some(_) => serviceDependency.copy(environment = environmentMappingName)
          case None    => serviceDependency
        }
      }
    }
  }

  def getServicesWithDependency(
      optTeam     : Option[TeamName],
      flag        : SlugInfoFlag,
      group       : String,
      artefact    : String,
      versionRange: BobbyVersionRange)(implicit hc: HeaderCarrier): Future[Seq[ServiceWithDependency]] =
    serviceDependenciesConnector
      .getServicesWithDependency(flag, group, artefact, versionRange)
      .map { l =>
        optTeam match {
          case None       => l
          case Some(team) => l.filter(_.teams.contains(team))
        }
      }
      .map(_
        .sortBy(_.slugName)
        .sorted(Ordering.by((_: ServiceWithDependency).depSemanticVersion).reverse))

  def getGroupArtefacts(implicit hc: HeaderCarrier): Future[List[GroupArtefacts]] =
    serviceDependenciesConnector
      .getGroupArtefacts
      .map(_.map(g => g.copy(artefacts = g.artefacts.sorted)))
      .map(_.sortBy(_.group))

  def getJDKVersions(flag: SlugInfoFlag)(implicit hc: HeaderCarrier) : Future[List[JDKVersion]] =
    serviceDependenciesConnector
      .getJDKVersions(flag)
      .map(_.sortBy(_.version))

  def getJDKCountsForEnv(env: SlugInfoFlag)(implicit hc: HeaderCarrier) : Future[JDKUsageByEnv] =
    for {
      versions <- serviceDependenciesConnector.getJDKVersions(env)
      counts   =  versions.groupBy(j => j.copy(name="", kind = JDK) ).mapValues(_.length)
    } yield JDKUsageByEnv(env.asString, counts)

}

object DependenciesService {

  def sortDependencies(dependencies: Seq[ServiceDependency]): Seq[ServiceDependency] =
    dependencies.sortBy(serviceDependency => (serviceDependency.group, serviceDependency.artifact))
}


case class ServiceJDKVersion(version: String, vendor: String, kind:String)

case class ServiceDependency(
    path    : String
  , group   : String
  , artifact: String
  , version : String
  , meta    : String = ""
  )

case class ServiceDependencies(
      uri          : String
    , name         : String
    , version      : Option[String]
    , runnerVersion: String
    , java         : ServiceJDKVersion
    , classpath    : String
    , dependencies : Seq[ServiceDependency]
    , environment  : Option[String] = None
    ) {
  val isEmpty: Boolean = dependencies.isEmpty

  val nonEmpty: Boolean = dependencies.nonEmpty

  val semanticVersion: Option[Version] =
    version.flatMap(Version.parse)
}

object ServiceDependencies {
  import play.api.libs.functional.syntax._
  import play.api.libs.json.__

  implicit val jdkr = (
        (__ \ "version").read[String]
      ~ (__ \ "vendor" ).read[String]
      ~ (__ \ "kind"   ).read[String]
    )(ServiceJDKVersion)

  implicit val dependencyReads: Reads[ServiceDependency] = Json.using[Json.WithDefaultValues].reads[ServiceDependency]
  implicit val serviceDependenciesReads: Reads[ServiceDependencies] = (
    (__ \ "uri"          ).read[String]
  ~ (__ \ "name"         ).read[String]
  ~ (__ \ "version"      ).readNullable[String]
  ~ (__ \ "runnerVersion").read[String]
  ~ (__ \ "java"         ).read[ServiceJDKVersion]
  ~ (__ \ "classpath"    ).read[String]
  ~ (__ \ "dependencies" ).read[Seq[ServiceDependency]]
  ~ (__ \ "environment"  ).readNullable[String]
  )(ServiceDependencies.apply _)

}
