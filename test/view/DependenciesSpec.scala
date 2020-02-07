/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.Instant

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.Matchers._
import org.scalatest.{Assertion, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.ViewMessages
import uk.gov.hmrc.cataloguefrontend.connector.model._
import views.html.partials.DependenciesPartial

class DependenciesSpec extends WordSpec with MockitoSugar {

  private[this] val viewMessages = mock[ViewMessages]

  "library and sbt plugin dependencies list" should {

    val dependencies = Dependencies(
      "service",
      Seq(
        Dependency("lib1-up-to-date", "uk.gov.hmrc", Version("1.0.0"), Some(Version("1.0.0"))),
        Dependency("lib2-minor-behind", "uk.gov.hmrc", Version("2.0.0"), Some(Version("2.1.0"))),
        Dependency("lib3-major-behind", "uk.gov.hmrc", Version("3.0.0"), Some(Version("4.0.0"))),
        Dependency("lib4-patch-behind", "uk.gov.hmrc", Version("3.0.0"), Some(Version("3.0.1"))),
        Dependency("lib5-no-latest-version", "uk.gov.hmrc", Version("3.0.0"), None),
        Dependency("lib6-invalid-ahead-current", "uk.gov.hmrc", Version("4.0.0"), Some(Version("3.0.1")))
      ),
      Seq(
        Dependency("plugin1-up-to-date", "uk.gov.hmrc", Version("1.0.0"), Some(Version("1.0.0"))),
        Dependency("plugin2-minor-behind", "uk.gov.hmrc", Version("2.0.0"), Some(Version("2.1.0"))),
        Dependency("plugin3-major-behind", "uk.gov.hmrc", Version("3.0.0"), Some(Version("4.0.0"))),
        Dependency("plugin4-patch-behind", "uk.gov.hmrc", Version("3.0.0"), Some(Version("3.0.1"))),
        Dependency("plugin5-no-latest-version", "uk.gov.hmrc", Version("3.0.0"), None),
        Dependency("plugin6-invalid-ahead-current", "uk.gov.hmrc", Version("4.0.0"), Some(Version("3.0.1")))
      ),
      Seq(Dependency("sbt", "uk.gov.hmrc", Version("0.13.11"), Some(Version("0.13.15")))),
      lastUpdated = Instant.now
    )

    "show green and ok icon if versions are the same" in {
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#lib1-up-to-date").get(0).text() shouldBe "lib1-up-to-date 1.0.0 1.0.0"
      verifyColour(document, "#lib1-up-to-date", "green")
      verifyIcon(document, "#lib1-up-to-date-icon", "glyphicon", "glyphicon-ok-circle")
      verifyTitle(document, "#lib1-up-to-date-icon", "up to date")

      document.select("#plugin1-up-to-date").get(0).text() shouldBe "plugin1-up-to-date 1.0.0 1.0.0"
      verifyColour(document, "#plugin1-up-to-date", "green")
      verifyIcon(document, "#plugin1-up-to-date-icon", "glyphicon", "glyphicon-ok-circle")
      verifyTitle(document, "#plugin1-up-to-date-icon", "up to date")
    }

    "show amber and alert icon if there is a minor version discrepancy" in {
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#lib2-minor-behind").get(0).text() shouldBe "lib2-minor-behind 2.0.0 2.1.0"
      verifyColour(document, "#lib2-minor-behind", "amber")
      verifyIcon(document, "#lib2-minor-behind-icon", "glyphicon", "glyphicon-alert")
      verifyTitle(document, "#lib2-minor-behind-icon", "minor version behind")

      document.select("#plugin2-minor-behind").get(0).text() shouldBe "plugin2-minor-behind 2.0.0 2.1.0"
      verifyColour(document, "#plugin2-minor-behind", "amber")
      verifyIcon(document, "#plugin2-minor-behind-icon", "glyphicon", "glyphicon-alert")
      verifyTitle(document, "#plugin2-minor-behind-icon", "minor version behind")
    }

    "show amber and alert icon if there is a patch version discrepancy" in {
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#lib4-patch-behind").get(0).text() shouldBe "lib4-patch-behind 3.0.0 3.0.1"
      verifyColour(document, "#lib4-patch-behind", "amber")
      verifyIcon(document, "#lib4-patch-behind-icon", "glyphicon", "glyphicon-alert")
      verifyTitle(document, "#lib4-patch-behind-icon", "minor version behind")

      document.select("#plugin4-patch-behind").get(0).text() shouldBe "plugin4-patch-behind 3.0.0 3.0.1"
      verifyColour(document, "#plugin4-patch-behind", "amber")
      verifyIcon(document, "#plugin4-patch-behind-icon", "glyphicon", "glyphicon-alert")
      verifyTitle(document, "#plugin4-patch-behind-icon", "minor version behind")

    }

    "show red and ban icon if there is a major version discrepancy" in {
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#lib3-major-behind").get(0).text() shouldBe "lib3-major-behind 3.0.0 4.0.0"
      verifyColour(document, "#lib3-major-behind", "red")
      verifyIcon(document, "#lib3-major-behind-icon", "glyphicon", "glyphicon-ban-circle")
      verifyTitle(document, "#lib3-major-behind-icon", "major version behind")

      document.select("#plugin3-major-behind").get(0).text() shouldBe "plugin3-major-behind 3.0.0 4.0.0"
      verifyColour(document, "#plugin3-major-behind", "red")
      verifyIcon(document, "#plugin3-major-behind-icon", "glyphicon", "glyphicon-ban-circle")
      verifyTitle(document, "#plugin3-major-behind-icon", "major version behind")
    }

    "show grey and question mark icon if there is no latest version available (not found)" in {
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#lib5-no-latest-version").get(0).text() shouldBe "lib5-no-latest-version 3.0.0 (not found)"
      verifyColour(document, "#lib5-no-latest-version", "grey")
      verifyIcon(document, "#lib5-no-latest-version-icon", "glyphicon", "glyphicon-question-sign")
      verifyTitle(document, "#lib5-no-latest-version-icon", "not found")

      document.select("#plugin5-no-latest-version").get(0).text() shouldBe "plugin5-no-latest-version 3.0.0 (not found)"
      verifyColour(document, "#plugin5-no-latest-version", "grey")
      verifyIcon(document, "#plugin5-no-latest-version-icon", "glyphicon", "glyphicon-question-sign")
      verifyTitle(document, "#plugin5-no-latest-version-icon", "not found")
    }

    "show black and question mark icon if versions are invalid (eg: current version > latest version) - (this scenario should not happen unless the reloading of the libraries' latest versions has been failing)" in {
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#lib6-invalid-ahead-current").get(0).text() shouldBe "lib6-invalid-ahead-current 4.0.0 3.0.1"
      verifyColour(document, "#lib6-invalid-ahead-current", "black")
      verifyIcon(document, "#lib6-invalid-ahead-current-icon", "glyphicon", "glyphicon-question-sign")
      verifyTitle(document, "#lib6-invalid-ahead-current-icon", "invalid version difference")

      document
        .select("#plugin6-invalid-ahead-current")
        .get(0)
        .text() shouldBe "plugin6-invalid-ahead-current 4.0.0 3.0.1"
      verifyColour(document, "#plugin6-invalid-ahead-current", "black")
      verifyIcon(document, "#lib6-invalid-ahead-current-icon", "glyphicon", "glyphicon-question-sign")
      verifyTitle(document, "#lib6-invalid-ahead-current-icon", "invalid version difference")

    }

  }

  private def verifyColour(document: Document, elementsCssSelector: String, colour: String): Unit =
    verifyCss(document, elementsCssSelector, colour)

  def verifyIcon(document: Document, elementsCssSelector: String, iconCssClasses: String*): Unit =
    verifyCss(document, elementsCssSelector, iconCssClasses: _*)

  def verifyTitle(document: Document, elementsCssSelector: String, title: String): Assertion = {
    val elements = document.select(elementsCssSelector)

    assert(
      elements.attr("title").contains(title),
      s"element title ($title) is not found : [${elements.text()}]"
    )

  }

  private def verifyCss(document: Document, elementsCssSelector: String, iconCssClasses: String*): Unit = {
    import collection.JavaConverters._
    val elements = document.select(elementsCssSelector)
    iconCssClasses.foreach { iconCssClass =>
      assert(
        elements.hasClass(iconCssClass),
        s"Css class($iconCssClasses) is not found in element's classes: [${elements.asScala.headOption.map(_.classNames().asScala).mkString(", ")}]"
      )
    }
  }

  "sbt dependency" should {

    "show green if versions are the same" in {
      val dependencies =
        Dependencies(
          "service",
          Nil,
          Nil,
          Seq(Dependency("sbt", "org.scala-sbt", Version("1.0.0"), Some(Version("1.0.0")))),
          lastUpdated = Instant.now)
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#sbt").get(0).text() shouldBe "sbt 1.0.0 1.0.0"
      verifyColour(document, "#sbt", "green")
      verifyIcon(document, "#sbt-icon", "glyphicon", "glyphicon-ok-circle")
    }

    "show amber if there is a minor version discrepancy" in {
      val dependencies =
        Dependencies(
          "service",
          Nil,
          Nil,
          Seq(Dependency("sbt", "org.scala-sbt", Version("1.0.0"), Some(Version("1.1.0")))),
          lastUpdated = Instant.now)
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#sbt").get(0).text() shouldBe "sbt 1.0.0 1.1.0"
      verifyColour(document, "#sbt", "amber")
      verifyIcon(document, "#sbt-icon", "glyphicon", "glyphicon-alert")

    }

    "show amber if there is a patch version discrepancy" in {
      val dependencies =
        Dependencies(
          "service",
          Nil,
          Nil,
          Seq(Dependency("sbt", "org.scala-sbt", Version("1.0.0"), Some(Version("1.0.1")))),
          lastUpdated = Instant.now)
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#sbt").get(0).text() shouldBe "sbt 1.0.0 1.0.1"
      verifyColour(document, "#sbt", "amber")
      verifyIcon(document, "#sbt-icon", "glyphicon", "glyphicon-alert")

    }

    "show red if there is a major version discrepancy" in {
      val dependencies =
        Dependencies(
          "service",
          Nil,
          Nil,
          Seq(Dependency("sbt", "org.scala-sbt", Version("1.0.0"), Some(Version("2.0.0")))),
          lastUpdated = Instant.now)
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#sbt").get(0).text() shouldBe "sbt 1.0.0 2.0.0"
      verifyColour(document, "#sbt", "red")
      verifyIcon(document, "#sbt-icon", "glyphicon", "glyphicon-ban-circle")

    }

    "show grey and (not found) if there is no latest version available" in {
      val dependencies = Dependencies(
        "service",
        Nil,
        Nil,
        Seq(Dependency("sbt", "org.scala-sbt", Version("1.0.0"), None)),
        lastUpdated = Instant.now)
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#sbt").get(0).text() shouldBe "sbt 1.0.0 (not found)"
      verifyColour(document, "#sbt", "grey")
      verifyIcon(document, "#sbt-icon", "glyphicon", "glyphicon-question-sign")

    }

    "show black if versions are invalid (eg: current version > latest version)" in {
      val dependencies =
        Dependencies(
          "service",
          Nil,
          Nil,
          Seq(Dependency("sbt", "org.scala-sbt", Version("5.0.0"), Some(Version("1.0.0")))),
          lastUpdated = Instant.now)
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#sbt").get(0).text() shouldBe "sbt 5.0.0 1.0.0"
      verifyColour(document, "#sbt", "black")
    }
  }

  "legend section" should {
    "be shown if least one library dependency entry exists" in {
      val dependencies = Dependencies(
        "service",
        Seq(Dependency("lib1-up-to-date", "uk.gov.hrmc", Version("1.0.0"), Some(Version("1.0.0")))),
        Nil,
        Nil,
        lastUpdated = Instant.now)
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      verifyLegendSectionIsShowing(document)
    }

    "be shown if at least one sbt dependency entry exists" in {
      val dependencies = Dependencies(
        "service",
        Nil,
        Seq(Dependency("plugin1-up-to-date", "uk.gov.hrmc", Version("1.0.0"), Some(Version("1.0.0")))),
        Nil,
        lastUpdated = Instant.now)
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      verifyLegendSectionIsShowing(document)
    }

    "be shown if at least one other dependency entry exists" in {
      val dependencies = Dependencies(
        "service",
        Nil,
        Nil,
        Seq(Dependency("sbt", "org.scala-sbt", Version("1.0.0"), None)),
        lastUpdated = Instant.now)
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      verifyLegendSectionIsShowing(document)
    }

    "not be shown if no dependency entry exists" in {
      val dependencies = Dependencies("service", Nil, Nil, Nil, lastUpdated = Instant.now)
      val document     = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#legend") shouldBe empty
    }
  }

  private def verifyLegendSectionIsShowing(document: Document) =
    document.select("#legend").get(0).text() shouldBe "Legend: Up to date Minor version behind Major version behind Bobby rule pending Bobby rule violation"

  private def asDocument(html: Html): Document = Jsoup.parse(html.toString())
}
