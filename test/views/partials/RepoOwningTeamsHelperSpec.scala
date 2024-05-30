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

package views.partials

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, RepoType}
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName

import java.time.Instant

class RepoOwningTeamsHelperSpec extends AnyWordSpec with Matchers {

  "An RepoOwningTeamsHelper" should {
    "sort teamNames by owningTeams" in {
      val ghr = GitRepository(
        name               = "a",
        description        = "b",
        githubUrl          = "https://github.com/hmrc/a",
        createdDate        = Instant.now(),
        lastActiveDate     = Instant.now(),
        isPrivate          = false,
        repoType           = RepoType.Service,
        serviceType        = None,
        digitalServiceName = None,
        owningTeams        = Seq(TeamName("Zoo")),
        language           = None,
        isArchived         = false,
        defaultBranch      = "main",
        branchProtection   = None,
        isDeprecated       = false,
        teamNames          = Seq(TeamName("Foo"), TeamName("Bar"), TeamName("Baz"), TeamName("Buz"), TeamName("Zoo"))
      )

      RepoOwningTeamsHelper.teamNamesSortedByOwningTeam(ghr) shouldBe Seq(TeamName("Zoo"), TeamName("Bar"), TeamName("Baz"), TeamName("Buz"), TeamName("Foo"))
    }
  }
}
