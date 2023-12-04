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

package uk.gov.hmrc.cataloguefrontend.service

import org.mockito.MockitoSugar
import org.mockito.MockitoSugar.mock
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, RepoType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DefaultBranchesServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  "filterRepositories" should {
    "return an integer representing how many repositories should be returned in a default search" in new Setup {
      when(mockTeamsAndRepositoriesConnector.allRepositories())
        .thenReturn(Future.successful(mockRepositories))
      defaultBranchesService.filterRepositories(
        repositories    = mockRepositories,
        name            = None,
        defaultBranch   = None,
        teamNames       = None,
        singleOwnership = false,
        includeArchived = false
      ).length shouldBe 3
    }
  }

  "filterRepositoriesTwo" should {
    "return an integer representing how many repositories matches the provided parameters" in new Setup {
      when(mockTeamsAndRepositoriesConnector.allRepositories())
        .thenReturn(Future.successful(mockRepositoriesTwo))
      defaultBranchesService.filterRepositories(
        repositories    = mockRepositoriesTwo,
        name            = Some("test-3"),
        defaultBranch   = None,
        teamNames       = Some(TeamName("Team-1")),
        singleOwnership = false,
        includeArchived = false
      ).length shouldBe 1
    }
  }

  "allTeams" should {
    "return an integer representing how many teams are found" in new Setup {
      when(mockTeamsAndRepositoriesConnector.allRepositories(None, None, None, None, None))
        .thenReturn(Future.successful(mockRepositoriesTwo))
      val result: Seq[GitRepository] = defaultBranchesService.filterRepositories(
        repositories    = mockRepositoriesTwo,
        name            = None,
        defaultBranch   = None,
        teamNames       = None,
        singleOwnership = false,
        includeArchived = true
      )
      defaultBranchesService.allTeams(result).length shouldBe 3
    }
  }
}

  private[this] trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val mockTeamsAndRepositoriesConnector: TeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    val defaultBranchesService: DefaultBranchesService = new DefaultBranchesService()
    val mockRepositories: Seq[GitRepository] = Seq(
      GitRepository(
        name           = "test",
        createdDate    = Instant.now(),
        lastActiveDate = Instant.now(),
        repoType       = RepoType.Service,
        isArchived     = false,
        teamNames      = Seq(TeamName("Team-1"), TeamName("Team-2")),
        defaultBranch  = "main",
        description    = "",
        githubUrl      = "",
        language       = None
      ),
      GitRepository(
        name           = "test-2",
        createdDate    = Instant.now(),
        lastActiveDate = Instant.now(),
        repoType       = RepoType.Service,
        isArchived     = false,
        teamNames      = Seq(TeamName("Team-1")),
        defaultBranch  = "master",
        description    = "",
        githubUrl      = "",
        language       = None
      ),
      GitRepository(
        name           = "test-3",
        createdDate    = Instant.now(),
        lastActiveDate = Instant.now(),
        repoType       = RepoType.Service,
        isArchived     = false,
        teamNames      = Seq(TeamName("Team-1")),
        defaultBranch  = "main",
        description    = "",
        githubUrl      = "",
        language       = None
      ),
      GitRepository(
        name           = "test-4",
        createdDate    = Instant.now(),
        lastActiveDate = Instant.now(),
        repoType       = RepoType.Service,
        isArchived     = true,
        teamNames      = Seq(TeamName("Team-2")),
        defaultBranch  = "master",
        description    = "",
        githubUrl      = "",
        language       = None
      )
    )
    val mockRepositoriesTwo: Seq[GitRepository] = Seq(
      GitRepository(
        name           = "test",
        createdDate    = Instant.now(),
        lastActiveDate = Instant.now(),
        repoType       = RepoType.Service,
        isArchived     = false,
        teamNames      = Seq(TeamName("Team-3")),
        defaultBranch  = "main",
        description    = "",
        githubUrl      = "",
        language       = None
      ),
      GitRepository(
        name           = "test-2",
        createdDate    = Instant.now(),
        lastActiveDate = Instant.now(),
        repoType       = RepoType.Service,
        isArchived     = false,
        teamNames      = Seq(TeamName("Team-1")),
        defaultBranch  = "main",
        description    = "",
        githubUrl      = "",
        language       = None
      ),
      GitRepository(
        name           = "test-3",
        createdDate    = Instant.now(),
        lastActiveDate = Instant.now(),
        repoType       = RepoType.Service,
        isArchived     = false,
        teamNames      = Seq(TeamName("Team-1")),
        defaultBranch  = "main",
        description    = "",
        githubUrl      = "",
        language       = None
      ),
      GitRepository(
        name           = "test-4",
        createdDate    = Instant.now(),
        lastActiveDate = Instant.now(),
        repoType       = RepoType.Service,
        isArchived     = true,
        teamNames      = Seq(TeamName("Team-2")),
        defaultBranch  = "master",
        description    = "",
        githubUrl      = "",
        language       = None
      )
    )
}
