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

package uk.gov.hmrc.cataloguefrontend.view

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, Link, RepoType}
import uk.gov.hmrc.cataloguefrontend.model.TeamName

import java.time.Instant

class ViewHelperSpec extends AnyWordSpec with Matchers {

  "ViewHelper.githubBadgeType" should {
    "be Open if repository is not private" in {
      val repo = aRepo.copy(
        isPrivate = false,
        githubUrl = "https://github.com/hmrc/name"
      )

      ViewHelper.githubBadgeType(repo) shouldBe "Public"
    }
    "be Private if repository is private" in {
      val repo = aRepo.copy(
        isPrivate = true,
        githubUrl = "https://github.com/hmrc/name"
      )

      ViewHelper.githubBadgeType(repo) shouldBe "Private"
    }
  }

  "ViewHelper.teamNamesSortedByOwningTeam" should {
    "sort teamNames by owningTeams" in {
      val ghr = aRepo.copy(
        owningTeams        = Seq(TeamName("Zoo")),
        teamNames          = Seq(TeamName("Foo"), TeamName("Bar"), TeamName("Baz"), TeamName("Buz"), TeamName("Zoo"))
      )

      ViewHelper.teamNamesSortedByOwningTeam(ghr) shouldBe Seq(TeamName("Zoo"), TeamName("Bar"), TeamName("Baz"), TeamName("Buz"), TeamName("Foo"))
    }
  }

  val aLink = Link("name", "display-name", "url")

  val aRepo = GitRepository(
    name           = "name",
    description    = "description",
    createdDate    = Instant.now,
    lastActiveDate = Instant.now,
    owningTeams    = Nil,
    teamNames      = Nil,
    githubUrl      = "https://github.com/hmrc/name",
    repoType       = RepoType.Other,
    isPrivate      = false,
    isArchived     = false,
    defaultBranch  = "main",
    language       = None
  )

}
