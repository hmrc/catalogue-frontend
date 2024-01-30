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


import uk.gov.hmrc.cataloguefrontend.connector.model.{Log, UserLog}
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.healthindicators.{routes => healthRoutes}
import uk.gov.hmrc.cataloguefrontend.leakdetection.{routes => leakRoutes}
import uk.gov.hmrc.cataloguefrontend.repository.{routes => reposRoutes}
import uk.gov.hmrc.cataloguefrontend.prcommenter.{PrCommenterConnector, routes => prcommenterRoutes}
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{routes => commissioningRoutes}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.search.SearchIndex.{normalizeTerm, optimizeIndex}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.{routes => serviceConfigsRoutes}
import uk.gov.hmrc.cataloguefrontend.teams.{routes => teamRoutes}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{routes => wrwRoutes}
import uk.gov.hmrc.cataloguefrontend.deployments.{routes => depRoutes}
import uk.gov.hmrc.cataloguefrontend.{routes => catalogueRoutes}
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterType, routes => shutterRoutes}
import uk.gov.hmrc.cataloguefrontend.users.{routes => userRoutes}
import uk.gov.hmrc.cataloguefrontend.createrepository.{routes => createRepoRoutes}
import uk.gov.hmrc.http.HeaderCarrier

import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class SearchTerm(
  linkType: String,
  name    : String,
  link    : String,
  weight  : Float       = 0.5f,
  hints   : Set[String] = Set.empty
) {
  lazy val terms: Set[String] =
    Set(name, linkType).union(hints).map(normalizeTerm)
}

@Singleton
class SearchIndex @Inject()(
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  prCommenterConnector         : PrCommenterConnector,
  userManagementConnector      : UserManagementConnector
)(implicit ec: ExecutionContext) {

  private[search] val cachedIndex = new AtomicReference[Map[String, Seq[SearchTerm]]](Map.empty)

  private val hardcodedLinks = List(
    SearchTerm("explorer", "dependency",                   catalogueRoutes.DependencyExplorerController.landing.url,                              1.0f, Set("depex")),
    SearchTerm("explorer", "bobby",                        catalogueRoutes.BobbyExplorerController.list().url,                                    1.0f),
    SearchTerm("explorer", "jvm",                          catalogueRoutes.JDKVersionController.compareAllEnvironments().url,                     1.0f, Set("jdk", "jre")),
    SearchTerm("explorer", "leaks",                        leakRoutes.LeakDetectionController.ruleSummaries.url,                                  1.0f, Set("lds")),
    SearchTerm("page",     "whatsrunningwhere",            wrwRoutes.WhatsRunningWhereController.releases().url,                                  1.0f, Set("wrw")),
    SearchTerm("page",     "deployment",                   depRoutes.DeploymentEventsController.deploymentEvents(Environment.Production).url,     1.0f),
    SearchTerm("page",     "shutter-overview",             shutterRoutes.ShutterOverviewController.allStates(ShutterType.Frontend).url,           1.0f),
    SearchTerm("page",     "shutter-api",                  shutterRoutes.ShutterOverviewController.allStates(ShutterType.Api).url,                1.0f),
    SearchTerm("page",     "shutter-rate",                 shutterRoutes.ShutterOverviewController.allStates(ShutterType.Rate).url,               1.0f),
    SearchTerm("page",     "shutter-events",               shutterRoutes.ShutterEventsController.shutterEvents.url,                               1.0f),
    SearchTerm("page",     "teams",                        teamRoutes.TeamsController.allTeams().url,                                             1.0f),
    SearchTerm("page",     "repositories",                 reposRoutes.RepositoriesController.allRepositories().url,                              1.0f),
    SearchTerm("page",     "users",                        userRoutes.UsersController.allUsers().url,                                             1.0f),
    SearchTerm("page",     "defaultbranch",                catalogueRoutes.CatalogueController.allDefaultBranches().url,                          1.0f),
    SearchTerm("page",     "pr-commenter-recommendations", prcommenterRoutes.PrCommenterController.recommendations().url,                         1.0f),
    SearchTerm("page",     "search config",                serviceConfigsRoutes.ServiceConfigsController.searchLanding().url,                     1.0f),
    SearchTerm("page",     "config warnings",              serviceConfigsRoutes.ServiceConfigsController.configWarningLanding().url,              1.0f),
    SearchTerm("page",     "create service repository",    createRepoRoutes.CreateRepositoryController.createServiceRepositoryLanding().url,      1.0f),
    SearchTerm("page",     "create prototype repository",  createRepoRoutes.CreateRepositoryController.createPrototypeRepositoryLanding().url,    1.0f),
    SearchTerm("page",     "create test repository",       createRepoRoutes.CreateRepositoryController.createTestRepository().url,                1.0f),
    SearchTerm("page",     "deploy service",               depRoutes.DeployServiceController.step1(None).url,                                     1.0f),
    SearchTerm("page",     "search commissioning state",   commissioningRoutes.ServiceCommissioningStatusController.searchLanding().url,          1.0f)
  )

  def updateIndexes(): Future[Unit] = {
    implicit val hc = HeaderCarrier()
    for {
      repos         <- teamsAndRepositoriesConnector.allRepositories(None, None, None, None, None)
      teams         <- teamsAndRepositoriesConnector.allTeams()
      teamPageLinks =  teams.flatMap(t => List(SearchTerm("teams",       t.name.asString, teamRoutes.TeamsController.team(t.name).url, 0.5f),
                                               SearchTerm("deployments", t.name.asString, s"${wrwRoutes.WhatsRunningWhereController.releases().url}?profile_type=team&profile_name=${URLEncoder.encode(t.name.asString, "UTF-8")}")))
      repoLinks     =  repos.flatMap(r => List(SearchTerm(r.repoType.asString,    r.name, catalogueRoutes.CatalogueController.repository(r.name).url, 0.5f, Set("repository")),
                                               SearchTerm("health",      r.name,          healthRoutes.HealthIndicatorsController.breakdownForRepo(r.name).url),
                                               SearchTerm("leak",        r.name,          leakRoutes.LeakDetectionController.branchSummaries(r.name).url, 0.5f)))
      serviceLinks  =  repos.filter(_.repoType == RepoType.Service)
                            .flatMap(r => List(SearchTerm("deploy",              r.name, depRoutes.DeployServiceController.step1(Some(r.name)).url),
                                               SearchTerm("config",              r.name, serviceConfigsRoutes.ServiceConfigsController.configExplorer(r.name).url ),
                                               SearchTerm("timeline",            r.name, depRoutes.DeploymentTimelineController.graph(r.name).url),
                                               SearchTerm("commissioning state", r.name, commissioningRoutes.ServiceCommissioningStatusController.getCommissioningState(r.name).url)
                                          )
                                    )
      comments      <- prCommenterConnector.search(None, None, None)
      commentLinks  =  comments.flatMap(x => List(SearchTerm(s"recommendations", x.name,  prcommenterRoutes.PrCommenterController.recommendations(name = Some(x.name)).url, 0.5f)))
      users         <- userManagementConnector.getAllUsers(None)
      userLinks     =  users.map(u => SearchTerm("users", u.username, userRoutes.UsersController.user(u.username).url, 0.5f))
      allLinks      =  hardcodedLinks ++ teamPageLinks ++ repoLinks ++ serviceLinks ++ commentLinks ++ userLinks
      sortedLinks   = allLinks.sortWith(_.link.length > _.link.length) //sort needed to efficiently search URIs
    } yield cachedIndex.set(optimizeIndex(sortedLinks))
  }

  def search(query: Seq[String]): Seq[SearchTerm] =
    SearchIndex.search(query, cachedIndex.get())
    
  def searchByUserLogs(userLog: UserLog) : Seq[SearchTerm] =
    SearchIndex.searchURIs(userLog.logs, cachedIndex.get().values.flatten.toSeq)
}

object SearchIndex {
  def normalizeTerm(term: String): String =
    term.toLowerCase.replaceAll(" -_", "")

  // TODO: we could cache the results short term, generally the next query will be the previous query + 1 letter
  //       so we can reuse the partial result set
  private[search] def search(query: Seq[String], index: Map[String, Seq[SearchTerm]]): Seq[SearchTerm] = {
    val normalised = query.map(normalizeTerm)
    normalised
      .foldLeft(index.getOrElse(normalised.head.slice(0,3), Seq.empty)) {
        (acc, cur) => acc.filter(_.terms.exists(_.contains(cur)))
      }.map(st => if(normalised.exists(_.equalsIgnoreCase(st.name))) st.copy(weight = 1f) else st) //Increase weighting of an exact match
      .sortBy(st => -st.weight ->  st.name.toLowerCase)
      .distinct
  }

  def optimizeIndex(index: Seq[SearchTerm]): Map[String, Seq[SearchTerm]] =
    index.flatMap(st => (st.linkType.sliding(3,1) ++ st.name.sliding(3,1) ++ st.hints.mkString.sliding(3,1))
      .map(_.toLowerCase() -> st))
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2))
      .toMap
  
  def searchURIs(logs: Seq[Log], index: Seq[SearchTerm]): Seq[SearchTerm] = {
    //unlike search() .weight in this search represents visit counter instead of quality of match
    val filteredLogs = logs.filter(log => index.exists(searchTerm => log.uri.contains(searchTerm.link))) //remove logs not in index
    val weightedSearchTerms = for {
      log <- filteredLogs
      matchedTerm <- index.find(searchTerm => log.uri.contains(searchTerm.link))
      weightedSearchTerm = SearchTerm(
        linkType = matchedTerm.linkType
      , name     = matchedTerm.name
      , link     = matchedTerm.link
      , weight   = log.count  //insert visit counter
      )
    } yield (weightedSearchTerm)
    val distinctLinkSearchTerms = weightedSearchTerms.distinctBy(_.link) //group search terms with same links, weighting at this stage is incorrect
    val reWeightedSearchTerms = for {
      distinctTerm <- distinctLinkSearchTerms
      summedWeight = weightedSearchTerms.filter(_.link.equals(distinctTerm.link)).foldLeft(0)(_ + _.weight.toInt) //find total weight for distinct terms
      reWeightedSearchTerm = SearchTerm(
        linkType = distinctTerm.linkType
      , name     = distinctTerm.name
      , link     = distinctTerm.link
      , weight   = summedWeight  //insert total weight
      )
    } yield(reWeightedSearchTerm)
    reWeightedSearchTerms.sortWith(_.weight > _.weight).take(20)
  }
}
