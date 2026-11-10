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

package uk.gov.hmrc.cataloguefrontend.view.partials.html

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependency, DependencyScope}
import uk.gov.hmrc.cataloguefrontend.model.Version

class DependencySectionSpec extends AnyWordSpec with Matchers {

  val dependency1 = Dependency(
                      name           = "example-library"
                    , group          = "uk.gov.hmrc"
                    , currentVersion = Version("1.2.3-play-25")
                    , latestVersion  = Some(Version("1.2.3-play-26"))
                    , scope          = DependencyScope.Compile
                    )
  val dependency2 = Dependency(
                      name           = "library4j"
                    , group          = "uk.gov.hmrc"
                    , currentVersion = Version("4.0.1")
                    , latestVersion  = Some(Version("4.2.0"))
                    , scope          = DependencyScope.Compile
                    )
  val rootId = "rootId"

  "dependency_section" should {
    "show an HMRC dependency as plain text when its repository link flag is false" in {
      val dependency = dependency2.copy(name = "example-unregistered-library", group = "uk.gov.hmrc.example", currentVersion = Version("1.0.0"), hasRepositoryLink = false)
      val document = org.jsoup.Jsoup.parse(dependency_section(Seq(dependency), rootId).body)

      document.select(".dep-group").text() shouldBe "uk.gov.hmrc.example:"
      document.select(".dep-name").text() shouldBe "example-unregistered-library"
      document.select(s"#example-unregistered-library-$rootId-current-version").text() shouldBe "1.0.0"
      document.select(s"#link-to-example-unregistered-library-$rootId").isEmpty shouldBe true
    }

    "show a repository link with the full HMRC subgroup when a link is available" in {
      val dependency = dependency2.copy(group = "uk.gov.hmrc.example", hasRepositoryLink = true)
      val document = org.jsoup.Jsoup.parse(dependency_section(Seq(dependency), rootId).body)

      document.select(s"#link-to-library4j-$rootId").attr("href") should include("group=uk.gov.hmrc.example")
    }

    "show a repository link with the dependency's group, artefact and version" in {
      val dependency = dependency2.copy(
        name = "example-legacy-library",
        group = "uk.gov.hmrc.example",
        currentVersion = Version("2.0.0"),
        hasRepositoryLink = true
      )
      val document = org.jsoup.Jsoup.parse(dependency_section(Seq(dependency), rootId).body)

      document.select(s"#link-to-example-legacy-library-$rootId").attr("href") shouldBe
        "/dependency-repository?group=uk.gov.hmrc.example&artefact=example-legacy-library&version=2.0.0"
    }

    "show a repository link for a dependency in the base uk.gov.hmrc group" in {
      val document = org.jsoup.Jsoup.parse(dependency_section(Seq(dependency1), rootId).body)

      document.select(s"#link-to-example-library-$rootId").size() shouldBe 1
    }

    "show an external dependency as plain text even when its repository link flag is true" in {
      val document = org.jsoup.Jsoup.parse(dependency_section(Seq(dependency2.copy(group = "org.example")), rootId).body)

      document.select(".dep-name").text() shouldBe "library4j"
      document.select(s"#link-to-library4j-$rootId").isEmpty shouldBe true
    }

    "display the version suffix when present" in {
      val res = dependency_section(Seq(dependency1), rootId, showVulnerabilityColumn = false).body
      res should include(s"""<div id="example-library-$rootId-current-version" class="col-3">1.2.3-play-25</div>""")
      res should include(s"""<div id="example-library-$rootId-latestVersion-version" class="col-3">""")
      res should include(s"""<span class="glyphicon medium-glyphicon glyphicon-arrow-right pe-2"></span><span>1.2.3-play-26</span>""")
    }

    "not display the version suffix when missing" in {
      val res = dependency_section(Seq(dependency2), rootId, showVulnerabilityColumn = false).body
      res should include(s"""<div id="library4j-$rootId-current-version" class="col-3">4.0.1</div>""")
      res should include("""<span class="glyphicon medium-glyphicon glyphicon-arrow-right pe-2"></span><span>4.2.0</span>""")
    }
  }
}
