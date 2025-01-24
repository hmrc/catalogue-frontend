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

import cats.implicits.*
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, RequestHeader}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.config.UserManagementPortalConfig
import uk.gov.hmrc.cataloguefrontend.connector.{ServiceDependenciesConnector, TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.leakdetection.LeakDetectionService
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, SlugInfoFlag, TeamName}
import uk.gov.hmrc.cataloguefrontend.platforminitiatives.PlatformInitiativesConnector
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.ServiceCommissioningStatusConnector
import uk.gov.hmrc.cataloguefrontend.servicemetrics.ServiceMetricsConnector
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterService, ShutterType}
import uk.gov.hmrc.cataloguefrontend.teams.view.html.{TeamInfoOldPage, TeamInfoPage, TeamsListPage}
import uk.gov.hmrc.cataloguefrontend.vulnerabilities.VulnerabilitiesConnector
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{Profile, ProfileName, ProfileType, ReleasesConnector}
import uk.gov.hmrc.cataloguefrontend.view.html.OutOfDateTeamDependenciesPage
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamsController @Inject()(
  userManagementConnector            : UserManagementConnector
, teamsAndRepositoriesConnector      : TeamsAndRepositoriesConnector
, platformInitiativesConnector       : PlatformInitiativesConnector
, serviceDependenciesConnector       : ServiceDependenciesConnector
, serviceCommissioningStatusConnector: ServiceCommissioningStatusConnector
, serviceMetricsConnector            : ServiceMetricsConnector
, vulnerabilitiesConnector           : VulnerabilitiesConnector
, releasesConnector                  : ReleasesConnector
, leakDetectionService               : LeakDetectionService
, shutterService                     : ShutterService
, umpConfig                          : UserManagementPortalConfig
, teamInfoPage                       : TeamInfoPage
, teamInfoOldPage                    : TeamInfoOldPage
, outOfDateTeamDependenciesPage      : OutOfDateTeamDependenciesPage
, override val mcc                   : MessagesControllerComponents
, override val auth                  : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  def team(teamName: TeamName): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      if  uk.gov.hmrc.cataloguefrontend.CatalogueFrontendSwitches.showNewTeamPage.isEnabled then
        for
          results <- ( teamsAndRepositoriesConnector.allTeams(Some(teamName)).map(_.headOption.map(_.githubUrl))
                     , teamsAndRepositoriesConnector.allRepositories(team = Some(teamName), archived = Some(false))
                     , userManagementConnector.getTeam(teamName)
                     , teamsAndRepositoriesConnector.openPullRequestsRaisedByMembersOfTeam(teamName).map: openPrs =>
                        val githubUrl = s"https://github.com/search?q=org:hmrc+is:pr+is:open+${openPrs.map(_.author).distinct.map { a => s"author:$a" }.mkString("+")}&type=pullrequests"
                        (openPrs.size, url"$githubUrl")
                     , teamsAndRepositoriesConnector.openPullRequestsForReposOwnedByTeam(teamName).map: openPrs =>
                        val githubUrl = s"https://github.com/search?q=${openPrs.map(_.repoName).distinct.map { r => s"repo:hmrc/$r"}.mkString("+")}+is:pr+is:open&type=pullrequests"
                        (openPrs.size, url"$githubUrl")
                     , leakDetectionService.repoSummaries(team = Some(teamName), includeWarnings = false, includeExemptions = false, includeViolations = true, includeNonIssues = false)
                     , serviceDependenciesConnector.bobbyReports(teamName = Some(teamName), flag = SlugInfoFlag.ForEnvironment(Environment.Production))
                     , serviceDependenciesConnector.bobbyReports(teamName = Some(teamName), flag = SlugInfoFlag.Latest)
                     , ( shutterService.getShutterStates(ShutterType.Frontend, Environment.Production, Some(teamName), None)
                       , shutterService.getShutterStates(ShutterType.Api     , Environment.Production, Some(teamName), None)
                       ).tupled.map(x => x._1 ++ x._2)
                     , platformInitiativesConnector.getInitiatives(team = Some(teamName))
                     , vulnerabilitiesConnector.vulnerabilityCounts(SlugInfoFlag.Latest, team = Some(teamName))
                     , serviceCommissioningStatusConnector.cachedCommissioningStatus(teamName = Some(teamName))
                     , serviceMetricsConnector.metrics(Some(Environment.Production), Some(teamName))
                     , releasesConnector.releases(Some(Profile(ProfileType.Team, ProfileName(teamName.asString))))
                     , teamsAndRepositoriesConnector.findTestJobs(Some(teamName))
                     ).tupled
        yield Ok(teamInfoPage(teamName, umpMyTeamsUrl = umpConfig.umpMyTeamsPageUrl(teamName), results))
      else
        ( userManagementConnector.getTeam(teamName)
        , teamsAndRepositoriesConnector.allTeams()
        ).mapN {
          ( umpTeam
          , githubTeams
          ) =>
            val optGithubTeam = githubTeams.find(_.name == umpTeam.teamName)
            optGithubTeam match
              case Some(githubTeam) =>
                ( teamsAndRepositoriesConnector.repositoriesForTeam(teamName, Some(false))
                , leakDetectionService.repositoriesWithLeaks()
                , serviceDependenciesConnector.dependenciesForTeam(teamName)
                , serviceDependenciesConnector.getCuratedSlugDependenciesForTeam(teamName, SlugInfoFlag.ForEnvironment(Environment.Production))
                ).mapN:
                  ( repos
                  , reposWithLeaks
                  , masterTeamDependencies
                  , prodDependencies
                  ) =>
                    Ok(
                      teamInfoOldPage(
                        teamName               = teamName
                      , repos                  = repos.groupBy(_.repoType)
                      , umpTeam                = umpTeam
                      , umpMyTeamsUrl          = umpConfig.umpMyTeamsPageUrl(teamName)
                      , leaksFoundForTeam      = repos.exists(r => reposWithLeaks.exists(_.name == r.name))
                      , hasLeaks               = (repoName: String) => reposWithLeaks.exists(_.name == repoName)
                      , masterTeamDependencies = masterTeamDependencies.flatMap(mtd => repos.find(_.name == mtd.repositoryName).map(gr => RepoAndDependencies(gr, mtd)))
                      , prodDependencies       = prodDependencies
                      , gitHubUrl              = Some(githubTeam.githubUrl)
                      )
                    )
              case _ =>
                Future.successful(
                  Ok(
                    teamInfoOldPage(
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
        }.flatten

  def allTeams(name: Option[String]): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      ( userManagementConnector.getAllTeams()
      , teamsAndRepositoriesConnector.allTeams()
      ).mapN: (umpTeams, gitHubTeams) =>
        Ok(TeamsListPage(
          teams = umpTeams.map(umpTeam => (umpTeam, gitHubTeams.find(_.name == umpTeam.teamName))),
          name
        ))

  def outOfDateTeamDependencies(teamName: TeamName): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      ( teamsAndRepositoriesConnector.allRepositories(team = Some(teamName), archived = Some(false))
      , serviceDependenciesConnector.dependenciesForTeam(teamName)
      , serviceDependenciesConnector.getCuratedSlugDependenciesForTeam(teamName, SlugInfoFlag.ForEnvironment(Environment.Production))
      ).mapN:
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

end TeamsController
