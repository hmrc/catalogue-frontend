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

import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HealthIndicatorsServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {
  "createRepoRatingsWithTeams" should {
    "return an empty owning teams set when .allTeamsByService is map.empty and allRepoRatings is Seq.empty" in new Setup {
      when(mockTeamsAndReposConnector.allTeamsByService) thenReturn
        Future.successful(Map.empty)

      when(mockHealthIndicatorsConnector.getAllIndicators(RepoType.Service)) thenReturn
        Future.successful(Seq.empty)

      healthIndicatorsService.findIndicatorsWithTeams(RepoType.Service).futureValue shouldBe
        Seq()
    }

    "return an empty owning teams set when .allTeamsByService is map.empty" in new Setup {
      when(mockTeamsAndReposConnector.allTeamsByService) thenReturn
        Future.successful(Map.empty)

      when(mockHealthIndicatorsConnector.getAllIndicators(RepoType.Service)) thenReturn
        Future.successful(Seq(Indicator("foo", RepoType.Service, 10, Seq(WeightedMetric(MetricType.GitHub, 10, Seq.empty)))))

      healthIndicatorsService.findIndicatorsWithTeams(RepoType.Service).futureValue shouldBe
        Seq(IndicatorsWithTeams("foo", Seq.empty, RepoType.Service, 10, Seq(WeightedMetric(MetricType.GitHub, 10, Seq.empty))))
    }

    "return correct IndicatorsWithTeams when TARConnector returns teams and HIConnector returns Indicators" in new Setup {
      when(mockTeamsAndReposConnector.allTeamsByService) thenReturn
        Future.successful(Map("bar" -> Seq(TeamName("foo"))))

      when(mockHealthIndicatorsConnector.getAllIndicators(RepoType.Service)) thenReturn
        Future.successful(Seq(Indicator("bar", RepoType.Service, 10, Seq(WeightedMetric(MetricType.GitHub, 10, Seq.empty)))))

      healthIndicatorsService.findIndicatorsWithTeams(RepoType.Service).futureValue shouldBe
        Seq(IndicatorsWithTeams("bar", Seq(TeamName("foo")), RepoType.Service, 10, Seq(WeightedMetric(MetricType.GitHub, 10, Seq.empty))))
    }
  }
  private[this] trait Setup {
    implicit val hc: HeaderCarrier                                = HeaderCarrier()
    val mockTeamsAndReposConnector: TeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    val mockHealthIndicatorsConnector: HealthIndicatorsConnector  = mock[HealthIndicatorsConnector]
    val healthIndicatorsService                                   = new HealthIndicatorsService(mockTeamsAndReposConnector, mockHealthIndicatorsConnector)
  }
}
