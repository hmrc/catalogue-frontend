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

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependency, Version}

class DependencySectionSpec extends WordSpec with Matchers {

  val dependency1 = Dependency("example-library", Version("1.2.3-play-25"), Some(Version("1.2.3-play-26")))
  val dependency2 = Dependency("library4j", Version("4.0.1"), Some(Version("4.2.0")))

  "dependency_section" should {

    "display the version suffix when present" in {
      val res = views.html.partials.dependency_section(Seq(dependency1)).body
      res should include("""<span id="example-library-current-version" class="col-xs-3">1.2.3-play-25</span>""")
      res should include(
        """<span id="example-library-latestVersion-version" class="col-xs-3">
        |                    <span class="glyphicon glyphicon-arrow-right small-glyphicon" style="padding-right: 10px;"> </span>
        |                    1.2.3-play-26
        |                </span>""".stripMargin)
    }

    "not display the version suffix when missing" in {
      val res = views.html.partials.dependency_section(Seq(dependency2)).body
      res should include("""<span id="library4j-current-version" class="col-xs-3">4.0.1</span>""")
      res should include(
        """<span id="library4j-latestVersion-version" class="col-xs-3">
        |                    <span class="glyphicon glyphicon-arrow-right small-glyphicon" style="padding-right: 10px;"> </span>
        |                    4.2.0
        |                </span>""".stripMargin)
    }
  }

}
