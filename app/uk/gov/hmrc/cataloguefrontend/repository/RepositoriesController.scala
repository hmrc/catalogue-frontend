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

package uk.gov.hmrc.cataloguefrontend.repository

import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, RepoType, ServiceType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.repository
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

  def allRepositories(
    name          : Option[String],
    team          : Option[TeamName],
    showArchived  : Boolean,
    repoTypeString: Option[String]
  ): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      val allTeams =
        teamsAndRepositoriesConnector
          .allTeams()
          .map(_.sortBy(_.name.asString))

      val (repoType, serviceType) = repoTypeString match {
        case Some("FrontendService")  => (Some(RepoType.Service), Some(ServiceType.Frontend))
        case Some("BackendService")   => (Some(RepoType.Service), Some(ServiceType.Backend))
        case Some(other)              => (RepoType.parse(other).toOption, None)
        case None                     => (None, None)
      }

      val allRepositories =
        teamsAndRepositoriesConnector
          .allRepositories(
            name        = None, // Use listjs filtering
            team        = team.filterNot(_.asString.isEmpty),
            archived    = if (showArchived) None else Some(false),
            repoType    = repoType,
            serviceType = serviceType
          ).map(_.sortBy(_.name.toLowerCase))

      for {
        teams        <- allTeams
        repositories <- allRepositories
      } yield {
        val filteredRepositoriesByStatus = filterRepositoriesByStatus(repositories, request.getQueryString("status"))
        Ok(repositoriesListPage(filteredRepositoriesByStatus, teams, RepoListFilter.form.bindFromRequest()))
      }
    }

  private def filterRepositoriesByStatus(repositories: Seq[GitRepository], status: Option[String]): Seq[GitRepository] = {
    status match {
      case Some("All") => repositories
      case Some("Archived") => repositories.filter(repo => repo.isArchived)
      case Some("Deprecated") => repositories.filter(repo => repo.isDeprecated)
      case _ => repositories.filterNot(repo => repo.isArchived || repo.isDeprecated) //Case for default "Active" status
    }
  }

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

case class RepoListFilter(
  name    : Option[String]   = None,
  team    : Option[TeamName] = None,
  repoType: Option[String]   = None,
  status  : Option[String]   = None
) {
  def isEmpty: Boolean =
    name.isEmpty && team.isEmpty && repoType.isEmpty
}

object RepoListFilter {
  lazy val form =
    Form(
      mapping(
        "name"            -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
        "team"            -> optional(text).transform[Option[TeamName]](_.filter(_.trim.nonEmpty).map(TeamName.apply), _.map(_.asString)),
        "repoType"        -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity)
      )(RepoListFilter.apply)(RepoListFilter.unapply)
    )
}
