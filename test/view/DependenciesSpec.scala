/*
 * Copyright 2017 HM Revenue & Customs
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

package view

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.play.OneAppPerTest
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependencies, LibraryDependencyState, Version}

class DependenciesSpec  extends WordSpec with Matchers with OneAppPerTest {


  def asDocument(html: Html): Document = Jsoup.parse(html.toString())

  "deployments_list" should {

    val dependencies = Dependencies("service", Seq(
      LibraryDependencyState("lib1-up-to-date", Version(1, 0, 0), Some(Version(1, 0, 0))),
      LibraryDependencyState("lib2-minor-behind", Version(2, 0, 0), Some(Version(2, 1, 0))),
      LibraryDependencyState("lib3-major-behind", Version(3, 0, 0), Some(Version(4, 0, 0))),
      LibraryDependencyState("lib4-patch-behind", Version(3, 0, 0), Some(Version(3, 0, 1))),
      LibraryDependencyState("lib5-no-latest-version", Version(3, 0, 0), None)
    ))


    "show green if versions are the same" in {
      val document = asDocument(views.html.partials.dependencies(Some(dependencies)))

      document.select("#lib1-up-to-date").get(0).text() shouldBe "lib1-up-to-date 1.0.0 1.0.0"

      document.select("#lib1-up-to-date").hasClass("green") shouldBe true
    }

    "show amber if there is a minor version discrepancy" in {
      val document = asDocument(views.html.partials.dependencies(Some(dependencies)))

      document.select("#lib2-minor-behind").get(0).text() shouldBe "lib2-minor-behind 2.0.0 2.1.0"

      document.select("#lib2-minor-behind").hasClass("amber") shouldBe true
    }

    "show amber if there is a patch version discrepancy" in {
      val document = asDocument(views.html.partials.dependencies(Some(dependencies)))

      document.select("#lib4-patch-behind").get(0).text() shouldBe "lib4-patch-behind 3.0.0 3.0.1"

      document.select("#lib4-patch-behind").hasClass("amber") shouldBe true
    }

    "show red if there is a major version discrepancy" in {
      val document = asDocument(views.html.partials.dependencies(Some(dependencies)))

      document.select("#lib3-major-behind").get(0).text() shouldBe "lib3-major-behind 3.0.0 4.0.0"

      document.select("#lib3-major-behind").hasClass("red") shouldBe true
    }

    "show grey and (not found) if there is no latest version available" in {
      val document = asDocument(views.html.partials.dependencies(Some(dependencies)))

      document.select("#lib5-no-latest-version").get(0).text() shouldBe "lib5-no-latest-version 3.0.0 (not found)"

      document.select("#lib5-no-latest-version").hasClass("grey") shouldBe true
    }
  }

}
