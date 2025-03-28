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

import play.api.data.{Form, Forms}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, ServiceType, TeamsAndRepositoriesConnector, given}
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, TeamName}
import uk.gov.hmrc.cataloguefrontend.repository.{routes => repositoryRoutes}
import uk.gov.hmrc.cataloguefrontend.repository.view.html.RepositoriesListPage
import uk.gov.hmrc.cataloguefrontend.util.Parser
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class RepositoriesController @Inject() (
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  override val mcc             : MessagesControllerComponents,
  repositoriesListPage         : RepositoriesListPage,
  override val auth            : FrontendAuthComponents
 )(using
   override val ec: ExecutionContext
) extends FrontendController(mcc)
  with CatalogueAuthBuilders:

  def allRepositories(
    name          : Option[String],
    team          : Option[TeamName],
    digitalService: Option[DigitalService],
    showArchived  : Option[Boolean],
    repoTypeString: Option[String]
  ): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      val allTeams =
        teamsAndRepositoriesConnector
          .allTeams()
          .map(_.sortBy(_.name))

      val (repoType, serviceType) = repoTypeString match
        case Some("FrontendService")  => (Some(RepoType.Service)        , Some(ServiceType.Frontend))
        case Some("BackendService")   => (Some(RepoType.Service)        , Some(ServiceType.Backend))
        case Some(other)              => ( Parser[RepoType].parse(other).toOption
                                         , None
                                         )
        case None                     => (None                          , None)

      val allRepositories =
        teamsAndRepositoriesConnector
          .allRepositories(
            name           = None, // Use listjs filtering
            team           = team.filterNot(_.asString.isEmpty),
            digitalService = digitalService.filterNot(_.asString.isEmpty),
            archived       = if showArchived.contains(true) then None else Some(false),
            repoType       = repoType,
            serviceType    = serviceType
          ).map(_.sortBy(_.name.toLowerCase))

      val allDigitalServices =
        teamsAndRepositoriesConnector
          .allDigitalServices()

      for
        teams           <- allTeams
        repositories    <- allRepositories
        digitalServices <- allDigitalServices
      yield Ok(repositoriesListPage(repositories, teams, digitalServices, RepoListFilter.form.bindFromRequest()))
    }

  def allServices: Action[AnyContent] =
    Action:
      Redirect:
        repositoryRoutes.RepositoriesController.allRepositories(repoType = Some(RepoType.Service.asString))

  def allLibraries: Action[AnyContent] =
    Action:
      Redirect(repositoryRoutes.RepositoriesController.allRepositories(repoType = Some(RepoType.Library.asString)))

  def allPrototypes: Action[AnyContent] =
    Action:
      Redirect(repositoryRoutes.RepositoriesController.allRepositories(repoType = Some(RepoType.Prototype.asString)))

case class RepoListFilter(
  name          : Option[String]         = None,
  team          : Option[TeamName]       = None,
  digitalService: Option[DigitalService] = None,
  repoType      : Option[String]         = None,
  showArchived  : Option[Boolean]        = None
):
  def isEmpty: Boolean =
    name.isEmpty && team.isEmpty && digitalService.isEmpty && repoType.isEmpty

object RepoListFilter {
  lazy val form =
    Form(
      Forms.mapping(
        "name"           -> Forms.optional(Forms.text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
        "team"           -> Forms.optional(Forms.of[TeamName]),
        "digitalService" -> Forms.optional(Forms.of[DigitalService]).transform(_.filter(_.asString.trim.nonEmpty), identity),
        "repoType"       -> Forms.optional(Forms.text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity),
        "showArchived"   -> Forms.optional(Forms.boolean)
      )(RepoListFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
    )
}
