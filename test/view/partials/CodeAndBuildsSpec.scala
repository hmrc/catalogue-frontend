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
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import uk.gov.hmrc.cataloguefrontend.{CatalogueFrontendSwitches, FeatureSwitch}
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, RepositoryDetails}
import uk.gov.hmrc.cataloguefrontend.connector.Link

class CodeAndBuildsSpec extends WordSpec with Matchers {

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
    repoType     = RepoType.Other,
    isPrivate    = true
  )

  "code_and_builds" should {

    "display configuration explorer when feature flag is enabled" in {
      FeatureSwitch.enable(CatalogueFrontendSwitches.configExplorer)
      val result = views.html.partials.code_and_builds(repo).body
      result should include ("href=\"reponame/config\"")
      result should include ("Config Explorer")
    }

    "not display config explorer link when feature flag is disabled" in {
      FeatureSwitch.disable(CatalogueFrontendSwitches.configExplorer)
      val result = views.html.partials.code_and_builds(repo).body
      result should not include ("href=\"reponame/config\"")
      result should not include ("Config Explorer")
    }
  }

}
