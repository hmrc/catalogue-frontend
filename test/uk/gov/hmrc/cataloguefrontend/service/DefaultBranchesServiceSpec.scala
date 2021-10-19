package uk.gov.hmrc.cataloguefrontend.service

import org.mockito.MockitoSugar
import org.mockito.MockitoSugar.mock
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, RepositoryDisplayDetails, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DefaultBranchesServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  "filterRepositories" should {
    "return an integer representing how many repositories should be returned in a default search" in new Setup {
      when(mockTeamsAndRepositoriesConnector.allDefaultBranches) thenReturn
        Future.successful(mockRepositories)
      defaultBranchesService.filterRepositories(
        repositories = mockRepositories,
        name = Option(null),
        defaultBranch = Option(null),
        teamNames = Option(null),
        singleOwnership = false,
        includeArchived = false
      ).length shouldBe 3
    }
  }

  "filterRepositoriesTwo" should {
    "return an integer representing how many repositories matches the provided parameters" in new Setup {
      when(mockTeamsAndRepositoriesConnector.allDefaultBranches) thenReturn
        Future.successful(mockRepositoriesTwo)
      defaultBranchesService.filterRepositories(
        repositories = mockRepositoriesTwo,
        name = Some("test-3"),
        defaultBranch = Option(null),
        teamNames = Some("Team-1"),
        singleOwnership = false,
        includeArchived = false
      ).length shouldBe 1
    }
  }

  "allTeams" should {
    "return an integer representing how many teams are found" in new Setup {
      when(mockTeamsAndRepositoriesConnector.allDefaultBranches) thenReturn
        Future.successful(mockRepositoriesTwo)
      val result: Seq[RepositoryDisplayDetails] = defaultBranchesService.filterRepositories(
        repositories = mockRepositoriesTwo,
        name = Option(null),
        defaultBranch = Option(null),
        teamNames = Option(null),
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
    val mockRepositories: Seq[RepositoryDisplayDetails] = Seq(
      RepositoryDisplayDetails(
        name = "test",
        createdAt = LocalDateTime.now(),
        lastUpdatedAt = LocalDateTime.now(),
        repoType = RepoType.Service,
        isArchived = false,
        teamNames = Seq("Team-1", "Team-2"),
        defaultBranch = "main"
      ),
      RepositoryDisplayDetails(
        name = "test-2",
        createdAt = LocalDateTime.now(),
        lastUpdatedAt = LocalDateTime.now(),
        repoType = RepoType.Service,
        isArchived = false,
        teamNames = Seq("Team-1"),
        defaultBranch = "master"
      ),
      RepositoryDisplayDetails(
        name = "test-3",
        createdAt = LocalDateTime.now(),
        lastUpdatedAt = LocalDateTime.now(),
        repoType = RepoType.Service,
        isArchived = false,
        teamNames = Seq("Team-1"),
        defaultBranch = "main"
      ),
      RepositoryDisplayDetails(
        name = "test-4",
        createdAt = LocalDateTime.now(),
        lastUpdatedAt = LocalDateTime.now(),
        repoType = RepoType.Service,
        isArchived = true,
        teamNames = Seq("Team-2"),
        defaultBranch = "master"
      )
    )
    val mockRepositoriesTwo: Seq[RepositoryDisplayDetails] = Seq(
      RepositoryDisplayDetails(
        name = "test",
        createdAt = LocalDateTime.now(),
        lastUpdatedAt = LocalDateTime.now(),
        repoType = RepoType.Service,
        isArchived = false,
        teamNames = Seq("Team-3"),
        defaultBranch = "main"
      ),
      RepositoryDisplayDetails(
        name = "test-2",
        createdAt = LocalDateTime.now(),
        lastUpdatedAt = LocalDateTime.now(),
        repoType = RepoType.Service,
        isArchived = false,
        teamNames = Seq("Team-1"),
        defaultBranch = "main"
      ),
      RepositoryDisplayDetails(
        name = "test-3",
        createdAt = LocalDateTime.now(),
        lastUpdatedAt = LocalDateTime.now(),
        repoType = RepoType.Service,
        isArchived = false,
        teamNames = Seq("Team-1"),
        defaultBranch = "main"
      ),
      RepositoryDisplayDetails(
        name = "test-4",
        createdAt = LocalDateTime.now(),
        lastUpdatedAt = LocalDateTime.now(),
        repoType = RepoType.Service,
        isArchived = true,
        teamNames = Seq("Team-2"),
        defaultBranch = "master"
      )
    )
}