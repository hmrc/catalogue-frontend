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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.connector.model._
import uk.gov.hmrc.cataloguefrontend.model.SlugInfoFlag
import uk.gov.hmrc.cataloguefrontend.service.ServiceDependencies
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceDependenciesConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit
  ec: ExecutionContext
) {
  import HttpReads.Implicits._

  private val servicesDependenciesBaseUrl: String =
    servicesConfig.baseUrl("service-dependencies")

  def dependenciesForTeam(team: TeamName)(implicit hc: HeaderCarrier): Future[Seq[Dependencies]] = {
    implicit val dr = Dependencies.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/teams/${team.asString}/dependencies")
      .execute[Seq[Dependencies]]
  }

  def getSlugInfo(
    serviceName: String,
    version    : Option[Version] = None
  )(implicit
    hc: HeaderCarrier
  ): Future[Option[ServiceDependencies]] = {
    implicit val sdr = ServiceDependencies.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/sluginfo?name=$serviceName&version=${version.map(_.toString)}")
      .execute[Option[ServiceDependencies]]
  }

  def getCuratedSlugDependenciesForTeam(
    teamName: TeamName,
    flag    : SlugInfoFlag
  )(implicit
    hc: HeaderCarrier
  ): Future[Map[String, Seq[Dependency]]] = {
    implicit val dr = Dependency.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/teams/${teamName.asString}/slug-dependencies?flag=${flag.asString}")
      .execute[Map[String, Seq[Dependency]]]
  }

  def getServicesWithDependency(
    flag        : SlugInfoFlag,
    group       : String,
    artefact    : String,
    versionRange: BobbyVersionRange,
    scopes      : List[DependencyScope]
  )(implicit
    hc: HeaderCarrier
  ): Future[Seq[ServiceWithDependency]] = {
    implicit val r = ServiceWithDependency.reads
    val queryParams = Seq(
      "flag"         -> flag.asString,
      "group"        -> group,
      "artefact"     -> artefact,
      "versionRange" -> versionRange.range
    )

    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/serviceDeps?$queryParams&scope=${scopes.map(_.asString)}")
      .execute[Seq[ServiceWithDependency]]
  }

  def getGroupArtefacts(implicit hc: HeaderCarrier): Future[List[GroupArtefacts]] = {
    implicit val r = GroupArtefacts.apiFormat
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/groupArtefacts")
      .execute[List[GroupArtefacts]]
  }

  def getJDKVersions(teamName: Option[TeamName], flag: SlugInfoFlag)(implicit hc: HeaderCarrier): Future[List[JDKVersion]] = {
    implicit val r = JDKVersionFormats.jdkFormat
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/jdkVersions?team=${teamName.map(_.asString)}&flag=${flag.asString}")
      .execute[List[JDKVersion]]
  }

  def getBobbyRuleViolations(implicit hc: HeaderCarrier): Future[Map[(BobbyRule, SlugInfoFlag), Int]] = {
    implicit val brvr = BobbyRulesSummary.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/bobbyViolations")
      .execute[BobbyRulesSummary]
      .map(_.summary)
  }

  def getHistoricBobbyRuleViolations(query: List[String], from: LocalDate, to: LocalDate)(implicit hc: HeaderCarrier): Future[HistoricBobbyRulesSummary] = {
    implicit val brvr = HistoricBobbyRulesSummary.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/historicBobbyViolations?query=$query&from=$from&to=$to")
      .execute[HistoricBobbyRulesSummary]
  }

  def getRepositoryName(group: String, artefact: String, version: Version)(implicit hc: HeaderCarrier): Future[Option[String]] =
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/repository-name?group=$group&artefact=$artefact&version=${version.toString}")
      .execute[Option[String]]

  def getRepositoryModules(repositoryName: String)(implicit hc: HeaderCarrier): Future[Option[RepositoryModules]] = {
    implicit val dr = RepositoryModules.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/module-dependencies/$repositoryName")
      .execute[Option[RepositoryModules]]
  }

  def getRepositoryModules(repositoryName: String, version: Version)(implicit hc: HeaderCarrier): Future[Option[RepositoryModules]] = {
    implicit val dr = RepositoryModules.reads
    httpClientV2
      .get(url"$servicesDependenciesBaseUrl/api/module-dependencies/$repositoryName?version=${version.toString}")
      .execute[Option[RepositoryModules]]
  }
}
