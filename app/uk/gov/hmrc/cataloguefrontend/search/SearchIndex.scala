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

package uk.gov.hmrc.cataloguefrontend.search


import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.routes as catalogueRoutes
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.bobby.routes as bobbyRoutes
import uk.gov.hmrc.cataloguefrontend.createrepository.routes as createRepoRoutes
import uk.gov.hmrc.cataloguefrontend.dependency.routes as dependencyRoutes
import uk.gov.hmrc.cataloguefrontend.deployments.routes as deployRoutes
import uk.gov.hmrc.cataloguefrontend.leakdetection.routes as leakRoutes
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.prcommenter.{PrCommenterConnector, routes as prcommenterRoutes}
import uk.gov.hmrc.cataloguefrontend.repository.routes as reposRoutes
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.{ServiceConfigsConnector, routes as serviceConfigsRoutes}
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.routes as commissioningRoutes
import uk.gov.hmrc.cataloguefrontend.servicemetrics.routes as serviceMetricsRoutes
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterType, routes as shutterRoutes}
import uk.gov.hmrc.cataloguefrontend.teams.routes as teamRoutes
import uk.gov.hmrc.cataloguefrontend.test.routes as testJobRoutes
import uk.gov.hmrc.cataloguefrontend.users.routes as userRoutes
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.routes as wrwRoutes
import uk.gov.hmrc.cataloguefrontend.vulnerabilities.routes as vulnerabilitiesRoutes
import uk.gov.hmrc.http.HeaderCarrier

import java.util.concurrent.atomic.AtomicReference
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class SearchTerm(
  linkType       : String,
  name           : String,
  link           : String,
  weight         : Float       = 0.5f,
  hints          : Set[String] = Set.empty,
  openInNewWindow: Boolean     = false
):
  lazy val terms: Set[String] =
    Set(name, linkType).union(hints).map(SearchIndex.normalizeTerm)

@Singleton
class SearchIndex @Inject()(
  config                       : Configuration,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  prCommenterConnector         : PrCommenterConnector,
  userManagementConnector      : UserManagementConnector,
  serviceConfigsConnector      : ServiceConfigsConnector
)(using ExecutionContext):

  import SearchIndex.*

  private[search] val cachedIndex =
    AtomicReference[Map[String, Seq[SearchTerm]]](Map.empty)

  private val hardcodedLinks = List(
    SearchTerm("page", "dependency explorer",          dependencyRoutes.DependencyExplorerController.landing.url,                             1.0f, Set("depex")),
    SearchTerm("page", "jdk explorer",                 catalogueRoutes.JdkVersionController.compareAllEnvironments().url,                     1.0f, Set("jdk", "jre")),
    SearchTerm("page", "leaks",                        leakRoutes.LeakDetectionController.ruleSummaries.url,                                  1.0f),
    SearchTerm("page", "bobby rules",                  bobbyRoutes.BobbyExplorerController.list().url,                                        1.0f),
    SearchTerm("page", "bobby violations",             bobbyRoutes.BobbyExplorerController.bobbyViolations().url,                             1.0f),
    SearchTerm("page", "whats running where (wrw)",    wrwRoutes.WhatsRunningWhereController.releases().url,                                  1.0f, Set("wrw")),
    SearchTerm("page", "deployment",                   deployRoutes.DeploymentEventsController.deploymentEvents(Environment.Production).url,  1.0f),
    SearchTerm("page", "shutter-overview",             shutterRoutes.ShutterOverviewController.allStates(ShutterType.Frontend).url,           1.0f),
    SearchTerm("page", "shutter-api",                  shutterRoutes.ShutterOverviewController.allStates(ShutterType.Api).url,                1.0f),
    SearchTerm("page", "shutter-rate",                 shutterRoutes.ShutterOverviewController.allStates(ShutterType.Rate).url,               1.0f),
    SearchTerm("page", "shutter-events",               shutterRoutes.ShutterEventsController.shutterEvents.url,                               1.0f),
    SearchTerm("page", "teams",                        teamRoutes.TeamsController.allTeams().url,                                             1.0f),
    SearchTerm("page", "repositories",                 reposRoutes.RepositoriesController.allRepositories().url,                              1.0f),
    SearchTerm("page", "users",                        userRoutes.UsersController.users.url,                                                  1.0f),
    SearchTerm("page", "pr-commenter-recommendations", prcommenterRoutes.PrCommenterController.recommendations().url,                         1.0f),
    SearchTerm("page", "search config",                serviceConfigsRoutes.ServiceConfigsController.searchLanding().url,                     1.0f),
    SearchTerm("page", "config warnings",              serviceConfigsRoutes.ServiceConfigsController.configWarningLanding().url,              1.0f),
    SearchTerm("page", "create repository",            createRepoRoutes.CreateRepositoryController.createRepoLandingGet().url,                1.0f),
    SearchTerm("page", "deploy service",               deployRoutes.DeployServiceController.step1(None).url,                                  1.0f),
    SearchTerm("page", "search commissioning state",   commissioningRoutes.ServiceCommissioningStatusController.searchLanding().url,          1.0f),
    SearchTerm("page", "service metrics",              serviceMetricsRoutes.ServiceMetricsController.serviceMetrics().url,                    1.0f),
    SearchTerm("page", "vulnerabilities",              vulnerabilitiesRoutes.VulnerabilitiesController.vulnerabilitiesList().url,             1.0f),
    SearchTerm("page", "vulnerabilities services",     vulnerabilitiesRoutes.VulnerabilitiesController.vulnerabilitiesForServices().url,      1.0f),
    SearchTerm("page", "vulnerabilities timeline ",    vulnerabilitiesRoutes.VulnerabilitiesController.vulnerabilitiesTimeline().url,         1.0f),
    SearchTerm("page", "test results",                 testJobRoutes.TestJobController.allTests().url,                                        1.0f),
    SearchTerm("docs", "mdtp-handbook",                config.get[String]("docs.handbookUrl"),                                                1.0f, openInNewWindow = true),
    SearchTerm("docs", "blog posts",                   config.get[String]("confluence.allBlogsUrl"),                                          1.0f, openInNewWindow = true)
  )

  def updateIndexes(): Future[Unit] =
    given HeaderCarrier = HeaderCarrier()
    for
      repos           <- teamsAndRepositoriesConnector.allRepositories(None, None, None, None, None)
      teams           <- teamsAndRepositoriesConnector.allTeams()
      digitalServices <- teamsAndRepositoriesConnector.allDigitalServices()
      teamPageLinks   =  teams.flatMap(t => List(SearchTerm("team",        t.name.asString, teamRoutes.TeamsController.team(t.name).url, 0.5f),
                                                 SearchTerm("deployments", t.name.asString, s"${wrwRoutes.WhatsRunningWhereController.releases(teamName = Some(t.name)).url}")))
      digitalLinks    =  digitalServices.flatMap(x => List(SearchTerm("digital service", x.asString, teamRoutes.TeamsController.digitalService(x).url, 0.5f),
                                                           SearchTerm("deployments"    , x.asString, s"${wrwRoutes.WhatsRunningWhereController.releases(digitalService = Some(x)).url}")))
      mappings        <- serviceConfigsConnector.serviceRepoMappings
      repoLinks       =  repos.flatMap(r => List(SearchTerm(repoTypeString(r.repoType),    r.name, catalogueRoutes.CatalogueController.repository(r.name).url, 0.5f, Set("repository")),
                                                 SearchTerm("leak",        r.name,          leakRoutes.LeakDetectionController.branchSummaries(r.name).url, 0.5f))
                         ) ++ mappings.flatMap(s => List(SearchTerm(repoTypeString(RepoType.Service), s.serviceName, catalogueRoutes.CatalogueController.service(ServiceName(s.serviceName)).url, 0.5f, Set("repository"))))
      serviceLinks    =  repos.filter(_.repoType == RepoType.Service)
                              .flatMap(r => List(SearchTerm("deploy",              r.name, deployRoutes.DeployServiceController.step1(Some(ServiceName(r.name))).url),
                                                 SearchTerm("config",              r.name, serviceConfigsRoutes.ServiceConfigsController.configExplorer(ServiceName(r.name)).url ),
                                                 SearchTerm("timeline",            r.name, deployRoutes.DeploymentTimelineController.graph(Some(ServiceName(r.name))).url),
                                                 SearchTerm("commissioning state", r.name, commissioningRoutes.ServiceCommissioningStatusController.getCommissioningState(ServiceName(r.name)).url)
                                            )
                                      )
      comments        <- prCommenterConnector.search(None, None, None)
      commentLinks    =  comments.flatMap(x => List(SearchTerm(s"recommendations", x.name,  prcommenterRoutes.PrCommenterController.recommendations(name = Some(x.name)).url, 0.5f)))
      users           <- userManagementConnector.getAllUsers(None)
      userLinks       =  users.map(u => SearchTerm("users", u.username.asString, userRoutes.UsersController.user(u.username).url, 0.5f))
      allLinks        =  hardcodedLinks ++ teamPageLinks ++ digitalLinks ++ repoLinks ++ serviceLinks ++ commentLinks ++ userLinks
    yield cachedIndex.set(optimizeIndex(allLinks))

  def search(query: Seq[String]): Seq[SearchTerm] =
    SearchIndex.search(query, cachedIndex.get())

end SearchIndex

object SearchIndex:

  def normalizeTerm(term: String): String =
    term.toLowerCase.replaceAll(" -_", "")

  private[search] def repoTypeString(repoType: RepoType): String =
    repoType match
      case RepoType.Other => "Repository"
      case _              => repoType.asString

  private[search] def search(
    query: Seq[String],
    index: Map[String, Seq[SearchTerm]]
  ): Seq[SearchTerm] =
    val normalised = query.map(normalizeTerm)

    normalised
      .foldLeft(index.getOrElse(normalised.head.slice(0,3), Seq.empty)): (acc, cur) =>
        acc.filter(_.terms.exists(_.contains(cur)))
      .map: st =>
        if normalised.exists(_.equalsIgnoreCase(st.name))
        then st.copy(weight = 1f)  //Increase weighting of an exact match
        else st
      .sortBy(st => -st.weight -> st.name.toLowerCase)
      .distinct

  def optimizeIndex(index: Seq[SearchTerm]): Map[String, Seq[SearchTerm]] =
    index
      .flatMap: st =>
        (st.linkType.sliding(3, 1) ++ st.name.sliding(3, 1) ++ st.hints.mkString.sliding(3, 1))
          .map(_.toLowerCase -> st)
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2))
      .toMap

end SearchIndex
