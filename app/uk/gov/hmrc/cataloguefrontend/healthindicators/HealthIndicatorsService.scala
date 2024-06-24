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

package uk.gov.hmrc.cataloguefrontend.healthindicators

import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.connector.RepoType
import uk.gov.hmrc.cataloguefrontend.model.TeamName
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HealthIndicatorsService @Inject() (
  teamsAndReposConnector   : TeamsAndRepositoriesConnector,
  healthIndicatorsConnector: HealthIndicatorsConnector
)(implicit
  ec: ExecutionContext
) {

  def findIndicatorsWithTeams(
    repoType      : Option[RepoType],
    repoNameFilter: Option[String]
  )(implicit
    hc: HeaderCarrier
  ): Future[Seq[IndicatorsWithTeams]] =
    for {
      repoToTeams        <- teamsAndReposConnector.allTeamsByService()
      indicators         <- healthIndicatorsConnector.getIndicators(repoType)
      filteredIndicators =  indicators.filter(ind => repoNameFilter.fold(true)(name => ind.repoName.toLowerCase.contains(name.toLowerCase)))
    } yield
      filteredIndicators.map(i =>
        IndicatorsWithTeams(
          i.repoName,
          owningTeams = repoToTeams.getOrElse(i.repoName, Seq.empty).sorted,
          i.repoType,
          i.overallScore,
          i.weightedMetrics
        )
      )
}

case class IndicatorsWithTeams(
  repoName      : String,
  owningTeams   : Seq[TeamName],
  repoType      : RepoType,
  overallScore  : Int,
  weightedMetric: Seq[WeightedMetric]
)
