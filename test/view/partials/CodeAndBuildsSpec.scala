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

import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, RepositoryDetails}
import uk.gov.hmrc.cataloguefrontend.connector.Link
import uk.gov.hmrc.cataloguefrontend.service.RouteRulesService
import uk.gov.hmrc.cataloguefrontend.service.RouteRulesService.EnvironmentRoute

class CodeAndBuildsSpec extends WordSpec with Matchers with GeneratorDrivenPropertyChecks {

  val repo = RepositoryDetails(
    name         = "reponame",
    description  = "",
    createdAt    = LocalDateTime.now(),
    lastActive   = LocalDateTime.now(),
    owningTeams  = Seq(),
    teamNames    = Seq(),
    githubUrl    = Link("repo1", "repository 1", "http://url"),
    ci           = Seq(),
    environments = None,
    repoType     = RepoType.Service,
    isPrivate    = true
  )

  val environmentRoute = EnvironmentRoute(
    environment = "EnvName",
    routes      = Seq(RouteRulesService.Route("TestUrl0", "TestUrl0", "ruleConfigurationUrl0"),
                      RouteRulesService.Route("TestUrl1", "TestUrl1", "ruleConfigurationUrl1"))
  )

  "code_and_builds" should {

    "display configuration explorer" in {
      val result = views.html.partials.code_and_builds(repo).body
      result should include ("href=\"reponame/config\"")
      result should include ("Config Explorer")
    }

    "display the service dependencies link when the repo type is a Service" in {
      val result = views.html.partials.code_and_builds(repo).body
      result should include("""href="/dependencies/reponame"""")
      result should include("Service Dependencies")
    }

    import org.scalacheck._
    "not display the service dependencies link when the repo type is anything other than a Service" in {
      forAll(Gen.oneOf(RepoType.Library, RepoType.Other, RepoType.Prototype)) { genRepoType =>
        val result = views.html.partials.code_and_builds(repo.copy(repoType = genRepoType)).body
        result should not include("""href="/dependencies/reponame"""")
        result should not include("Service Dependencies")
      }
    }

/*    "display routing rules" in {
      val result = views.html.partials.code_and_builds(repo).body
      result should include ("id=\"route-rule-0\"")
      result should include ("id=\"route-rule-1\"")
    }*/
  }

}
