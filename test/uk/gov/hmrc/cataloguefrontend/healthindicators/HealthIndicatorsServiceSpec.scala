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
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HealthIndicatorsServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {
  "createRepoRatingsWithTeams" should {
    "return an empty owning teams set when teamsWithRepositories is map.empty and allRepoRatings is Seq.empty" in new Setup {
      when(mockTARC.teamsWithRepositories) thenReturn
        Future.successful(Seq.empty)

      when(mockHIC.getAllRepositoryRatings) thenReturn
        Future.successful(Seq.empty)

      hIS.createRepoRatingsWithTeams.futureValue shouldBe
        Seq()
    }

    "return an empty owning teams set when teamsWithRepositories is map.empty" in new Setup {
      when(mockTARC.teamsWithRepositories) thenReturn
        Future.successful(Seq.empty)

      when(mockHIC.getAllRepositoryRatings) thenReturn
        Future.successful(Seq(RepositoryRating("foo", RepoType.Service, 10, Seq(Rating(RatingType.ReadMe, 10, Seq.empty)))))

      hIS.createRepoRatingsWithTeams.futureValue shouldBe
        Seq(RepoRatingsWithTeams("foo", Set(), RepoType.Service, 10, Seq(Rating(RatingType.ReadMe, 10, Seq.empty))))
    }

    "return correct RepoRatingsWithTeams when TARConnector returns teams and HIConnector returns RepoRatings" in new Setup {
      when(mockTARC.teamsWithRepositories) thenReturn
        Future.successful(Seq(Team(TeamName("bar"), None, None, None, Some(Map("Service" -> Seq("foo"))))))

      when(mockHIC.getAllRepositoryRatings) thenReturn
        Future.successful(Seq(RepositoryRating("foo", RepoType.Service, 10, Seq(Rating(RatingType.ReadMe, 10, Seq.empty)))))

      hIS.createRepoRatingsWithTeams.futureValue shouldBe
        Seq(RepoRatingsWithTeams("foo", Set("bar"), RepoType.Service, 10, Seq(Rating(RatingType.ReadMe, 10, Seq.empty))))
    }
  }

  "getRepoToTeams" should {
    "return empty map when connector returns no teams" in new Setup {
      when(mockTARC.teamsWithRepositories) thenReturn
        Future.successful(Seq.empty)

      hIS.getRepoToTeams.futureValue shouldBe Map.empty
    }

    "return Map.empty when TeamsAndReposConnector returns None repo for team" in new Setup {
      when(mockTARC.teamsWithRepositories) thenReturn
        Future.successful(Seq(Team(TeamName("foo"), None, None, None, repos = None)))

      hIS.getRepoToTeams.futureValue shouldBe Map.empty
    }

    "return Map.empty when TeamsAndReposConnector returns empty repoType map for a team" in new Setup {
      when(mockTARC.teamsWithRepositories) thenReturn
        Future.successful(Seq(Team(TeamName("foo"), None, None, None, repos = Some(Map.empty))))

      hIS.getRepoToTeams.futureValue shouldBe Map.empty
    }

    "return all teams for one repo when TeamsAndReposConnector returns multiple teams with only one repo" in new Setup {
      when(mockTARC.teamsWithRepositories) thenReturn
        Future.successful(
          Seq(
            Team(TeamName("foo"), None, None, None, Some(Map("Service"   -> Seq("auth")))),
            Team(TeamName("bar"), None, None, None, Some(Map("Prototype" -> Seq("auth")))),
            Team(TeamName("hello"), None, None, None, Some(Map("Other"   -> Seq("auth"))))
          ))

      hIS.getRepoToTeams.futureValue shouldBe Map("auth" -> Set("foo", "bar", "hello"))
    }

    "return one team for many repos when TeamsAndRepositoriesConnector returns one team with multiple repos" in new Setup {
      when(mockTARC.teamsWithRepositories) thenReturn
        Future.successful(
          Seq(
            Team(TeamName("foo"), None, None, None, Some(Map("Service" -> Seq("auth"), "Prototype" -> Seq("admin"), "Other" -> Seq("advert"))))
          ))

      hIS.getRepoToTeams.futureValue shouldBe Map("auth" -> Set("foo"), "admin" -> Set("foo"), "advert" -> Set("foo"))
    }

    "return correct map when TeamsAndRepositories returns multiple teams with some duplicate repositories" in new Setup {
      when(mockTARC.teamsWithRepositories) thenReturn
        Future.successful(
          Seq(
            Team(TeamName("foo"), None, None, None, Some(Map("Service" -> Seq("auth"), "Prototype"  -> Seq("admin"), "Other" -> Seq("advert", "world")))),
            Team(TeamName("bar"), None, None, None, Some(Map("Service" -> Seq("hello"), "Prototype" -> Seq("jfrog"), "Other" -> Seq("advert"), "Stub" -> Seq("get"))))
          ))

      hIS.getRepoToTeams.futureValue shouldBe
        Map("auth" -> Set("foo"), "admin" -> Set("foo"), "advert" -> Set("foo", "bar"), "world" -> Set("foo"), "hello" -> Set("bar"), "jfrog" -> Set("bar"), "get" -> Set("bar"))

    }
  }
  private[this] trait Setup {
    implicit val hc: HeaderCarrier              = HeaderCarrier()
    val mockTARC: TeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    val mockHIC: HealthIndicatorsConnector      = mock[HealthIndicatorsConnector]
    val hIS                                     = new HealthIndicatorsService(mockTARC, mockHIC)
  }
}
