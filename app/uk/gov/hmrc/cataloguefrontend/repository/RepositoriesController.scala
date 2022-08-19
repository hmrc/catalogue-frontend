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

package uk.gov.hmrc.cataloguefrontend.repository

import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.repository
import uk.gov.hmrc.cataloguefrontend.repository.RepositoriesController.teamsAndReposTypeMapping
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
  override val auth            : FrontendAuthComponents
 )(implicit
   override val ec: ExecutionContext
) extends FrontendController(mcc)
  with CatalogueAuthBuilders {

  def allRepositories(name: Option[String] = None, team: Option[String] = None, archived: Option[Boolean] = None, repoType: Option[String] = None): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      val allTeams =
        teamsAndRepositoriesConnector
          .allTeams
          .map(_.sortBy(_.name.asString))

      val (teamsAndReposRepoType, teamsAndReposServiceType) = teamsAndReposTypeMapping(repoType)

      val allRepositories =
        teamsAndRepositoriesConnector
          .allRepositories(name.filterNot(_.isEmpty), team.filterNot(_.isEmpty), archived, teamsAndReposRepoType, teamsAndReposServiceType)
          .map(_.sortBy(_.name.toLowerCase))

      for {
        teams        <- allTeams
        repositories <- allRepositories
      } yield Ok(repositoriesListPage(repositories, teams, RepoListFilter.form.bindFromRequest())
      )}


  def allServices: Action[AnyContent] =
    Action {
      Redirect(
        repository.routes.RepositoriesController.allRepositories(repoType = Some(RepoType.Service.asString)))
    }

  def allLibraries: Action[AnyContent] =
    Action {
      Redirect(repository.routes.RepositoriesController.allRepositories(repoType = Some(RepoType.Library.asString)))
    }

  def allPrototypes: Action[AnyContent] =
    Action {
      Redirect(repository.routes.RepositoriesController.allRepositories(repoType = Some(RepoType.Prototype.asString)))
    }
}

object RepositoriesController {

  def teamsAndReposTypeMapping(repoType: Option[String]): (Option[String], Option[String]) = {
    repoType match {
      case None                     => (None, None)
      case Some("Service")          => (Some("Service"), None)
      case Some("FrontendService")  => (Some("Service"), Some("FrontendService"))
      case Some("BackendService")   => (Some("Service"), Some("BackendService"))
      case Some("Library")          => (Some("Library"), None)
      case Some("Prototype")        => (Some("Prototype"), None)
      case Some("Other")            => (Some("Other"), None)
      case _                        => (None, None)
    }
  }
}

case class RepoListFilter(
                           name: Option[String] = None,
                           team: Option[String] = None,
                           repoType : Option[String] = None,
                         ) {
  def isEmpty: Boolean =
    name.isEmpty && team.isEmpty && repoType.isEmpty
}

object RepoListFilter {
  lazy val form = Form(
  mapping(
  "name"            -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
  "team"            -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
  "repoType"        -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity)
  )(RepoListFilter.apply)(RepoListFilter.unapply)
  )
}
