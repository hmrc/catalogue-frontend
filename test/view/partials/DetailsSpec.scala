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
import uk.gov.hmrc.cataloguefrontend.service.RouteRulesService
import uk.gov.hmrc.cataloguefrontend.service.RouteRulesService.EnvironmentRoute
import uk.gov.hmrc.cataloguefrontend.{CatalogueFrontendSwitches, FeatureSwitch}

class DetailsSpec extends WordSpec with Matchers {

  val repo = RepositoryDetails(
    name         = "reponame",
    description  = "some description",
    createdAt    = LocalDateTime.of(2018, 12, 31, 8, 30, 30),
    lastActive   = LocalDateTime.of(2018, 12, 31, 18, 30, 30),
    owningTeams  = Seq(),
    teamNames    = Seq(),
    githubUrl    = Link("repo1", "repository 1", "http://url"),
    ci           = Seq(),
    environments = None,
    repoType     = RepoType.Other,
    isPrivate    = true
  )

  val environmentRoute = EnvironmentRoute(
    environment = "EnvName",
    routes      = Seq(RouteRulesService.Route("TestUrl0", "TestUrl0", "ruleConfigurationUrl0"),
                      RouteRulesService.Route("TestUrl1", "TestUrl1", "ruleConfigurationUrl1"))
  )

  "details" should {
    "display description when available" in {
      FeatureSwitch.enable(CatalogueFrontendSwitches.routingRules)
      val result = views.html.partials.details(repo).body
      result should include ("some description")
    }

    "should not display description when it is not available" in {
      FeatureSwitch.enable(CatalogueFrontendSwitches.routingRules)
      val result = views.html.partials.details(repo.copy(description = "")).body
      result should not include ("some description")
    }

    "display Created At Date" in {
      FeatureSwitch.enable(CatalogueFrontendSwitches.routingRules)
      val result = views.html.partials.details(repo).body
      result should include ("id=\"created-at\"")
      result should include ("31 Dec 2018 08:30")
    }

    "display Last Active Date" in {
      FeatureSwitch.enable(CatalogueFrontendSwitches.routingRules)
      val result = views.html.partials.details(repo).body
      result should include ("id=\"last-active\"")
      result should include ("31 Dec 2018 18:30")
    }
  }
}
