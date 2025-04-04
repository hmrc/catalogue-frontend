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

import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, ServiceDependenciesConnector}
import uk.gov.hmrc.cataloguefrontend.connector.model._
import uk.gov.hmrc.cataloguefrontend.model.{Environment, DigitalService, ServiceName, SlugInfoFlag, TeamName, Version, VersionRange}
import uk.gov.hmrc.cataloguefrontend.util.DependencyGraphParser
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereVersion
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DependenciesService @Inject() (
  serviceDependenciesConnector: ServiceDependenciesConnector
)(using ExecutionContext):

  def search(
    serviceName: ServiceName,
    deployments: Seq[WhatsRunningWhereVersion]
  )(using
    HeaderCarrier
  ): Future[Seq[ServiceDependencies]] =
    Future
      .traverse(deployments): wrwv =>
        serviceDependenciesConnector
          .getSlugInfo(serviceName, Some(wrwv.version))
          .map(_.map(_.copy(environment = Some(wrwv.environment))))
      .map(_.flatten)

  def getServiceDependencies(
    serviceName: ServiceName,
    version    : Version
  )(using
    HeaderCarrier
  ): Future[Option[ServiceDependencies]] =
    serviceDependenciesConnector
      .getSlugInfo(serviceName, Some(version)).map(_.headOption)

  def getDependencies(
    optTeam     : Option[TeamName],
    flag        : SlugInfoFlag,
    repoType    : Seq[RepoType],
    group       : String,
    artefact    : String,
    versionRange: VersionRange,
    scope       : Seq[DependencyScope]
  )(using
    HeaderCarrier
  ): Future[Seq[RepoWithDependency]] =
    serviceDependenciesConnector
      .getDependencies(flag, group, artefact, repoType, versionRange, scope)
      .map: l =>
        optTeam match
          case None       => l
          case Some(team) => l.filter(_.teams.contains(team))
      .map(
        _.sortBy(_.repoName)
          .sorted(Ordering.by((_: RepoWithDependency).depVersion).reverse)
      )

  def getGroupArtefacts()(using HeaderCarrier): Future[List[GroupArtefacts]] =
    serviceDependenciesConnector.getGroupArtefacts()
      .map(_.map(g => g.copy(artefacts = g.artefacts.sorted)))
      .map(_.sortBy(_.group))

  def getJdkVersions(flag: SlugInfoFlag, teamName: Option[TeamName], digitalService: Option[DigitalService])(using HeaderCarrier): Future[List[JdkVersion]] =
    serviceDependenciesConnector.getJdkVersions(flag, teamName, digitalService)

  def getJdkCountsForEnv(env: SlugInfoFlag, teamName: Option[TeamName], digitalService: Option[DigitalService])(using HeaderCarrier): Future[JdkUsageByEnv] =
    for
      versions <- serviceDependenciesConnector.getJdkVersions(env, teamName, digitalService)
      counts   =  versions.groupBy(v => (v.version, v.vendor, v.kind)).view.mapValues(_.length).toMap
    yield JdkUsageByEnv(env, counts)

  def getSbtVersions(flag: SlugInfoFlag, teamName: Option[TeamName], digitalService: Option[DigitalService])(using HeaderCarrier): Future[List[SbtVersion]] =
    serviceDependenciesConnector.getSbtVersions(flag, teamName, digitalService)

  def getSbtCountsForEnv(env: SlugInfoFlag, teamName: Option[TeamName], digitalService: Option[DigitalService])(using HeaderCarrier): Future[SbtUsageByEnv] =
    for
      versions <- serviceDependenciesConnector.getSbtVersions(env, teamName, digitalService)
      counts   =  versions.groupBy(_.version).view.mapValues(_.length).toMap
    yield SbtUsageByEnv(env, counts)

end DependenciesService

object DependenciesService:
  def sortDependencies(dependencies: Seq[ServiceDependency]): Seq[ServiceDependency] =
    dependencies.sortBy(serviceDependency => (serviceDependency.group, serviceDependency.artefact))

  def sortAndSeparateDependencies(serviceDependencies: ServiceDependencies): (Seq[ServiceDependency], Seq[TransitiveServiceDependency]) =
    serviceDependencies.dependencyDotCompile
      .fold(
        (serviceDependencies.dependencies, Seq.empty[TransitiveServiceDependency])
      ): graph =>
        DependencyGraphParser
          .parse(graph)
          .dependenciesWithImportPath
          .partition(_._2.length == 2) match
            case (direct, transitive) =>
              ( direct.map(_._1).sortBy(d => (d.group, d.artefact))
              , transitive
                  .map(t => TransitiveServiceDependency(t._1, t._2.takeRight(2).head))
                  .sortBy(d => (d.dependency.group, d.dependency.artefact))
              )

end DependenciesService

case class ServiceJdkVersion(
  version: String,
  vendor : Vendor,
  kind   : Kind
)

case class ServiceDependency(
  group   : String,
  artefact: String,
  version : String
)

case class TransitiveServiceDependency(
  dependency: ServiceDependency,
  importedBy: ServiceDependency
)

case class ServiceDependencies(
  uri                  : String,
  name                 : String,
  version              : Version,
  runnerVersion        : String,
  java                 : ServiceJdkVersion,
  classpath            : String,
  dependencies         : Seq[ServiceDependency],
  environment          : Option[Environment] = None,
  dependencyDotCompile : Option[String] = None,
  dependencyDotProvided: Option[String] = None,
  dependencyDotTest    : Option[String] = None,
  dependencyDotIt      : Option[String] = None,
  dependencyDotBuild   : Option[String] = None
):
  val isEmpty: Boolean = dependencies.isEmpty

  val nonEmpty: Boolean = dependencies.nonEmpty

  def dotFileForScope(scope: DependencyScope): Option[String] =
    scope match
      case DependencyScope.Compile  => dependencyDotCompile
      case DependencyScope.Provided => dependencyDotProvided
      case DependencyScope.Test     => dependencyDotTest
      case DependencyScope.It       => dependencyDotIt
      case DependencyScope.Build    => dependencyDotBuild

object ServiceDependencies:
  private val serviceJdkVersionReads =
    ( (__ \ "version").read[String]
    ~ (__ \ "vendor" ).read[Vendor](Vendor.reads)
    ~ (__ \ "kind"   ).read[Kind  ](Kind.reads)
    )(ServiceJdkVersion.apply)

  private val serviceDependencyReads: Reads[ServiceDependency] =
    ( (__ \ "group"   ).read[String]
    ~ (__ \ "artifact").read[String]
    ~ (__ \ "version" ).read[String]
    )(ServiceDependency.apply)

  val reads: Reads[ServiceDependencies] =
    given Reads[Version          ] = Version.format
    given Reads[ServiceJdkVersion] = serviceJdkVersionReads
    given Reads[ServiceDependency] = serviceDependencyReads
    ( (__ \ "uri"                       ).read[String]
    ~ (__ \ "name"                      ).read[String]
    ~ (__ \ "version"                   ).read[Version]
    ~ (__ \ "runnerVersion"             ).read[String]
    ~ (__ \ "java"                      ).read[ServiceJdkVersion]
    ~ (__ \ "classpath"                 ).read[String]
    ~ (__ \ "dependencies"              ).read[Seq[ServiceDependency]]
    ~ (__ \ "environment"               ).readNullable[Environment]
    ~ (__ \ "dependencyDot" \ "compile" ).readNullable[String].map(_.filter(_.nonEmpty))
    ~ (__ \ "dependencyDot" \ "provided").readNullable[String].map(_.filter(_.nonEmpty))
    ~ (__ \ "dependencyDot" \ "test"    ).readNullable[String].map(_.filter(_.nonEmpty))
    ~ (__ \ "dependencyDot" \ "it"      ).readNullable[String].map(_.filter(_.nonEmpty))
    ~ (__ \ "dependencyDot" \ "build"   ).readNullable[String].map(_.filter(_.nonEmpty))
    )(ServiceDependencies.apply)


case class SlugVersionInfo(
  version: Version,
  latest : Boolean,
  created: Instant
)

object SlugVersionInfo {
  import play.api.libs.functional.syntax._
  import play.api.libs.json.__

  val reads: Reads[SlugVersionInfo] =
    ( (__ \ "version").read[Version](Version.format)
    ~ (__ \ "latest" ).readWithDefault[Boolean](false)
    ~ (__ \ "created").read[Instant]
    )(SlugVersionInfo.apply)
}
