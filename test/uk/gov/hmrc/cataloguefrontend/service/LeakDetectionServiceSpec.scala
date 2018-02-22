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

package uk.gov.hmrc.cataloguefrontend.service

import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.Team
import uk.gov.hmrc.cataloguefrontend.connector.RepositoryWithLeaks

class LeakDetectionServiceSpec extends WordSpec with Matchers with PropertyChecks {

  "Service" should {
    "determine if at least one of team's repos has leaks" in {
      val reposWithLeaks = List(RepositoryWithLeaks("repo2"))
      val allTeamsInfo =
        List(
          Team(
            name                     = "team0",
            firstActiveDate          = None,
            lastActiveDate           = None,
            firstServiceCreationDate = None,
            repos                    = Some(Map("library" -> List("repo1", "repo2"))),
            ownedRepos               = List("repo2")
          ),
          Team(
            name                     = "team1",
            firstActiveDate          = None,
            lastActiveDate           = None,
            firstServiceCreationDate = None,
            repos                    = Some(Map("library" -> List("repo2"))),
            ownedRepos               = Nil
          ),
          Team(
            name                     = "team2",
            firstActiveDate          = None,
            lastActiveDate           = None,
            firstServiceCreationDate = None,
            repos                    = None,
            ownedRepos               = List("repo3"))
        )

      val service = new LeakDetectionService(null, configuration)

      val (ownsDirectly :: contributesButOtherTeamOwns :: ownsButNoLeaks :: Nil) = allTeamsInfo

      service.teamHasLeaks(ownsDirectly, allTeamsInfo, reposWithLeaks)                shouldBe true
      service.teamHasLeaks(contributesButOtherTeamOwns, allTeamsInfo, reposWithLeaks) shouldBe false
      service.teamHasLeaks(ownsButNoLeaks, allTeamsInfo, reposWithLeaks)              shouldBe false
    }

    "determine if a team is responsible for a repo" in {
      val service = new LeakDetectionService(null, configuration)

      val leakDetectionBannerScenarios =
        // format: off
        Table(
          ("contributes", "owns", "owned by others", "is responsible"),
          (false,         false,  false,              false         ),
          (false,         false,  true,               false         ),
          (false,         true,   false,              true          ),
          (false,         true,   true,               true          ),
          (true,          false,  false,              true          ),
          (true,          false,  true,               false         ),
          (true,          true,   false,              true          ),
          (true,          true,   true,               true          )
        )
      // format: on

      forAll(leakDetectionBannerScenarios) {
        case (contributes, owns, ownedByOthers, isResponsible) =>
          service.isTeamResponsibleForRepo(contributes, owns, ownedByOthers) shouldBe isResponsible
      }
    }
  }

  val configuration =
    Configuration(
      "microservice.services.leak-detection.productionUrl" -> "",
      "ldsIntegration.enabled"                             -> "false"
    )
}
