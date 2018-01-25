/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.{Link, RepoType, RepositoryDetails}
import views.partials.githubBadgeType

class GithubBadgeTypeSpec extends WordSpec with Matchers {

  "Github Badge" should {
    "be Internal if repository is on github enterprise regardless if private/public" in {
      val repo1 = aRepo.copy(
        isPrivate  = true,
        githubUrls = Set(aLink.copy(url = "https://github.tools.tax.service.gov.uk/orgs/HMRC/name"))
      )
      githubBadgeType(repo1) shouldBe "Internal"

      val repo2 = aRepo.copy(
        isPrivate  = false,
        githubUrls = Set(aLink.copy(url = "https://github.tools.tax.service.gov.uk/orgs/HMRC/name"))
      )
      githubBadgeType(repo2) shouldBe "Internal"
    }
    "be Public if repository is not private and on github.com" in {
      val repo = aRepo.copy(
        isPrivate  = false,
        githubUrls = Set(aLink.copy(url = "https://github.com/hmrc/name"))
      )

      githubBadgeType(repo) shouldBe "Public"
    }
    "be Private if repository is private and on github.com" in {
      val repo = aRepo.copy(
        isPrivate  = true,
        githubUrls = Set(aLink.copy(url = "https://github.com/hmrc/name"))
      )

      githubBadgeType(repo) shouldBe "Private"
    }
  }

  val aLink = Link("name", "display-name", "url")

  val aRepo = RepositoryDetails(
    name         = "name",
    description  = "description",
    createdAt    = LocalDateTime.now,
    lastActive   = LocalDateTime.now,
    teamNames    = Nil,
    githubUrls   = Set.empty,
    ci           = Nil,
    environments = None,
    repoType     = RepoType.Other,
    isPrivate    = false
  )

}
