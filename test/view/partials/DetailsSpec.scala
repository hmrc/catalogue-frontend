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

package view.partials

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, RepoType, RouteRulesConnector}

import java.time.Instant

class DetailsSpec extends AnyWordSpec with Matchers {

  val repo = GitRepository(
    name           = "reponame",
    description    = "some description",
    createdDate    = Instant.parse("2018-12-31T08:30:30.00Z"),
    lastActiveDate = Instant.parse("2018-12-31T18:30:30.00Z"),
    owningTeams    = Seq(),
    teamNames      = Seq(),
    githubUrl      = "http://url",
    language       = None,
    repoType       = RepoType.Other,
    isPrivate      = true,
    isArchived     = false,
    defaultBranch  = "main"
  )

  val environmentRoute = RouteRulesConnector.EnvironmentRoute(
    environment = "EnvName",
    routes      = Seq(RouteRulesConnector.Route("TestUrl0", "ruleConfigurationUrl0"),
                      RouteRulesConnector.Route("TestUrl1", "ruleConfigurationUrl1"))
  )

  "details" should {
    "display description when available" in {
      val result = views.html.partials.details(repo).body
      result should include ("some description")
    }

    "should not display description when it is not available" in {
      val result = views.html.partials.details(repo.copy(description = "")).body
      result should not include ("some description")
    }

    "display Created At Date" in {
      val result = views.html.partials.details(repo).body
      result should include ("id=\"created-at\"")
      result should include ("31 Dec 2018 08:30")
    }

    "display Last Active Date" in {
      val result = views.html.partials.details(repo).body
      result should include ("id=\"last-active\"")
      result should include ("31 Dec 2018 18:30")
    }
  }
}
