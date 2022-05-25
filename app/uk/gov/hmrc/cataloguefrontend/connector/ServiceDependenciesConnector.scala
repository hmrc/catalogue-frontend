/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads}
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceDependenciesConnector @Inject() (
  httpClient    : HttpClient,
  servicesConfig: ServicesConfig
)(implicit
  ec: ExecutionContext
) {
  import HttpReads.Implicits._

  private val servicesDependenciesBaseUrl: String =
    servicesConfig.baseUrl("service-dependencies")

  def dependenciesForTeam(team: TeamName)(implicit hc: HeaderCarrier): Future[Seq[Dependencies]] = {
    implicit val dr = Dependencies.reads
    httpClient.GET[Seq[Dependencies]](url"$servicesDependenciesBaseUrl/api/teams/${team.asString}/dependencies")
  }

  def getSlugInfo(
    serviceName: String,
    version    : Option[Version] = None
  )(implicit
    hc: HeaderCarrier
  ): Future[Option[ServiceDependencies]] = {
    implicit val sdr = ServiceDependencies.reads
    httpClient.GET[Option[ServiceDependencies]](
      url"$servicesDependenciesBaseUrl/api/sluginfo?name=$serviceName&version=${version.map(_.toString)}"
    )
  }

  def getCuratedSlugDependenciesForTeam(
    teamName: TeamName,
    flag    : SlugInfoFlag
  )(implicit
    hc: HeaderCarrier
  ): Future[Map[String, Seq[Dependency]]] = {
    implicit val dr = Dependency.reads
    httpClient.GET[Map[String, Seq[Dependency]]](
      url"$servicesDependenciesBaseUrl/api/teams/${teamName.asString}/slug-dependencies?flag=${flag.asString}"
    )
  }

  def getServicesWithDependency(
    flag        : SlugInfoFlag,
    group       : String,
    artefact    : String,
    versionRange: BobbyVersionRange,
    scope       : DependencyScope
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
    httpClient
      .GET[Seq[ServiceWithDependency]](
        url"$servicesDependenciesBaseUrl/api/serviceDeps?$queryParams&scope=${scope.asString}"
      )
  }

  def getGroupArtefacts(implicit hc: HeaderCarrier): Future[List[GroupArtefacts]] = {
    implicit val r = GroupArtefacts.apiFormat
    httpClient.GET[List[GroupArtefacts]](url"$servicesDependenciesBaseUrl/api/groupArtefacts")
  }

  def getJDKVersions(flag: SlugInfoFlag)(implicit hc: HeaderCarrier): Future[List[JDKVersion]] = {
    implicit val r = JDKVersionFormats.jdkFormat
    httpClient.GET[List[JDKVersion]](
      url"$servicesDependenciesBaseUrl/api/jdkVersions?flag=${flag.asString}"
    )
  }

  def getBobbyRuleViolations(implicit hc: HeaderCarrier): Future[Map[(BobbyRule, SlugInfoFlag), Int]] = {
    implicit val brvr = BobbyRulesSummary.reads
    httpClient
      .GET[BobbyRulesSummary](url"$servicesDependenciesBaseUrl/api/bobbyViolations")
      .map(_.summary)
  }

  def getHistoricBobbyRuleViolations(query: List[String])(implicit hc: HeaderCarrier): Future[HistoricBobbyRulesSummary] = {
    implicit val brvr = HistoricBobbyRulesSummary.reads
    httpClient.GET[HistoricBobbyRulesSummary](url"$servicesDependenciesBaseUrl/api/historicBobbyViolations?query=$query")
  }

  def getRepositoryName(group: String, artefact: String, version: Version)(implicit hc: HeaderCarrier): Future[Option[String]] =
    httpClient.GET[Option[String]](url"$servicesDependenciesBaseUrl/api/repository-name?group=$group&artefact=$artefact&version=${version.toString}")

  def getRepositoryModules(repositoryName: String)(implicit hc: HeaderCarrier): Future[Option[RepositoryModules]] = {
    implicit val dr = RepositoryModules.reads
    httpClient.GET[Option[RepositoryModules]](url"$servicesDependenciesBaseUrl/api/module-dependencies/$repositoryName")
  }

  def getRepositoryModules(repositoryName: String, version: Version)(implicit hc: HeaderCarrier): Future[Option[RepositoryModules]] = {
    implicit val dr = RepositoryModules.reads
    httpClient.GET[Option[RepositoryModules]](url"$servicesDependenciesBaseUrl/api/module-dependencies/$repositoryName?version=${version.toString}")
  }
}
