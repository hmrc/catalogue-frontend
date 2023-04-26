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

package uk.gov.hmrc.cataloguefrontend.service

import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cataloguefrontend.connector.ServiceDependenciesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model._
import uk.gov.hmrc.cataloguefrontend.model.{Environment, SlugInfoFlag}
import uk.gov.hmrc.cataloguefrontend.util.DependencyGraphParser
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{JsonCodecs, WhatsRunningWhereVersion}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DependenciesService @Inject() (
  serviceDependenciesConnector: ServiceDependenciesConnector
)(implicit val ec: ExecutionContext) {

  def search(serviceName: String, deployments: Seq[WhatsRunningWhereVersion])(implicit hc: HeaderCarrier): Future[Seq[ServiceDependencies]] =
    Future
      .traverse(deployments)(wrwv =>
        serviceDependenciesConnector
          .getSlugInfo(serviceName, Some(wrwv.versionNumber.asVersion))
          .map(_.map(_.copy(environment = Some(wrwv.environment))))
      )
      .map(_.flatten)

  def getServiceDependencies(
    serviceName: String,
    version    : Version
  )(implicit
    hc: HeaderCarrier
  ): Future[Option[ServiceDependencies]] =
    serviceDependenciesConnector
      .getSlugInfo(serviceName, Some(version)).map(_.headOption)

  def getServicesWithDependency(
    optTeam     : Option[TeamName],
    flag        : SlugInfoFlag,
    group       : String,
    artefact    : String,
    versionRange: BobbyVersionRange,
    scope       : List[DependencyScope]
  )(implicit
    hc: HeaderCarrier
  ): Future[Seq[ServiceWithDependency]] =
    serviceDependenciesConnector
      .getServicesWithDependency(flag, group, artefact, versionRange, scope)
      .map { l =>
        optTeam match {
          case None       => l
          case Some(team) => l.filter(_.teams.contains(team))
        }
      }
      .map(
        _.sortBy(_.slugName)
          .sorted(Ordering.by((_: ServiceWithDependency).depVersion).reverse)
      )

  def getGroupArtefacts(implicit hc: HeaderCarrier): Future[List[GroupArtefacts]] =
    serviceDependenciesConnector.getGroupArtefacts
      .map(_.map(g => g.copy(artefacts = g.artefacts.sorted)))
      .map(_.sortBy(_.group))

  def getJDKVersions(flag: SlugInfoFlag, teamName: Option[TeamName])(implicit hc: HeaderCarrier): Future[List[JDKVersion]] =
    serviceDependenciesConnector
      .getJDKVersions(teamName, flag)

  def getJDKCountsForEnv(env: SlugInfoFlag, teamName: Option[TeamName])(implicit hc: HeaderCarrier): Future[JDKUsageByEnv] =
    for {
      versions <- serviceDependenciesConnector.getJDKVersions(teamName, env)
      counts   =  versions.groupBy(_.copy(name = "", kind = JDK)).view.mapValues(_.length).toMap
    } yield JDKUsageByEnv(env, counts)

  def getSBTVersions(flag: SlugInfoFlag, teamName: Option[TeamName])(implicit hc: HeaderCarrier): Future[List[SBTVersion]] =
    serviceDependenciesConnector
      .getSBTVersions(teamName, flag)

  def getSBTCountsForEnv(env: SlugInfoFlag, teamName: Option[TeamName])(implicit hc: HeaderCarrier): Future[SBTUsageByEnv] =
    for {
      versions <- serviceDependenciesConnector.getSBTVersions(teamName, env)
      counts   =  versions.groupBy(_.copy(name = "")).view.mapValues(_.length).toMap
    } yield SBTUsageByEnv(env, counts)
}

object DependenciesService {

  def sortDependencies(dependencies: Seq[ServiceDependency]): Seq[ServiceDependency] =
    dependencies.sortBy(serviceDependency => (serviceDependency.group, serviceDependency.artifact))

  def sortAndSeparateDependencies(serviceDependencies: ServiceDependencies): (Seq[ServiceDependency], Seq[TransitiveServiceDependency]) =
    serviceDependencies.dependencyDotCompile
      .fold(
        (serviceDependencies.dependencies, Seq.empty[TransitiveServiceDependency])
      )(graph =>
        DependencyGraphParser
          .parse(graph)
          .dependenciesWithImportPath
          .partition(_._2.length == 2) match {
            case (direct, transitive) =>
              ( direct.map(_._1).sortBy(d => (d.group, d.artifact)),
                transitive.map(t => TransitiveServiceDependency(t._1, t._2.takeRight(2).head)).sortBy(d => (d.dependency.group, d.dependency.artifact))
              )
        }
      )
}

case class ServiceJDKVersion(
  version: String,
  vendor : String,
  kind   : String
)

case class ServiceDependency(
  group   : String,
  artifact: String,
  version : String
)

case class TransitiveServiceDependency(
  dependency: ServiceDependency,
  importedBy: ServiceDependency
)

case class ServiceDependencies(
  uri                 : String,
  name                : String,
  version             : Version,
  runnerVersion       : String,
  java                : ServiceJDKVersion,
  classpath           : String,
  dependencies        : Seq[ServiceDependency],
  environment         : Option[Environment] = None,
  dependencyDotCompile: Option[String] = None,
  dependencyDotTest   : Option[String] = None,
  dependencyDotIt     : Option[String] = None,
  dependencyDotBuild  : Option[String] = None
) {
  val isEmpty: Boolean = dependencies.isEmpty

  val nonEmpty: Boolean = dependencies.nonEmpty

  def dotFileForScope(scope: DependencyScope): Option[String] =
    scope match {
      case DependencyScope.Compile => dependencyDotCompile
      case DependencyScope.Test    => dependencyDotTest
      case DependencyScope.It      => dependencyDotIt
      case DependencyScope.Build   => dependencyDotBuild
    }
}

object ServiceDependencies {
  import play.api.libs.functional.syntax._
  import play.api.libs.json.__

  private val serviceJDKVersionReads =
    ( (__ \ "version").read[String]
    ~ (__ \ "vendor" ).read[String]
    ~ (__ \ "kind"   ).read[String]
    )(ServiceJDKVersion)

  private val serviceDependencyReads: Reads[ServiceDependency] =
    Json.using[Json.WithDefaultValues].reads[ServiceDependency]

  val reads: Reads[ServiceDependencies] = {
    implicit val vf   = Version.format
    implicit val jdkr = serviceJDKVersionReads
    implicit val sdr  = serviceDependencyReads
    implicit val envf = JsonCodecs.environmentFormat
    ( (__ \ "uri"                      ).read[String]
    ~ (__ \ "name"                     ).read[String]
    ~ (__ \ "version"                  ).read[Version]
    ~ (__ \ "runnerVersion"            ).read[String]
    ~ (__ \ "java"                     ).read[ServiceJDKVersion]
    ~ (__ \ "classpath"                ).read[String]
    ~ (__ \ "dependencies"             ).read[Seq[ServiceDependency]]
    ~ (__ \ "environment"              ).readNullable[Environment]
    ~ (__ \ "dependencyDot" \ "compile").readNullable[String].map(_.filter(_.nonEmpty))
    ~ (__ \ "dependencyDot" \ "test"   ).readNullable[String].map(_.filter(_.nonEmpty))
    ~ (__ \ "dependencyDot" \ "it"     ).readNullable[String].map(_.filter(_.nonEmpty))
    ~ (__ \ "dependencyDot" \ "build"  ).readNullable[String].map(_.filter(_.nonEmpty))
    )(ServiceDependencies.apply _)
  }
}
