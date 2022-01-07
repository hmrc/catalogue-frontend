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

package uk.gov.hmrc.cataloguefrontend.service

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.connector.{RepositoryWithLeaks, RepoType, Team}
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName

import scala.concurrent.ExecutionContext

class LeakDetectionServiceSpec extends AnyWordSpec with Matchers {
  import ExecutionContext.Implicits.global

  "Service" should {
    "determine if at least one of team's repos has leaks" in new Setup {
      val team = Team(
        name           = TeamName("team0"),
        createdDate    = None,
        lastActiveDate = None,
        repos          = Some(Map(RepoType.Library -> List("repo1", "repo2")))
      )

      val repoWithLeak = RepositoryWithLeaks("repo1")

      service.teamHasLeaks(team, Seq(repoWithLeak)) shouldBe true
    }

    "determine if a team has no leaks" in new Setup {
      val team = Team(
        name            = TeamName("team0"),
        createdDate     = None,
        lastActiveDate  = None,
        repos           = Some(Map(RepoType.Library -> List("repo1", "repo2")))
      )

      val repoWithLeak = RepositoryWithLeaks("repo3")

      service.teamHasLeaks(team, Seq(repoWithLeak)) shouldBe false
    }

    "filter repositories in the exclusion list" in new Setup {
      val team = Team(
        name            = TeamName("team0"),
        createdDate     = None,
        lastActiveDate  = None,
        repos           = Some(Map(RepoType.Library -> List("a-repo-to-ignore")))
      )

      val repoToIgnore = RepositoryWithLeaks("a-repo-to-ignore")

      service.teamHasLeaks(team, Seq(repoToIgnore)) shouldBe false
    }
  }

  private trait Setup {

    private val configuration =
      Configuration(
        "lds.publicUrl"          -> "",
        "lds.integrationEnabled" -> "true",
        "lds.noWarningsOn.0"     -> "a-repo-to-ignore"
      )

    val service = new LeakDetectionService(null, configuration)
  }
}
