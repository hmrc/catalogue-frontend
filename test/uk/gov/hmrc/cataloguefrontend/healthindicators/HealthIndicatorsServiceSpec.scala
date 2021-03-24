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
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.connector.{Team, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.ServiceName
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HealthIndicatorsServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {
  "createRepoRatingsWithTeams" should {
    "return an empty owning teams set when .allTeamsByService is map.empty and allRepoRatings is Seq.empty" in new Setup {
      when(mockTARC.allTeamsByService) thenReturn
        Future.successful(Map.empty)

      when(mockHIC.getAllRepositoryRatings) thenReturn
        Future.successful(Seq.empty)

      hIS.createRepoRatingsWithTeams.futureValue shouldBe
        Seq()
    }

    "return an empty owning teams set when .allTeamsByService is map.empty" in new Setup {
      when(mockTARC.allTeamsByService) thenReturn
        Future.successful(Map.empty)

      when(mockHIC.getAllRepositoryRatings) thenReturn
        Future.successful(Seq(RepositoryRating("foo", RepoType.Service, 10, Seq(Rating(RatingType.ReadMe, 10, Seq.empty)))))

      hIS.createRepoRatingsWithTeams.futureValue shouldBe
        Seq(RepoRatingsWithTeams("foo", Seq.empty, RepoType.Service, 10, Seq(Rating(RatingType.ReadMe, 10, Seq.empty))))
    }

    "return correct RepoRatingsWithTeams when TARConnector returns teams and HIConnector returns RepoRatings" in new Setup {
      when(mockTARC.allTeamsByService) thenReturn
        Future.successful(Map("bar" -> Seq(TeamName("foo"))))

      when(mockHIC.getAllRepositoryRatings) thenReturn
        Future.successful(Seq(RepositoryRating("bar", RepoType.Service, 10, Seq(Rating(RatingType.ReadMe, 10, Seq.empty)))))

      hIS.createRepoRatingsWithTeams.futureValue shouldBe
        Seq(RepoRatingsWithTeams("bar", Seq(TeamName("foo")), RepoType.Service, 10, Seq(Rating(RatingType.ReadMe, 10, Seq.empty))))
    }
  }
  private[this] trait Setup {
    implicit val hc: HeaderCarrier              = HeaderCarrier()
    val mockTARC: TeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    val mockHIC: HealthIndicatorsConnector      = mock[HealthIndicatorsConnector]
    val hIS                                     = new HealthIndicatorsService(mockTARC, mockHIC)
  }
}
