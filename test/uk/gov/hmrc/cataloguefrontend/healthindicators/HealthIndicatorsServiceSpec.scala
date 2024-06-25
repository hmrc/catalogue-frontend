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

import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.TeamName
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HealthIndicatorsServiceSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with ScalaFutures {

  "createRepoRatingsWithTeams" should {
    "return an empty owning teams set when .allTeamsByService is map.empty and allRepoRatings is Seq.empty" in new Setup {
      when(mockTeamsAndReposConnector.allTeamsByService())
        .thenReturn(Future.successful(Map.empty))

      when(mockHealthIndicatorsConnector.getIndicators(Some(RepoType.Service)))
        .thenReturn(Future.successful(Seq.empty))

      healthIndicatorsService.findIndicatorsWithTeams(Some(RepoType.Service), None).futureValue shouldBe
        Seq()
    }

    "return an empty owning teams set when .allTeamsByService is map.empty" in new Setup {
      when(mockTeamsAndReposConnector.allTeamsByService())
        .thenReturn(Future.successful(Map.empty))

      when(mockHealthIndicatorsConnector.getIndicators(Some(RepoType.Service)))
        .thenReturn(Future.successful(Seq(Indicator(repoName = "foo", RepoType.Service, overallScore = 10, weightedMetrics = Seq(WeightedMetric(MetricType.GitHub, 10, Seq.empty))))))

      healthIndicatorsService.findIndicatorsWithTeams(Some(RepoType.Service), None).futureValue shouldBe
        Seq(IndicatorsWithTeams(repoName = "foo", owningTeams = Seq.empty, RepoType.Service, overallScore = 10, weightedMetric = Seq(WeightedMetric(MetricType.GitHub, 10, Seq.empty))))
    }

    "return correct IndicatorsWithTeams when TARConnector returns teams and HIConnector returns Indicators" in new Setup {
      when(mockTeamsAndReposConnector.allTeamsByService())
        .thenReturn(Future.successful(
          Map("bar" -> Seq(TeamName("foo")))
        ))

      when(mockHealthIndicatorsConnector.getIndicators(Some(RepoType.Service)))
        .thenReturn(Future.successful(Seq(Indicator(repoName = "bar", RepoType.Service, overallScore = 10, weightedMetrics = Seq(WeightedMetric(MetricType.GitHub, 10, Seq.empty))))))

      healthIndicatorsService.findIndicatorsWithTeams(Some(RepoType.Service), None).futureValue shouldBe
        Seq(IndicatorsWithTeams(repoName = "bar", owningTeams = Seq(TeamName("foo")), RepoType.Service, overallScore = 10, weightedMetric = Seq(WeightedMetric(MetricType.GitHub, 10, Seq.empty))))
    }

    "return indicators filtered by repository name" in new Setup {
      when(mockTeamsAndReposConnector.allTeamsByService())
        .thenReturn(Future.successful(Map(
          "bAr"       -> Seq(TeamName("a")),
          "foobaRfoo" -> Seq(TeamName("b")),
          "foobar"    -> Seq(TeamName("c")),
          "hello"     -> Seq(TeamName("d")),
          "world"     -> Seq(TeamName("e"))
        )))

      when(mockHealthIndicatorsConnector.getIndicators(repoType = Some(RepoType.Service)))
        .thenReturn(Future.successful(
          Seq(
            Indicator(repoName = "bAr",       RepoType.Service, overallScore = 10, weightedMetrics = Seq(WeightedMetric(MetricType.GitHub, 10, Seq.empty))),
            Indicator(repoName = "foobaRfoo", RepoType.Service, overallScore = 11, weightedMetrics = Seq(WeightedMetric(MetricType.GitHub, 11, Seq.empty))),
            Indicator(repoName = "hello",     RepoType.Service, overallScore = 10, weightedMetrics = Seq(WeightedMetric(MetricType.GitHub, 10, Seq.empty))),
            Indicator(repoName = "foobar",    RepoType.Service, overallScore = 12, weightedMetrics = Seq(WeightedMetric(MetricType.GitHub, 12, Seq.empty))),
            Indicator(repoName = "world",     RepoType.Service, overallScore = 10, weightedMetrics = Seq(WeightedMetric(MetricType.GitHub, 10, Seq.empty)))
          )
        ))

      Seq("bar", "BaR", "BAR") foreach { searchTerm =>
        healthIndicatorsService.findIndicatorsWithTeams(repoType = Some(RepoType.Service), repoNameFilter = Some(searchTerm)).futureValue shouldBe
          Seq(
            IndicatorsWithTeams(repoName = "bAr"      , owningTeams = Seq(TeamName("a")), RepoType.Service, overallScore = 10, weightedMetric = Seq(WeightedMetric(MetricType.GitHub, 10, Seq.empty))),
            IndicatorsWithTeams(repoName = "foobaRfoo", owningTeams = Seq(TeamName("b")), RepoType.Service, overallScore = 11, weightedMetric = Seq(WeightedMetric(MetricType.GitHub, 11, Seq.empty))),
            IndicatorsWithTeams(repoName = "foobar"   , owningTeams = Seq(TeamName("c")), RepoType.Service, overallScore = 12, weightedMetric = Seq(WeightedMetric(MetricType.GitHub, 12, Seq.empty))),
          )
      }
    }
  }

  private[this] trait Setup {
    given HeaderCarrier                                = HeaderCarrier()
    val mockTeamsAndReposConnector: TeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    val mockHealthIndicatorsConnector: HealthIndicatorsConnector  = mock[HealthIndicatorsConnector]
    val healthIndicatorsService                                   = HealthIndicatorsService(mockTeamsAndReposConnector, mockHealthIndicatorsConnector)
  }
}
