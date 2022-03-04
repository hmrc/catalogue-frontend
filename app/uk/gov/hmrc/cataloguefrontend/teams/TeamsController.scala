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

package uk.gov.hmrc.cataloguefrontend.teams

import cats.implicits._
import play.api.Configuration
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.{TeamMember, UMPError}
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.connector.{ServiceDependenciesConnector, TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.leakdetection.LeakDetectionService
import uk.gov.hmrc.cataloguefrontend.model.{Environment, SlugInfoFlag}
import uk.gov.hmrc.cataloguefrontend.{DisplayableTeamMember, DisplayableTeamMembers, UserManagementPortalConfig}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.{TeamInfoPage, error_404_template, teams_list}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamsController @Inject()(  userManagementConnector      : UserManagementConnector,
                                  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
                                  serviceDependenciesConnector : ServiceDependenciesConnector,
                                  leakDetectionService         : LeakDetectionService,
                                  umpConfig                    : UserManagementPortalConfig,
                                  configuration                : Configuration,
                                  teamInfoPage                 : TeamInfoPage,
                                  mcc                          : MessagesControllerComponents
                               ) (implicit val ec: ExecutionContext)
  extends FrontendController(mcc) {

  private lazy val hideArchivedRepositoriesFromTeam: Boolean =
    configuration.get[Boolean]("team.hideArchivedRepositories")

  def team(teamName: TeamName): Action[AnyContent] =
    Action.async { implicit request =>
      teamsAndRepositoriesConnector.repositoriesForTeam(teamName).flatMap {
        case Nil   => Future.successful(notFound)
        case repos =>
          (
            userManagementConnector.getTeamMembersFromUMP(teamName),
            userManagementConnector.getTeamDetails(teamName),
            leakDetectionService.repositoriesWithLeaks,
            serviceDependenciesConnector.dependenciesForTeam(teamName),
            serviceDependenciesConnector.getCuratedSlugDependenciesForTeam(teamName, SlugInfoFlag.ForEnvironment(Environment.Production)),
            if (hideArchivedRepositoriesFromTeam)
              teamsAndRepositoriesConnector.archivedRepositories.map(_.map(_.name))
            else
              Future.successful(Nil)
            ).mapN { ( teamMembers,
                       teamDetails,
                       reposWithLeaks,
                       masterTeamDependencies,
                       prodDependencies,
                       reposToHide
                     ) =>
            Ok(
              teamInfoPage(
                teamName               = teamName,
                repos                  = repos.groupBy(_.repoType).mapValues(_.map(_.name)),
                errorOrTeamMembers     = convertToDisplayableTeamMembers(teamName, teamMembers),
                errorOrTeamDetails     = teamDetails,
                umpMyTeamsUrl          = umpConfig.umpMyTeamsPageUrl(teamName),
                leaksFoundForTeam      = repos.exists(r => leakDetectionService.hasLeaks(reposWithLeaks)(r.name)),
                hasLeaks               = leakDetectionService.hasLeaks(reposWithLeaks),
                masterTeamDependencies = masterTeamDependencies.filterNot(repo => reposToHide.contains(repo.repositoryName)),
                prodDependencies       = prodDependencies
              )
            )
          }

      }
    }

  def allTeams(): Action[AnyContent] =
    Action.async { implicit request =>

      teamsAndRepositoriesConnector.allTeams.map { response =>
          Ok(teams_list(response))
      }
    }


  private def convertToDisplayableTeamMembers(teamName: TeamName,
                                              errorOrTeamMembers: Either[UMPError, Seq[TeamMember]]
                                             ): Either[UMPError, Seq[DisplayableTeamMember]] =
    errorOrTeamMembers match {
      case Left(err) => Left(err)
      case Right(tms) =>
        Right(DisplayableTeamMembers(teamName, umpConfig.userManagementProfileBaseUrl, tms))
    }

  private def notFound(implicit request: Request[_], messages: Messages) = NotFound(error_404_template())

}
