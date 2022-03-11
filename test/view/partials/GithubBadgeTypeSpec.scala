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

package view.partials

import java.time.LocalDateTime
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, Link, RepoType}
import views.partials.githubBadgeType

class GithubBadgeTypeSpec extends AnyWordSpec with Matchers {

  "Github Badge" should {
    "be Open if repository is not private" in {
      val repo = aRepo.copy(
        isPrivate = false,
        githubUrl = "https://github.com/hmrc/name"
      )

      githubBadgeType(repo) shouldBe "Public"
    }
    "be Private if repository is private" in {
      val repo = aRepo.copy(
        isPrivate = true,
        githubUrl = "https://github.com/hmrc/name"
      )

      githubBadgeType(repo) shouldBe "Private"
    }
  }

  val aLink = Link("name", "display-name", "url")

  val aRepo = GitRepository(
    name          = "name",
    description   = "description",
    createdDate    = LocalDateTime.now,
    lastActiveDate = LocalDateTime.now,
    owningTeams   = Nil,
    teamNames     = Nil,
    githubUrl     = "https://github.com/hmrc/name",
    jenkinsURL    = None,
    repoType      = RepoType.Other,
    isPrivate     = false,
    isArchived    = false,
    defaultBranch = "main",
    language      = None
  )

}
