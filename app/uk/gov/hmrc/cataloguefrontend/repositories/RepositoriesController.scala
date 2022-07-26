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

package uk.gov.hmrc.cataloguefrontend.repositories

import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.SearchFiltering
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.RepositoriesListPage
import views.html.partials.RepoSearchResultsPage

import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

@Singleton
class RepositoriesController @Inject() (
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  override val mcc             : MessagesControllerComponents,
  repositoriesListPage         : RepositoriesListPage,
  repositoriesSearchResultsPage: RepoSearchResultsPage,
  override val auth            : FrontendAuthComponents,
  repositoriesSearchCache      : RepositoriesSearchCache
 )(implicit
   override val ec: ExecutionContext
) extends FrontendController(mcc)
  with CatalogueAuthBuilders {

  def allRepositories(name: Option[String], team: Option[String], repoType: Option[String]): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      import SearchFiltering._

      val allTeams = repositoriesSearchCache.getTeamsOrElseUpdate()
      val allRepositories = repositoriesSearchCache.getReposOrElseUpdate()

      for {
        teams        <- allTeams
        repositories <- allRepositories
      } yield
        Ok(
          repositoriesListPage(repositories = repositories.filter(RepoListFilter(name, team, repoType)), teams = teams, RepositoryType.values)
        )
    }

  def repositoriesSearch(name: Option[String], team: Option[String], repoType: Option[String], column: String, sortOrder: String) = {
    BasicAuthAction.async { implicit request =>
      import SearchFiltering._
      for {
        repos <- repositoriesSearchCache.getReposOrElseUpdate()
      } yield {
        val filtered = repos.filter(RepoListFilter(name, team, repoType))
        Ok(repositoriesSearchResultsPage(RepoSorter.sort(filtered, column, sortOrder)))
      }
    }
  }
}

case class RepoListFilter(
   name      : Option[String] = None,
   team      : Option[String] = None,
   repoType  : Option[String] = None
                         ) {
  def isEmpty: Boolean =
    name.isEmpty && team.isEmpty && repoType.isEmpty
}


