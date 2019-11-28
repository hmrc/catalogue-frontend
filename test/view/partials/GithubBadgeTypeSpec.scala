/*
 * Copyright 2019 HM Revenue & Customs
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
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cataloguefrontend.connector.{Link, RepoType, RepositoryDetails}
import views.partials.githubBadgeType

class GithubBadgeTypeSpec extends WordSpec with Matchers {

  "Github Badge" should {
    "be Open if repository is not private" in {
      val repo = aRepo.copy(
        isPrivate = false,
        githubUrl = aLink.copy(url = "https://github.com/hmrc/name")
      )

      githubBadgeType(repo) shouldBe "Open"
    }
    "be Private if repository is private" in {
      val repo = aRepo.copy(
        isPrivate = true,
        githubUrl = aLink.copy(url = "https://github.com/hmrc/name")
      )

      githubBadgeType(repo) shouldBe "Private"
    }
  }

  val aLink = Link("name", "display-name", "url")

  val aRepo = RepositoryDetails(name         = "name", description  = "description", createdAt    = LocalDateTime.now, lastActive   = LocalDateTime.now, owningTeams  = Nil, teamNames    = Nil, githubUrl    = Link("github-com", "GitHub.com", "https://github.com/hmrc/name"), jenkinsURL           = None, environments = None, repoType     = RepoType.Other, isPrivate    = false)

}
