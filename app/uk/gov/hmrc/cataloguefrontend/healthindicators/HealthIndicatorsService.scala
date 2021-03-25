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

import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
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
    val repoToTeamsFut    = teamsAndReposConnector.allTeamsByService
    val allRepoRatingsFut = healthIndicatorsConnector.getAllRepositoryRatings

    for {
      repoToTeams    <- repoToTeamsFut
      allRepoRatings <- allRepoRatingsFut
    } yield
      allRepoRatings.map { rr =>
        RepoRatingsWithTeams(
          rr.repositoryName,
          owningTeams = repoToTeams.getOrElse(rr.repositoryName, Seq.empty),
          rr.repositoryType,
          rr.repositoryScore,
          rr.ratings
        )
      }
  }
}

case class RepoRatingsWithTeams(repositoryName: String, owningTeams: Seq[TeamName], repositoryType: RepoType, repositoryScore: Int, ratings: Seq[Rating])
