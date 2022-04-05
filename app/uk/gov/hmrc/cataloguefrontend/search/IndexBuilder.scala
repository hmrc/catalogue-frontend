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
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.cataloguefrontend.healthindicators.{routes => healthRoutes}
import uk.gov.hmrc.cataloguefrontend.leakdetection.{routes => leakRoutes}
import uk.gov.hmrc.cataloguefrontend.teams.{routes => teamRoutes}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{routes => wrwRoutes}
import uk.gov.hmrc.cataloguefrontend.{routes => catalogueRoutes}

import java.util.concurrent.atomic.AtomicReference

case class SearchTerm(name: String,  link: String, linkType: String) {
  lazy val term: String = IndexBuilder.normalizeTerm(name)
}

@Singleton
class IndexBuilder @Inject()(teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector)(implicit ec: ExecutionContext){

  val cachedIndex = new AtomicReference[Seq[SearchTerm]]()

  val hardcodedLinks = List(
    new SearchTerm("dependency explorer", catalogueRoutes.DependencyExplorerController.landing.url, "explorer") {
      override lazy val term: String = "depex"
    },
    SearchTerm("dependency explorer", catalogueRoutes.DependencyExplorerController.landing.url, "explorer"),
    SearchTerm("bobby", catalogueRoutes.BobbyExplorerController.list().url, "explorer"),
    SearchTerm("jvm",   catalogueRoutes.JDKVersionController.compareAllEnvironments.url, "explorer"),
    SearchTerm("leaks", leakRoutes.LeakDetectionController.ruleSummaries.url, "explorer"),
    SearchTerm("lds",   leakRoutes.LeakDetectionController.ruleSummaries.url, "explorer"),
  )

  def buildIndexes(): Future[List[SearchTerm]] = {
    implicit val hc = HeaderCarrier()
    for {
      repos         <- teamsAndRepositoriesConnector.allRepositories
      teams         <- teamsAndRepositoriesConnector.allTeams
      teamPageLinks =  teams.flatMap(t => List(SearchTerm(t.name.asString, teamRoutes.TeamsController.team(t.name).url, "teams"),
                                               SearchTerm(t.name.asString, s"${wrwRoutes.WhatsRunningWhereController.releases(false).url}?profile_type=team&profile_name=${t.name.asString}".toString, "deployments")))
      repoLinks     =  repos.flatMap(r => List(SearchTerm(r.name, catalogueRoutes.CatalogueController.repository(r.name).url, "repo"),
                                               SearchTerm(r.name, healthRoutes.HealthIndicatorsController.breakdownForRepo(r.name).url, "health"),
                                               SearchTerm(r.name, leakRoutes.LeakDetectionController.report(r.name, r.defaultBranch).url,"leaks")))
      serviceLinks  =  repos.filter(_.repoType == RepoType.Service).map(r => SearchTerm(r.name, catalogueRoutes.CatalogueController.serviceConfig(r.name).url, "config"))
    } yield hardcodedLinks ++ teamPageLinks ++ repoLinks ++ serviceLinks
  }

  def getIndex(): Seq[SearchTerm] =
      cachedIndex.get()
}

object IndexBuilder {

  def normalizeTerm(term: String): String = {
    term.toLowerCase.replaceAll(" -_", "")
  }

  def search(query: String, index: Seq[SearchTerm]): Seq[SearchTerm] = {
      val searchTerms  = query.split(" ", 2).filter(_.nonEmpty)
      val termFilter   = searchTerms.headOption.map(IndexBuilder.normalizeTerm).getOrElse("")
      val kindFilter   = searchTerms.tail.lastOption.map(_.toLowerCase)
      val matches      = index.filter(st => st.term.contains(termFilter))
      kindFilter.map(kf => matches.filter(_.linkType.toLowerCase.contains(kf))).getOrElse(matches)
  }

}