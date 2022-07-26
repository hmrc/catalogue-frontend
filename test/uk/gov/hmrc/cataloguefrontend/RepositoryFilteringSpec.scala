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

package uk.gov.hmrc.cataloguefrontend

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.SearchFiltering._
import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, RepoType}
import uk.gov.hmrc.cataloguefrontend.repositories.RepoListFilter

import java.time.Instant

class RepositoryFilteringSpec extends AnyWordSpec with Matchers {

  "RepositoryFiltering" should {
    "get repositories filtered by only repository name, and default to Service repos" in {
      val now = Instant.now()

      val repositories = Seq(
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv2", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv3", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = "")
      )

      repositories.filter(RepoListFilter(name       = Some("serv1"))) shouldBe Seq(
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
      )
    }

    "get repositories filtered by partial repository name and case insensitive" in {
      val now = Instant.now()

      val repositories = Seq(
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv2", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("FOO", createdDate   = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "",  language = None, description = "")
      )

      repositories.filter(RepoListFilter(name       = Some("eRV"))) shouldBe Seq(
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv2", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
      )
    }

    "get repositories filtered by only repository type" in {
      val now = Instant.now()

      val repositories = Seq(
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv2", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv3", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = "")
      )

      repositories.filter(RepoListFilter(repoType   = Some("Other"))) shouldBe Seq(
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = "")
      )
    }

    "get repositories filtered repository type using 'service' as type " in {
      val now = Instant.now()

      val repositories = Seq(
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv2", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv3", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = "")
      )

      repositories.filter(RepoListFilter(repoType   = Some("service"))) shouldBe Seq(
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = "")
      )
    }

    "get repositories filtered repository type using 'deployable' as type " in {
      val now = Instant.now()

      val repositories = Seq(
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv2", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv3", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = "")
      )

      repositories.filter(RepoListFilter(repoType   = Some("Service"))) shouldBe Seq(
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = "")
      )
    }

    "get repositories filtered by both name and repository type" in {
      val now = Instant.now()

      val repositories = Seq(
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv2", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv4", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv3", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = "")
      )

      repositories.filter(RepoListFilter(name       = Some("serv1"), repoType = Some("Other"))) shouldBe Seq(
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = "")
      )
    }

    "get all repositories when team and RepoTypes are set to 'all'" in {
      val now = Instant.now()

      val repositories = Seq(
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv2", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv1", createdDate = now, lastActiveDate = now, repoType = RepoType.Other  , isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = ""),
        GitRepository("serv3", createdDate = now, lastActiveDate = now, repoType = RepoType.Service, isArchived = false,
          teamNames = Seq("team1"), defaultBranch = "main", githubUrl = "", language = None, description = "")
      )

      repositories.filter(RepoListFilter(team=Some("All"), repoType = Some("All"))) shouldBe repositories
    }
  }
}
