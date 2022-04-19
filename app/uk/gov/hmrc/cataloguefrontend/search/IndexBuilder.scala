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

package uk.gov.hmrc.cataloguefrontend.search


import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.healthindicators.{routes => healthRoutes}
import uk.gov.hmrc.cataloguefrontend.leakdetection.{routes => leakRoutes}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.search.IndexBuilder.normalizeTerm
import uk.gov.hmrc.cataloguefrontend.teams.{routes => teamRoutes}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{routes => wrwRoutes}
import uk.gov.hmrc.cataloguefrontend.{routes => catalogueRoutes}
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterType, routes => shutterRoutes}
import uk.gov.hmrc.http.HeaderCarrier

import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class SearchTerm(linkType: String, name: String, link: String, weight: Float = 0.5f, hints: Set[String] = Set.empty) {
  lazy val terms: Set[String] = Set(name, linkType).union(hints).map(normalizeTerm)
}

@Singleton
class IndexBuilder @Inject()(teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector)(implicit ec: ExecutionContext){

  protected[search] val cachedIndex = new AtomicReference[Seq[SearchTerm]](Seq.empty)

  private val hardcodedLinks = List(
    SearchTerm("explorer", "dependency",        catalogueRoutes.DependencyExplorerController.landing.url,                    1.0f, Set("depex")),
    SearchTerm("explorer", "bobby",             catalogueRoutes.BobbyExplorerController.list().url,                          1.0f),
    SearchTerm("explorer", "jvm",               catalogueRoutes.JDKVersionController.compareAllEnvironments.url,             1.0f, Set("jdk", "jre")),
    SearchTerm("explorer", "leaks",             leakRoutes.LeakDetectionController.ruleSummaries.url,                        1.0f, Set("lds")),
    SearchTerm("page",     "whatsrunningwhere", wrwRoutes.WhatsRunningWhereController.releases().url,                        1.0f, Set("wrw")),
    SearchTerm("page",     "deployment",        wrwRoutes.DeploymentHistoryController.history(Environment.Production).url,   1.0f),
    SearchTerm("page",     "shutter-overview",  shutterRoutes.ShutterOverviewController.allStates(ShutterType.Frontend).url, 1.0f),
    SearchTerm("page",     "shutter-api",       shutterRoutes.ShutterOverviewController.allStates(ShutterType.Api).url,      1.0f),
    SearchTerm("page",     "shutter-rate",      shutterRoutes.ShutterOverviewController.allStates(ShutterType.Rate).url,     1.0f),
    SearchTerm("page",     "shutter-events",    shutterRoutes.ShutterEventsController.shutterEvents.url,                     1.0f),
    SearchTerm("page",     "teams",             teamRoutes.TeamsController.allTeams.url,                                     1.0f),
    SearchTerm("page",     "repositories",      catalogueRoutes.CatalogueController.allRepositories().url,                   1.0f),
    SearchTerm("page",     "defaultbranch",     catalogueRoutes.CatalogueController.allDefaultBranches().url,                1.0f),
  )

  def buildIndexes(): Future[List[SearchTerm]] = {
    implicit val hc = HeaderCarrier()
    for {
      repos         <- teamsAndRepositoriesConnector.allRepositories
      teams         <- teamsAndRepositoriesConnector.allTeams
      teamPageLinks =  teams.flatMap(t => List(SearchTerm("teams",       t.name.asString, teamRoutes.TeamsController.team(t.name).url, 0.5f),
                                               SearchTerm("deployments", t.name.asString, s"${wrwRoutes.WhatsRunningWhereController.releases(false).url}?profile_type=team&profile_name=${URLEncoder.encode(t.name.asString, "UTF-8")}")))
      repoLinks     =  repos.flatMap(r => List(SearchTerm(r.repoType.asString,    r.name, catalogueRoutes.CatalogueController.repository(r.name).url, 0.5f, Set("repository")),
                                               SearchTerm("health",      r.name,          healthRoutes.HealthIndicatorsController.breakdownForRepo(r.name).url),
                                               SearchTerm("leak",        r.name,          leakRoutes.LeakDetectionController.branchSummaries(r.name).url, 0.5f)))
      serviceLinks  =  repos.filter(_.repoType == RepoType.Service)
                            .map(r =>          SearchTerm("config",      r.name,          catalogueRoutes.CatalogueController.serviceConfig(r.name).url ))
      allLinks = hardcodedLinks ++ teamPageLinks ++ repoLinks ++ serviceLinks
    } yield allLinks
  }

  def getIndex(): Seq[SearchTerm] =
      cachedIndex.get()
}

object IndexBuilder {

  def normalizeTerm(term: String): String = {
    term.toLowerCase.replaceAll(" -_", "")
  }

  // TODO: we could cache the results short term, generally the next query will be the previous query + 1 letter
  //       so we can reuse the partial result set
  def search(query: Seq[String], index: Seq[SearchTerm]): Seq[SearchTerm] = {
      query
        .map(normalizeTerm)
        .foldLeft(index) {
          case (acc, cur) => acc.filter(_.terms.exists(_.contains(cur)))
        }.sortWith( (a,b) => a.weight > b.weight )
  }

}