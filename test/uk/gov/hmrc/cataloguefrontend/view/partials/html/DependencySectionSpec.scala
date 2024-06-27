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
    "display the version suffix when present" in {
      val res = dependency_section(Seq(dependency1), rootId).body
      res should include(s"""<div id="example-library-$rootId-current-version" class="col-3">1.2.3-play-25</div>""")
      res should include(s"""<div id="example-library-$rootId-latestVersion-version" class="col-3">""")
      res should include(s"""<span class="glyphicon medium-glyphicon glyphicon-arrow-right pe-2"></span><span>1.2.3-play-26</span>""")
    }

    "not display the version suffix when missing" in {
      val res = dependency_section(Seq(dependency2), rootId).body
      res should include(s"""<div id="library4j-$rootId-current-version" class="col-3">4.0.1</div>""")
      res should include("""<span class="glyphicon medium-glyphicon glyphicon-arrow-right pe-2"></span><span>4.2.0</span>""")
    }
  }
}
