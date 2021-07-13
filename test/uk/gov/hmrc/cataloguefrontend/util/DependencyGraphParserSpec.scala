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

package uk.gov.hmrc.cataloguefrontend.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.cataloguefrontend.service.ServiceDependency
import uk.gov.hmrc.cataloguefrontend.util.DependencyGraphParser.{Arrow, DependencyGraph, Node}

class DependencyGraphParserSpec extends AnyWordSpecLike with Matchers {

  "The Graph Parser" should {
    "ignore non-node & arrow lines" in {
      DependencyGraphParser.parse(
        """digraph "dependency-graph" {
          |    graph[rankdir="LR"]
          |    edge [
          |      arrowtail="none"
          |    ]
          |}""".stripMargin) shouldBe  DependencyGraph.empty
    }

    "Extract a set of nodes" in {
      DependencyGraphParser
        .parse(
          """"com.typesafe:config:1.4.1"[label=<com.typesafe<BR/><B>config</B><BR/>1.4.1> style=""]
            |"org.yaml:snakeyaml:1.25"[label=<org.yaml<BR/><B>snakeyaml</B><BR/>1.25> style=""]""".stripMargin)
        .nodes shouldBe Set(Node("com.typesafe:config:1.4.1"), Node("org.yaml:snakeyaml:1.25"))
    }

    "Extract a set of arrows pointing to two nodes" in {
      DependencyGraphParser.parse(
        """    "uk.gov.hmrc:sbt-auto-build:2.13.0" -> "org.yaml:snakeyaml:1.25"
          |    "io.methvin:directory-watcher:0.10.1" -> "net.java.dev.jna:jna:5.6.0"""".stripMargin
      ).arrows shouldBe Set(
        Arrow(Node("uk.gov.hmrc:sbt-auto-build:2.13.0"), Node("org.yaml:snakeyaml:1.25")),
        Arrow(Node("io.methvin:directory-watcher:0.10.1"), Node("net.java.dev.jna:jna:5.6.0")))
    }

    "Drops nodes with a style tag indicating they've been evicted" in {
      DependencyGraphParser
        .parse(
          """"com.typesafe:config:1.4.1"[label=<com.typesafe<BR/><B>config</B><BR/>1.4.1> style="stroke-dasharray: 5,5"]
            |"org.yaml:snakeyaml:1.25"[label=<org.yaml<BR/><B>snakeyaml</B><BR/>1.25> style=""]""".stripMargin)
        .nodes shouldBe Set(Node("org.yaml:snakeyaml:1.25"))
    }

    "Separates direct dependencies from transitive ones" in {

      val root = ServiceDependency("foo", "root", "1.0.0")
      val bar = ServiceDependency("foo", "bar", "1.0.0")
      val baz = ServiceDependency("foo", "baz", "0.1.1")

      DependencyGraphParser.parse(
        """    "foo:root:1.0.0" -> "foo:bar:1.0.0"
          |    "foo:bar:1.0.0" -> "foo:baz:0.1.1"
          |    "foo:bar:1.0.0"[label=a style=""]
          |    "foo:baz:0.1.1"[label=a style=""]
          |"""".stripMargin
      ).dependenciesWithImportPath shouldBe (
        Seq(
          (bar, Seq(bar, root))
        , (baz, Seq(baz, bar, root))
        )
      )
    }

  }

}
