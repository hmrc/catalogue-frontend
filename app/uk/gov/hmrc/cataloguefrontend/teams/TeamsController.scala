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
import scala.concurrent.ExecutionContext

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

  def team(teamName: TeamName): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      teamsAndRepositoriesConnector.repositoriesForTeam(teamName, Some(false)).flatMap {
        case Nil   => for {
          maybeTeam   <- userManagementConnector.getTeam(teamName.asString)
        } yield Ok( teamInfoPage(
            teamName           = teamName,
            repos              = Map.empty,
            maybeTeam          = maybeTeam,
            umpMyTeamsUrl      = "",
            leaksFoundForTeam  = false,
            hasLeaks           = _ => false,
            Seq.empty,
            Map.empty
          ))
        case repos =>
          (
            userManagementConnector.getTeam(teamName.asString),
            leakDetectionService.repositoriesWithLeaks,
            serviceDependenciesConnector.dependenciesForTeam(teamName),
            serviceDependenciesConnector.getCuratedSlugDependenciesForTeam(teamName, SlugInfoFlag.ForEnvironment(Environment.Production)),
            ).mapN { ( maybeTeam,
                       reposWithLeaks,
                       masterTeamDependencies,
                       prodDependencies,
                     ) =>
            Ok(
              teamInfoPage(
                teamName               = teamName,
                repos                  = repos.groupBy(_.repoType),
                maybeTeam              = maybeTeam,
                umpMyTeamsUrl          = umpConfig.umpMyTeamsPageUrl(teamName),
                leaksFoundForTeam      = repos.exists(r => leakDetectionService.hasLeaks(reposWithLeaks)(r.name)),
                hasLeaks               = leakDetectionService.hasLeaks(reposWithLeaks),
                masterTeamDependencies = masterTeamDependencies.flatMap(mtd => repos.find(_.name == mtd.repositoryName).map(gr => RepoAndDependencies(gr, mtd))),
                prodDependencies       = prodDependencies
              )
            )
          }
      }
    }

  def allTeams(name: Option[String]): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      teamsAndRepositoriesConnector.allTeams().map(response =>
        Ok(teams_list(response, name))
      )
    }

  def outOfDateTeamDependencies(teamName: TeamName): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      (
        teamsAndRepositoriesConnector.repositoriesForTeam(teamName, Some(false)),
        serviceDependenciesConnector.dependenciesForTeam(teamName),
        serviceDependenciesConnector.getCuratedSlugDependenciesForTeam(teamName, SlugInfoFlag.ForEnvironment(Environment.Production))
      ).mapN { (repos, masterTeamDependencies, prodDependencies) =>
        val repoLookup = repos.map(r => r.name -> r).toMap
        Ok(outOfDateTeamDependenciesPage(
          teamName,
          masterTeamDependencies.flatMap(mtd => repoLookup.get(mtd.repositoryName).map(gr => RepoAndDependencies(gr, mtd))),
          prodDependencies
        ))
      }
    }
}
