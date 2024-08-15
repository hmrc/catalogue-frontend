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

package uk.gov.hmrc.cataloguefrontend.connector

import play.api.libs.json.Reads
import uk.gov.hmrc.cataloguefrontend.connector.model._
import uk.gov.hmrc.cataloguefrontend.model.{ServiceName, SlugInfoFlag, TeamName, Version, VersionRange}
import uk.gov.hmrc.cataloguefrontend.service.{ServiceDependencies, SlugVersionInfo}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceDependenciesConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using
  ec: ExecutionContext
):
  import HttpReads.Implicits._

  private val servicesDependenciesBaseUrl: String =
    servicesConfig.baseUrl("service-dependencies")

  def dependenciesForTeam(team: TeamName)(using HeaderCarrier): Future[Seq[Dependencies]] =
    given Reads[Dependencies] = Dependencies.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/teams/${team.asString}/dependencies")
      .execute[Seq[Dependencies]]

  def getSlugInfo(
    serviceName: ServiceName,
    version    : Option[Version] = None
  )(using
    HeaderCarrier
  ): Future[Option[ServiceDependencies]] =
    given Reads[ServiceDependencies] = ServiceDependencies.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/sluginfo?name=${serviceName.asString}&version=${version.map(_.toString)}")
      .execute[Option[ServiceDependencies]]

  def getSlugVersionInfo(
    serviceName: ServiceName
  )(using
    HeaderCarrier
  ): Future[Seq[SlugVersionInfo]] =
    given Reads[SlugVersionInfo] = SlugVersionInfo.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/sluginfo/${serviceName.asString}/versions")
      .execute[Seq[SlugVersionInfo]]

  def getCuratedSlugDependenciesForTeam(
    teamName: TeamName,
    flag    : SlugInfoFlag
  )(using
    HeaderCarrier
  ): Future[Map[String, Seq[Dependency]]] =
    given Reads[Dependency] = Dependency.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/teams/${teamName.asString}/slug-dependencies?flag=${flag.asString}")
      .execute[Map[String, Seq[Dependency]]]

  def getDependencies(
    flag        : SlugInfoFlag,
    group       : String,
    artefact    : String,
    repoType    : Seq[RepoType],
    versionRange: VersionRange,
    scopes      : Seq[DependencyScope]
  )(using
    HeaderCarrier
  ): Future[Seq[RepoWithDependency]] =
    given Reads[RepoWithDependency] = RepoWithDependency.reads
    val queryParams = Seq(
      "flag"         -> flag.asString,
      "group"        -> group,
      "artefact"     -> artefact,
      "versionRange" -> versionRange.range
    )

    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/repoDependencies?$queryParams&scope=${scopes.map(_.asString)}&repoType=${repoType.map(_.asString)}")
      .execute[Seq[RepoWithDependency]]

  def getGroupArtefacts()(using HeaderCarrier): Future[List[GroupArtefacts]] =
    given Reads[GroupArtefacts] = GroupArtefacts.apiFormat
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/groupArtefacts")
      .execute[List[GroupArtefacts]]

  def getJdkVersions(teamName: Option[TeamName], flag: SlugInfoFlag)(using HeaderCarrier): Future[List[JdkVersion]] =
    given Reads[JdkVersion] = JdkVersion.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/jdkVersions?team=${teamName.map(_.asString)}&flag=${flag.asString}")
      .execute[List[JdkVersion]]

  def getSbtVersions(teamName: Option[TeamName], flag: SlugInfoFlag)(using HeaderCarrier): Future[List[SbtVersion]] =
    given Reads[SbtVersion] = SbtVersion.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/sbtVersions?team=${teamName.map(_.asString)}&flag=${flag.asString}")
      .execute[List[SbtVersion]]

  def getBobbyRuleViolations()(using HeaderCarrier): Future[Map[(BobbyRule, SlugInfoFlag), Int]] =
    given Reads[BobbyRulesSummary] = BobbyRulesSummary.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/bobbyViolations")
      .execute[BobbyRulesSummary]
      .map(_.summary)

  def getHistoricBobbyRuleViolations(query: List[String], from: LocalDate, to: LocalDate)(using HeaderCarrier): Future[HistoricBobbyRulesSummary] =
    given Reads[HistoricBobbyRulesSummary] = HistoricBobbyRulesSummary.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/historicBobbyViolations?query=$query&from=$from&to=$to")
      .execute[HistoricBobbyRulesSummary]

  def getRepositoryName(group: String, artefact: String, version: Version)(using HeaderCarrier): Future[Option[String]] =
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/repository-name?group=$group&artefact=$artefact&version=${version.toString}")
      .execute[Option[String]]

  def getRepositoryModulesLatestVersion(repositoryName: String, includeVulnerabilities: Boolean = false)(using HeaderCarrier): Future[Option[RepositoryModules]] =
    given Reads[RepositoryModules] = RepositoryModules.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/repositories/$repositoryName/module-dependencies?version=latest&includeVulnerabilities=$includeVulnerabilities")
      .execute[Seq[RepositoryModules]]
      .map(_.headOption)

  def getRepositoryModules(repositoryName: String, version: Version, includeVulnerabilities: Boolean = false)(using HeaderCarrier): Future[Option[RepositoryModules]] =
    given Reads[RepositoryModules] = RepositoryModules.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/repositories/$repositoryName/module-dependencies?version=${version.toString}&includeVulnerabilities=$includeVulnerabilities")
      .execute[Seq[RepositoryModules]]
      .map(_.headOption)

  def getRepositoryModulesAllVersions(repositoryName: String)(using HeaderCarrier): Future[Seq[RepositoryModules]] =
    given Reads[RepositoryModules] = RepositoryModules.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/repositories/$repositoryName/module-dependencies")
      .execute[Seq[RepositoryModules]]
