/*
 * Copyright 2021 HM Revenue & Customs
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
  http          : HttpClient,
  servicesConfig: ServicesConfig
)(implicit
  ec: ExecutionContext
) {
  import HttpReads.Implicits._

  private val servicesDependenciesBaseUrl: String =
    servicesConfig.baseUrl("service-dependencies")

  def getDependencies(repositoryName: String)(implicit hc: HeaderCarrier): Future[Option[Dependencies]] = {
    implicit val dr = Dependencies.reads
    http.GET[Option[Dependencies]](url"$servicesDependenciesBaseUrl/api/dependencies/$repositoryName")
  }

  def getAllDependencies()(implicit hc: HeaderCarrier): Future[Seq[Dependencies]] = {
    implicit val dr = Dependencies.reads
    http.GET[Seq[Dependencies]](url"$servicesDependenciesBaseUrl/api/dependencies")
  }

  def dependenciesForTeam(team: TeamName)(implicit hc: HeaderCarrier): Future[Seq[Dependencies]] = {
    implicit val dr = Dependencies.reads
    http.GET[Seq[Dependencies]](s"$servicesDependenciesBaseUrl/api/teams/${team.asString}/dependencies")
  }

  def getSlugDependencies(
    serviceName: String,
    version    : Option[Version] = None
  )(implicit
    hc: HeaderCarrier
  ): Future[Seq[ServiceDependencies]] =
    http.GET[Seq[ServiceDependencies]](
      url"$servicesDependenciesBaseUrl/api/sluginfos?name=$serviceName&version=${version.map(_.toString)}"
    )

  def getSlugInfo(
    serviceName: String,
    version    : Option[Version] = None
  )(implicit
    hc: HeaderCarrier
  ): Future[Option[ServiceDependencies]] =
    http.GET[Option[ServiceDependencies]](
      url"$servicesDependenciesBaseUrl/api/sluginfo?name=$serviceName&version=${version.map(_.toString)}"
    )

  def getCuratedSlugDependencies(
    serviceName: String,
    flag       : SlugInfoFlag
  )(implicit
    hc: HeaderCarrier
  ): Future[Seq[Dependency]] = {
    implicit val dr = Dependency.reads
    http
      .GET[Option[Seq[Dependency]]](
        url"$servicesDependenciesBaseUrl/api/slug-dependencies/$serviceName?flag=${flag.asString}"
      )
      .map(_.getOrElse(Seq.empty))
  }

  def getCuratedSlugDependenciesForTeam(
    teamName: TeamName,
    flag    : SlugInfoFlag
  )(implicit
    hc: HeaderCarrier
  ): Future[Map[String, Seq[Dependency]]] = {
    implicit val dr = Dependency.reads
    http.GET[Map[String, Seq[Dependency]]](
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
    http
      .GET[Seq[ServiceWithDependency]](
        url"$servicesDependenciesBaseUrl/api/serviceDeps?$queryParams&scope=${scope.asString}"
      )
  }

  def getGroupArtefacts(implicit hc: HeaderCarrier): Future[List[GroupArtefacts]] = {
    implicit val r = GroupArtefacts.apiFormat
    http.GET[List[GroupArtefacts]](url"$servicesDependenciesBaseUrl/api/groupArtefacts")
  }

  def getJDKVersions(flag: SlugInfoFlag)(implicit hc: HeaderCarrier): Future[List[JDKVersion]] = {
    implicit val r = JDKVersionFormats.jdkFormat
    http.GET[List[JDKVersion]](
      url"$servicesDependenciesBaseUrl/api/jdkVersions?flag=${flag.asString}"
    )
  }

  def getBobbyRuleViolations(implicit hc: HeaderCarrier): Future[Map[(BobbyRule, SlugInfoFlag), Int]] = {
    implicit val brvr = BobbyRulesSummary.reads
    http
      .GET[BobbyRulesSummary](url"$servicesDependenciesBaseUrl/api/bobbyViolations")
      .map(_.summary)
  }

  def getHistoricBobbyRuleViolations(implicit hc: HeaderCarrier): Future[HistoricBobbyRulesSummary] = {
    implicit val brvr = HistoricBobbyRulesSummary.reads
    http.GET[HistoricBobbyRulesSummary](url"$servicesDependenciesBaseUrl/api/historicBobbyViolations")
  }

  def getRepoFor(group: String, artefact: String, version: String)(implicit hc: HeaderCarrier): Future[Option[String]] =
    http.GET[Option[String]](url"$servicesDependenciesBaseUrl/api/dependency-repository?group=$group&artefact=$artefact&version=$version")
}
