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

package uk.gov.hmrc.cataloguefrontend.teams

import cats.implicits._
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.connector.{ServiceDependenciesConnector, TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.leakdetection.LeakDetectionService
import uk.gov.hmrc.cataloguefrontend.model.{Environment, SlugInfoFlag}
import uk.gov.hmrc.cataloguefrontend.config.UserManagementPortalConfig
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.teams.{TeamInfoPage, teams_list}
import views.html.OutOfDateTeamDependenciesPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamsController @Inject()(
  userManagementConnector      : UserManagementConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  serviceDependenciesConnector : ServiceDependenciesConnector,
  leakDetectionService         : LeakDetectionService,
  umpConfig                    : UserManagementPortalConfig,
  teamInfoPage                 : TeamInfoPage,
  outOfDateTeamDependenciesPage: OutOfDateTeamDependenciesPage,
  override val mcc             : MessagesControllerComponents,
  override val auth            : FrontendAuthComponents
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  def team(teamName: String): Action[AnyContent] =
    team2(TeamName(teamName))

  def team2(teamName: TeamName): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      ( userManagementConnector.getTeam(teamName)
      , teamsAndRepositoriesConnector.allTeams()
      ).mapN {
        ( umpTeam
        , githubTeams
        ) =>
          val optGithubTeam = githubTeams.find(_.name == umpTeam.teamName)
          optGithubTeam match {
            case Some(githubTeam) =>
              ( teamsAndRepositoriesConnector.repositoriesForTeam(teamName, Some(false))
              , leakDetectionService.repositoriesWithLeaks
              , serviceDependenciesConnector.dependenciesForTeam(teamName)
              , serviceDependenciesConnector.getCuratedSlugDependenciesForTeam(teamName, SlugInfoFlag.ForEnvironment(Environment.Production))
              ).mapN {
                ( repos
                , reposWithLeaks
                , masterTeamDependencies
                , prodDependencies
                ) =>
                  Ok(
                    teamInfoPage(
                      teamName               = teamName
                    , repos                  = repos.groupBy(_.repoType)
                    , umpTeam                = umpTeam
                    , umpMyTeamsUrl          = umpConfig.umpMyTeamsPageUrl(teamName)
                    , leaksFoundForTeam      = repos.exists(r => leakDetectionService.hasLeaks(reposWithLeaks)(r.name))
                    , hasLeaks               = leakDetectionService.hasLeaks(reposWithLeaks)
                    , masterTeamDependencies = masterTeamDependencies.flatMap(mtd => repos.find(_.name == mtd.repositoryName).map(gr => RepoAndDependencies(gr, mtd)))
                    , prodDependencies       = prodDependencies
                    , gitHubUrl              = Some(githubTeam.githubUrl)
                    )
                  )
              }
            case _ =>
              Future.successful(
                Ok(
                  teamInfoPage(
                    teamName               = teamName
                  , repos                  = Map.empty
                  , umpTeam                = umpTeam
                  , umpMyTeamsUrl          = umpConfig.umpMyTeamsPageUrl(teamName)
                  , leaksFoundForTeam      = false
                  , hasLeaks               = _ => false
                  , masterTeamDependencies = Seq.empty
                  , prodDependencies       = Map.empty
                  , gitHubUrl              = None
                  )
                )
              )
          }
      }.flatten
    }

  def allTeams(name: Option[String]): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      ( userManagementConnector.getAllTeams()
      , teamsAndRepositoriesConnector.allTeams()
      ).mapN { (umpTeams, gitHubTeams) =>
        Ok(teams_list(
          teams = umpTeams.map(umpTeam => (umpTeam, gitHubTeams.find(_.name == umpTeam.teamName))),
          name
        ))
      }
    }

  def outOfDateTeamDependencies(teamName: String): Action[AnyContent] =
    outOfDateTeamDependencies2(TeamName(teamName))

  def outOfDateTeamDependencies2(teamName: TeamName): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      ( teamsAndRepositoriesConnector.repositoriesForTeam(teamName, Some(false))
      , serviceDependenciesConnector.dependenciesForTeam(teamName)
      , serviceDependenciesConnector.getCuratedSlugDependenciesForTeam(teamName, SlugInfoFlag.ForEnvironment(Environment.Production))
      ).mapN {
        ( repos
        , masterTeamDependencies
        , prodDependencies
        ) =>
        val repoLookup = repos.map(r => r.name -> r).toMap
        Ok(outOfDateTeamDependenciesPage(
          teamName,
          masterTeamDependencies.flatMap(mtd => repoLookup.get(mtd.repositoryName).map(gr => RepoAndDependencies(gr, mtd))),
          prodDependencies
        ))
      }
    }
}
