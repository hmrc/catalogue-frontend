/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.healthindicators

import uk.gov.hmrc.cataloguefrontend.connector.{Team, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HealthIndicatorsService @Inject()(
  teamsAndReposConnector: TeamsAndRepositoriesConnector,
  healthIndicatorsConnector: HealthIndicatorsConnector
)(
  implicit ec: ExecutionContext
) {

  def createRepoRatingsWithTeams(implicit hc: HeaderCarrier): Future[Seq[RepoRatingsWithTeams]] = {
    val repoToTeamsFut    = getRepoToTeams
    val allRepoRatingsFut = healthIndicatorsConnector.getAllRepositoryRatings

    for {
      repoToTeams    <- repoToTeamsFut
      allRepoRatings <- allRepoRatingsFut
      repoRatingsWithTeams = allRepoRatings.map(rr => {
        repoToTeams.get(rr.repositoryName) match {
          case Some(owningTeamsSet) => RepoRatingsWithTeams(rr.repositoryName, owningTeamsSet, rr.repositoryType, rr.repositoryScore, rr.ratings)
          case None                 => RepoRatingsWithTeams(rr.repositoryName, Set(), rr.repositoryType, rr.repositoryScore, rr.ratings)
        }
      })

    } yield repoRatingsWithTeams
  }

  def getRepoToTeams(implicit hc: HeaderCarrier): Future[Map[String, Set[String]]] =
    teamsAndReposConnector.teamsWithRepositories.map(invertTeamsToRepos)

  private def invertTeamsToRepos(teamsWithRepos: Seq[Team]): Map[String, Set[String]] = teamsWithRepos.foldLeft(Map.empty[String, Set[String]]) {
    case (runningTotal, Team(teamName, _, _, _, maybeRepos)) =>
      maybeRepos match {
        case None => runningTotal
        case Some(repos) =>
          repos.values.flatten.foldLeft(runningTotal) {
            case (result, repoName) =>
              val teams = result.get(repoName) match {
                case Some(currentTeams) => currentTeams ++ Set(teamName.asString)
                case None               => Set(teamName.asString)
              }
              result ++ Map(repoName -> teams)
          }
      }
  }
}

case class RepoRatingsWithTeams(repositoryName: String, owningTeams: Set[String], repositoryType: RepoType, repositoryScore: Int, ratings: Seq[Rating])

