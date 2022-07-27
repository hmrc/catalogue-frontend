/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.repository

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.{BranchProtection, GitRepository, RepoType}

import java.time.Instant
import java.time.temporal.ChronoUnit

class RepositoriesSortingSpec extends AnyWordSpec with Matchers {

  val now = Instant.now()
  val repo1 =
    GitRepository("service1",
      createdDate      = now.minus(30, ChronoUnit.SECONDS),
      lastActiveDate   = now.minus(20, ChronoUnit.SECONDS),
      repoType         = RepoType.Library,
      isArchived       = false,
      branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true, requiresCommitSignatures = true)),
      teamNames        = Seq("Ateam", "Bteam"),
      defaultBranch    = "main",
      githubUrl        = "",
      language         = None,
      description      = "")

    val repo2 =
      GitRepository("SERVICE2",
        createdDate    = now.minus(35, ChronoUnit.SECONDS),
        lastActiveDate = now.minus(30, ChronoUnit.SECONDS),
        repoType       = RepoType.Service,
        isArchived     = false,
        teamNames      = Seq("Bteam", "Cteam"),
        defaultBranch  = "main",
        githubUrl      = "",
        language       = None,
        description    = "")

    val repo3 =
      GitRepository("AMicroService",
        createdDate    = now.minus(25, ChronoUnit.SECONDS),
        lastActiveDate = now.minus(18, ChronoUnit.SECONDS),
        repoType       = RepoType.Other,
        isArchived     = true,
        teamNames      = Seq("Eteam", "Fteam"),
        defaultBranch  = "main",
        githubUrl      = "",
        language       = None,
        description    = "")

    val repo4 =
      GitRepository("frontendService",
        createdDate = now.minus(15, ChronoUnit.SECONDS),
        lastActiveDate = now.minus(5, ChronoUnit.SECONDS),
        repoType = RepoType.Prototype,
        isArchived = false,
        teamNames = Seq("Dteam"),
        defaultBranch = "main",
        githubUrl = "",
        language = None,
        description = "")

    val repos = Seq(repo1, repo2, repo3, repo4)

  "RepoSorter" should {
    "Sort by name in ascending order" in {
      RepoSorter.sort(repos, "name", "asc") shouldBe
        Seq(
          GitRepository("AMicroService", createdDate = now.minus(25, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(18, ChronoUnit.SECONDS), repoType = RepoType.Other, isArchived = true,
            teamNames = Seq("Eteam", "Fteam"), defaultBranch  = "main", githubUrl = "", language = None, description = ""),
          GitRepository("frontendService", createdDate = now.minus(15, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(5, ChronoUnit.SECONDS), repoType = RepoType.Prototype, isArchived = false,
            teamNames = Seq("Dteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("service1", createdDate      = now.minus(30, ChronoUnit.SECONDS),
            lastActiveDate   = now.minus(20, ChronoUnit.SECONDS), repoType = RepoType.Library, isArchived = false,
            branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true, requiresCommitSignatures = true)),
            teamNames = Seq("Ateam", "Bteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("SERVICE2", createdDate    = now.minus(35, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(30, ChronoUnit.SECONDS), repoType = RepoType.Service, isArchived = false,
            teamNames = Seq("Bteam", "Cteam"), defaultBranch = "main", githubUrl = "", language = None, description = "")
        )
    }

    "Sort by name in descending order" in {
      RepoSorter.sort(repos, "name", "desc") shouldBe
        Seq(
          GitRepository("SERVICE2", createdDate    = now.minus(35, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(30, ChronoUnit.SECONDS), repoType = RepoType.Service, isArchived = false,
            teamNames = Seq("Bteam", "Cteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("service1", createdDate      = now.minus(30, ChronoUnit.SECONDS),
            lastActiveDate   = now.minus(20, ChronoUnit.SECONDS), repoType = RepoType.Library, isArchived = false,
            branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true, requiresCommitSignatures = true)),
            teamNames = Seq("Ateam", "Bteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("frontendService", createdDate = now.minus(15, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(5, ChronoUnit.SECONDS), repoType = RepoType.Prototype, isArchived = false,
            teamNames = Seq("Dteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("AMicroService", createdDate = now.minus(25, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(18, ChronoUnit.SECONDS), repoType = RepoType.Other, isArchived = true,
            teamNames = Seq("Eteam", "Fteam"), defaultBranch  = "main", githubUrl = "", language = None, description = "")
        )
    }

    "Sort by repotype in ascending order" in {
      RepoSorter.sort(repos, "repoType", "asc") shouldBe
        Seq(
          GitRepository("service1", createdDate      = now.minus(30, ChronoUnit.SECONDS),
            lastActiveDate   = now.minus(20, ChronoUnit.SECONDS), repoType = RepoType.Library, isArchived = false,
            branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true, requiresCommitSignatures = true)),
            teamNames = Seq("Ateam", "Bteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("AMicroService", createdDate = now.minus(25, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(18, ChronoUnit.SECONDS), repoType = RepoType.Other, isArchived = true,
            teamNames = Seq("Eteam", "Fteam"), defaultBranch  = "main", githubUrl = "", language = None, description = ""),
          GitRepository("frontendService", createdDate = now.minus(15, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(5, ChronoUnit.SECONDS), repoType = RepoType.Prototype, isArchived = false,
            teamNames = Seq("Dteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("SERVICE2", createdDate    = now.minus(35, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(30, ChronoUnit.SECONDS), repoType = RepoType.Service, isArchived = false,
            teamNames = Seq("Bteam", "Cteam"), defaultBranch = "main", githubUrl = "", language = None, description = "")
        )
    }

    "Sort by repotype in descending order" in {
      RepoSorter.sort(repos, "repoType", "desc") shouldBe
        Seq(
          GitRepository("SERVICE2", createdDate    = now.minus(35, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(30, ChronoUnit.SECONDS), repoType = RepoType.Service, isArchived = false,
            teamNames = Seq("Bteam", "Cteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("frontendService", createdDate = now.minus(15, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(5, ChronoUnit.SECONDS), repoType = RepoType.Prototype, isArchived = false,
            teamNames = Seq("Dteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("AMicroService", createdDate = now.minus(25, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(18, ChronoUnit.SECONDS), repoType = RepoType.Other, isArchived = true,
            teamNames = Seq("Eteam", "Fteam"), defaultBranch  = "main", githubUrl = "", language = None, description = ""),
          GitRepository("service1", createdDate      = now.minus(30, ChronoUnit.SECONDS),
            lastActiveDate   = now.minus(20, ChronoUnit.SECONDS), repoType = RepoType.Library, isArchived = false,
            branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true, requiresCommitSignatures = true)),
            teamNames = Seq("Ateam", "Bteam"), defaultBranch = "main", githubUrl = "", language = None, description = "")
        )
    }

    "Sort by teamnames in ascending order" in {
      RepoSorter.sort(repos, "teamNames", "asc") shouldBe
        Seq(
          GitRepository("service1", createdDate = now.minus(30, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(20, ChronoUnit.SECONDS), repoType = RepoType.Library, isArchived = false,
            branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true, requiresCommitSignatures = true)),
            teamNames = Seq("Ateam", "Bteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("SERVICE2", createdDate = now.minus(35, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(30, ChronoUnit.SECONDS), repoType = RepoType.Service, isArchived = false,
            teamNames = Seq("Bteam", "Cteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("frontendService", createdDate = now.minus(15, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(5, ChronoUnit.SECONDS), repoType = RepoType.Prototype, isArchived = false,
            teamNames = Seq("Dteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("AMicroService", createdDate = now.minus(25, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(18, ChronoUnit.SECONDS), repoType = RepoType.Other, isArchived = true,
            teamNames = Seq("Eteam", "Fteam"), defaultBranch = "main", githubUrl = "", language = None, description = "")
        )
    }

      "Sort by teamnames in descending order" in {
        RepoSorter.sort(repos, "teamNames", "desc") shouldBe
          Seq(
            GitRepository("AMicroService", createdDate = now.minus(25, ChronoUnit.SECONDS),
              lastActiveDate = now.minus(18, ChronoUnit.SECONDS), repoType = RepoType.Other, isArchived = true,
              teamNames = Seq("Eteam", "Fteam"), defaultBranch  = "main", githubUrl = "", language = None, description = ""),
            GitRepository("frontendService", createdDate = now.minus(15, ChronoUnit.SECONDS),
              lastActiveDate = now.minus(5, ChronoUnit.SECONDS), repoType = RepoType.Prototype, isArchived = false,
              teamNames = Seq("Dteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
            GitRepository("SERVICE2", createdDate    = now.minus(35, ChronoUnit.SECONDS),
              lastActiveDate = now.minus(30, ChronoUnit.SECONDS), repoType = RepoType.Service, isArchived = false,
              teamNames = Seq("Bteam", "Cteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
            GitRepository("service1", createdDate      = now.minus(30, ChronoUnit.SECONDS),
              lastActiveDate   = now.minus(20, ChronoUnit.SECONDS), repoType = RepoType.Library, isArchived = false,
              branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true, requiresCommitSignatures = true)),
              teamNames = Seq("Ateam", "Bteam"), defaultBranch = "main", githubUrl = "", language = None, description = "")
          )
      }

      "Sort by status in descending order" in {
        RepoSorter.sort(repos, "status", "desc") shouldBe
          Seq(
            GitRepository("AMicroService", createdDate = now.minus(25, ChronoUnit.SECONDS),
              lastActiveDate = now.minus(18, ChronoUnit.SECONDS), repoType = RepoType.Other, isArchived = true,
              teamNames = Seq("Eteam", "Fteam"), defaultBranch  = "main", githubUrl = "", language = None, description = ""),
            GitRepository("service1", createdDate      = now.minus(30, ChronoUnit.SECONDS),
              lastActiveDate   = now.minus(20, ChronoUnit.SECONDS), repoType = RepoType.Library, isArchived = false,
              branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true, requiresCommitSignatures = true)),
              teamNames = Seq("Ateam", "Bteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
            GitRepository("SERVICE2", createdDate    = now.minus(35, ChronoUnit.SECONDS),
              lastActiveDate = now.minus(30, ChronoUnit.SECONDS), repoType = RepoType.Service, isArchived = false,
              teamNames = Seq("Bteam", "Cteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
            GitRepository("frontendService", createdDate = now.minus(15, ChronoUnit.SECONDS),
              lastActiveDate = now.minus(5, ChronoUnit.SECONDS), repoType = RepoType.Prototype, isArchived = false,
              teamNames = Seq("Dteam"), defaultBranch = "main", githubUrl = "", language = None, description = "")
          )
      }

    "Sort by branch protection in ascending order" in {
      RepoSorter.sort(repos, "branchProtection", "asc") shouldBe
        Seq(
          GitRepository("SERVICE2", createdDate    = now.minus(35, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(30, ChronoUnit.SECONDS), repoType = RepoType.Service, isArchived = false,
            teamNames = Seq("Bteam", "Cteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("AMicroService", createdDate = now.minus(25, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(18, ChronoUnit.SECONDS), repoType = RepoType.Other, isArchived = true,
            teamNames = Seq("Eteam", "Fteam"), defaultBranch  = "main", githubUrl = "", language = None, description = ""),
          GitRepository("frontendService", createdDate = now.minus(15, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(5, ChronoUnit.SECONDS), repoType = RepoType.Prototype, isArchived = false,
            teamNames = Seq("Dteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("service1", createdDate      = now.minus(30, ChronoUnit.SECONDS),
            lastActiveDate   = now.minus(20, ChronoUnit.SECONDS), repoType = RepoType.Library, isArchived = false,
            branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true, requiresCommitSignatures = true)),
            teamNames = Seq("Ateam", "Bteam"), defaultBranch = "main", githubUrl = "", language = None, description = "")
        )
    }

    "Sort by date created in ascending order" in {
      RepoSorter.sort(repos, "createdDate", "asc") shouldBe
        Seq(
          GitRepository("SERVICE2", createdDate = now.minus(35, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(30, ChronoUnit.SECONDS), repoType = RepoType.Service, isArchived = false,
            teamNames = Seq("Bteam", "Cteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("service1", createdDate = now.minus(30, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(20, ChronoUnit.SECONDS), repoType = RepoType.Library, isArchived = false,
            branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true, requiresCommitSignatures = true)),
            teamNames = Seq("Ateam", "Bteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("AMicroService", createdDate = now.minus(25, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(18, ChronoUnit.SECONDS), repoType = RepoType.Other, isArchived = true,
            teamNames = Seq("Eteam", "Fteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("frontendService", createdDate = now.minus(15, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(5, ChronoUnit.SECONDS), repoType = RepoType.Prototype, isArchived = false,
            teamNames = Seq("Dteam"), defaultBranch = "main", githubUrl = "", language = None, description = "")
        )
    }

    "Sort by last active date in descending order" in {
      RepoSorter.sort(repos, "lastActiveDate", "desc") shouldBe
        Seq(
          GitRepository("frontendService", createdDate = now.minus(15, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(5, ChronoUnit.SECONDS), repoType = RepoType.Prototype, isArchived = false,
            teamNames = Seq("Dteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("AMicroService", createdDate = now.minus(25, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(18, ChronoUnit.SECONDS), repoType = RepoType.Other, isArchived = true,
            teamNames = Seq("Eteam", "Fteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("service1", createdDate = now.minus(30, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(20, ChronoUnit.SECONDS), repoType = RepoType.Library, isArchived = false,
            branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true, requiresCommitSignatures = true)),
            teamNames = Seq("Ateam", "Bteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("SERVICE2", createdDate = now.minus(35, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(30, ChronoUnit.SECONDS), repoType = RepoType.Service, isArchived = false,
            teamNames = Seq("Bteam", "Cteam"), defaultBranch = "main", githubUrl = "", language = None, description = "")
        )
    }

    "An invalid sort type will default to ascending" in {
      RepoSorter.sort(repos, "createdDate", "foobar") shouldBe
        Seq(
          GitRepository("SERVICE2", createdDate = now.minus(35, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(30, ChronoUnit.SECONDS), repoType = RepoType.Service, isArchived = false,
            teamNames = Seq("Bteam", "Cteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("service1", createdDate = now.minus(30, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(20, ChronoUnit.SECONDS), repoType = RepoType.Library, isArchived = false,
            branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true, requiresCommitSignatures = true)),
            teamNames = Seq("Ateam", "Bteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("AMicroService", createdDate = now.minus(25, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(18, ChronoUnit.SECONDS), repoType = RepoType.Other, isArchived = true,
            teamNames = Seq("Eteam", "Fteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("frontendService", createdDate = now.minus(15, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(5, ChronoUnit.SECONDS), repoType = RepoType.Prototype, isArchived = false,
            teamNames = Seq("Dteam"), defaultBranch = "main", githubUrl = "", language = None, description = "")
        )
    }

    "An [invalid column name/column with an undefined ordering] will default to sorting on name" in {
      RepoSorter.sort(repos, "ThisColumnDoesntExist", "desc") shouldBe
        Seq(
          GitRepository("SERVICE2", createdDate    = now.minus(35, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(30, ChronoUnit.SECONDS), repoType = RepoType.Service, isArchived = false,
            teamNames = Seq("Bteam", "Cteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("service1", createdDate      = now.minus(30, ChronoUnit.SECONDS),
            lastActiveDate   = now.minus(20, ChronoUnit.SECONDS), repoType = RepoType.Library, isArchived = false,
            branchProtection = Some(BranchProtection(requiresApprovingReviews = true, dismissesStaleReviews = true, requiresCommitSignatures = true)),
            teamNames = Seq("Ateam", "Bteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("frontendService", createdDate = now.minus(15, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(5, ChronoUnit.SECONDS), repoType = RepoType.Prototype, isArchived = false,
            teamNames = Seq("Dteam"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
          GitRepository("AMicroService", createdDate = now.minus(25, ChronoUnit.SECONDS),
            lastActiveDate = now.minus(18, ChronoUnit.SECONDS), repoType = RepoType.Other, isArchived = true,
            teamNames = Seq("Eteam", "Fteam"), defaultBranch  = "main", githubUrl = "", language = None, description = "")
        )
    }

  }
}
