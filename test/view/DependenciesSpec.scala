/*
 * Copyright 2021 HM Revenue & Customs
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
import org.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.twirl.api.Html
import uk.gov.hmrc.cataloguefrontend.ViewMessages
import uk.gov.hmrc.cataloguefrontend.connector.model._
import views.html.partials.DependenciesPartial

class DependenciesSpec extends AnyWordSpec with Matchers with MockitoSugar {

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

    "show version-ok if versions are the same" in {
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#lib1-up-to-date").get(0).text() shouldBe "uk.gov.hmrc:lib1-up-to-date 1.0.0 1.0.0"
      document.select("#lib1-up-to-date").hasClass("version-ok") shouldBe true

      import collection.JavaConverters._
      println("a) " + document)
      println("b) " + document.select("#lib1-up-to-date-icon"))
      println("c) " + document.select("#lib1-up-to-date-icon").asScala.headOption.map(_.classNames().asScala).mkString(", "))

      document.select("#plugin1-up-to-date").get(0).text() shouldBe "uk.gov.hmrc:plugin1-up-to-date 1.0.0 1.0.0"
      document.select("#plugin1-up-to-date").hasClass("version-ok") shouldBe true
    }

    "show version-new-available and alert icon if there is a new version available" in {
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#lib2-minor-behind").get(0).text() shouldBe "uk.gov.hmrc:lib2-minor-behind 2.0.0 2.1.0"
      document.select("#lib2-minor-behind").hasClass("version-new-available") shouldBe true
      document.select("#lib2-minor-behind-icon").hasClass("glyphicon-alert") shouldBe true
      document.select("#lib2-minor-behind-icon").attr("title").contains("minor version behind")

      document.select("#plugin2-minor-behind").get(0).text() shouldBe "uk.gov.hmrc:plugin2-minor-behind 2.0.0 2.1.0"
      document.select("#plugin2-minor-behind").hasClass("version-new-available") shouldBe true
      document.select("#plugin2-minor-behind-icon").hasClass("glyphicon-alert") shouldBe true
      document.select("#plugin2-minor-behind-icon").attr("title").contains("minor version behind")
    }
  }

  "sbt dependency" should {
    "show version-ok if versions are the same" in {
      val dependencies =
        Dependencies(
          "service",
          Nil,
          Nil,
          Seq(Dependency("sbt", "org.scala-sbt", Version("1.0.0"), Some(Version("1.0.0")))),
          lastUpdated = Instant.now)
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#sbt").get(0).text() shouldBe "org.scala-sbt:sbt 1.0.0 1.0.0"
      document.select("#sbt").hasClass("version-ok") shouldBe true
    }

    "show version-new-available if there is a minor version discrepancy" in {
      val dependencies =
        Dependencies(
          "service",
          Nil,
          Nil,
          Seq(Dependency("sbt", "org.scala-sbt", Version("1.0.0"), Some(Version("1.1.0")))),
          lastUpdated = Instant.now)
      val document = asDocument(new DependenciesPartial(viewMessages)(Some(dependencies)))

      document.select("#sbt").get(0).text() shouldBe "org.scala-sbt:sbt 1.0.0 1.1.0"
      document.select("#sbt").hasClass("version-new-available") shouldBe true
      document.select("#sbt-icon").hasClass("glyphicon-alert") shouldBe true
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
    document.select("#legend").get(0).text() shouldBe "Legend: New version available Bobby rule pending Bobby rule violation"

  private def asDocument(html: Html): Document = Jsoup.parse(html.toString())
}
