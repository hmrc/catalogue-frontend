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
          |}""".stripMargin) shouldBe DependencyGraph.empty
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
        Arrow(Node("io.methvin:directory-watcher:0.10.1"), Node("net.java.dev.jna:jna:5.6.0"))
      )
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

    "Should be able to ignore packaging and scala version" in {
      DependencyGraphParser
        .parse(
          """
            |digraph "test" {
            |	"group:main_2.11:1.0.0" -> "group:component_2.11:jar:1.0.1"
            |	"group:subcomponent:1.0.2:compile" -> "group:component_2:1.0.3"
            |}""".stripMargin)
        .dependencies should contain theSameElementsAs Seq(
          ServiceDependency("group", "main", "1.0.0"),
          ServiceDependency("group", "component", "1.0.1"),
          ServiceDependency("group", "subcomponent", "1.0.2"),
          ServiceDependency("group", "component_2", "1.0.3")
        )
    }


    "Should be able to read maven generated graph" in {
      DependencyGraphParser
        .parse(
          """
            |digraph "uk.gov.hmrc.jdc:emcs:war:3.226.0" {
            |	"uk.gov.hmrc.jdc:emcs:war:3.226.0" -> "uk.gov.hmrc.rehoming:event-auditing:jar:2.0.0:compile" ;
            |	"uk.gov.hmrc.jdc:emcs:war:3.226.0" -> "org.springframework.ws:spring-ws-support:jar:2.1.4.RELEASE:compile" ;
            |	"uk.gov.hmrc.jdc:emcs:war:3.226.0" -> "uk.gov.hmrc.rehoming:rehoming-common:jar:7.41.0:compile" ;
            |	"uk.gov.hmrc.jdc:emcs:war:3.226.0" -> "xerces:xercesImpl:jar:2.12.0:compile" ;
            |}""".stripMargin)
        .nodes shouldBe Set(
          Node("uk.gov.hmrc.jdc:emcs:war:3.226.0"),
          Node("uk.gov.hmrc.rehoming:event-auditing:jar:2.0.0:compile"),
          Node("org.springframework.ws:spring-ws-support:jar:2.1.4.RELEASE:compile"),
          Node("uk.gov.hmrc.rehoming:rehoming-common:jar:7.41.0:compile"),
          Node("xerces:xercesImpl:jar:2.12.0:compile")
        )
    }

    "Should be able to read maven generated dependencies" in {
      DependencyGraphParser
        .parse(
          """
            |digraph "uk.gov.hmrc.jdc:emcs:war:3.226.0" {
            |	"uk.gov.hmrc.jdc:emcs:war:3.226.0" -> "uk.gov.hmrc.rehoming:event-auditing:jar:2.0.0:compile" ;
            |	"uk.gov.hmrc.jdc:emcs:war:3.226.0" -> "org.springframework.ws:spring-ws-support:jar:2.1.4.RELEASE:compile" ;
            |	"uk.gov.hmrc.jdc:emcs:war:3.226.0" -> "uk.gov.hmrc.rehoming:rehoming-common:jar:7.41.0:compile" ;
            |	"uk.gov.hmrc.jdc:emcs:war:3.226.0" -> "xerces:xercesImpl:jar:2.12.0:compile" ;
            |}""".stripMargin)
        .dependencies should contain theSameElementsAs Seq(
          ServiceDependency("org.springframework.ws", "spring-ws-support", "2.1.4.RELEASE"),
          ServiceDependency("uk.gov.hmrc.rehoming", "event-auditing", "2.0.0"),
          ServiceDependency("uk.gov.hmrc.jdc", "emcs", "3.226.0"),
          ServiceDependency("uk.gov.hmrc.rehoming", "rehoming-common", "7.41.0"),
          ServiceDependency("xerces", "xercesImpl", "2.12.0")
        )
    }

    "Should be able to read maven generated transitive dependencies" in {
      DependencyGraphParser
        .parse(
          """
            |digraph "uk.gov.hmrc.jdc:emcs:war:3.226.0" {
            | "uk.gov.hmrc.jdc:emcs:war:3.226.0" -> "uk.gov.hmrc.portal:reauthentication-client-app:jar:2.2.0:compile" ;
            |	"uk.gov.hmrc.portal:reauthentication-client-app:jar:2.2.0:compile" -> "uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile" ;
            |	"uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile" -> "uk.gov.hmrc.portal.iass:iass-data-protection:jar:1.3.1:compile" ;
            |	"uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile" -> "log4j:log4j:jar:1.2.17:compile" ;
            |	"uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile" -> "org.opensaml:opensaml-core:jar:3.3.0:compile" ;
            |	"uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile" -> "org.opensaml:opensaml-saml-impl:jar:3.3.0:compile" ;
            |	"uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile" -> "org.opensaml:opensaml-saml-api:jar:3.3.0:compile" ;
            | "org.opensaml:opensaml-core:jar:3.3.0:compile" -> "net.shibboleth.utilities:java-support:jar:7.3.0:compile" ;
            |}""".stripMargin)
        .arrows shouldBe Set(
          Arrow(Node("uk.gov.hmrc.jdc:emcs:war:3.226.0"),Node("uk.gov.hmrc.portal:reauthentication-client-app:jar:2.2.0:compile")),
          Arrow(Node("uk.gov.hmrc.portal:reauthentication-client-app:jar:2.2.0:compile"), Node("uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile")),
          Arrow(Node("uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile"), Node("uk.gov.hmrc.portal.iass:iass-data-protection:jar:1.3.1:compile")),
          Arrow(Node("uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile"), Node("log4j:log4j:jar:1.2.17:compile")),
          Arrow(Node("uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile"), Node("org.opensaml:opensaml-core:jar:3.3.0:compile")),
          Arrow(Node("uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile"), Node("org.opensaml:opensaml-saml-impl:jar:3.3.0:compile")),
          Arrow(Node("uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile"), Node("org.opensaml:opensaml-saml-api:jar:3.3.0:compile")),
          Arrow(Node("org.opensaml:opensaml-core:jar:3.3.0:compile"), Node("net.shibboleth.utilities:java-support:jar:7.3.0:compile")),
      )
    }

    "Should be able to separate maven generated transitive dependencies from direct ones" in {
      val emcs = ServiceDependency("uk.gov.hmrc.jdc", "emcs", "3.226.0")
      val reauthenticationClientApp = ServiceDependency("uk.gov.hmrc.portal", "reauthentication-client-app", "2.2.0")
      val reauthdecrypt = ServiceDependency("uk.gov.hmrc.portal", "reauthdecrypt", "3.3.0")
      val iassDataProtection = ServiceDependency("uk.gov.hmrc.portal.iass", "iass-data-protection", "1.3.1")
      val log4j = ServiceDependency("log4j", "log4j", "1.2.17")
      val opensamlCore = ServiceDependency("org.opensaml", "opensaml-core", "3.3.0")
      val opensamlSamlImpl = ServiceDependency("org.opensaml", "opensaml-saml-impl", "3.3.0")
      val opensamlSamlApi = ServiceDependency("org.opensaml", "opensaml-saml-api", "3.3.0")
      val javaSupport = ServiceDependency("net.shibboleth.utilities", "java-support", "7.3.0")

      DependencyGraphParser
        .parse(
          """
            |digraph "uk.gov.hmrc.jdc:emcs:war:3.226.0" {
            | "uk.gov.hmrc.jdc:emcs:war:3.226.0" -> "uk.gov.hmrc.portal:reauthentication-client-app:jar:2.2.0:compile" ;
            |	"uk.gov.hmrc.portal:reauthentication-client-app:jar:2.2.0:compile" -> "uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile" ;
            |	"uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile" -> "uk.gov.hmrc.portal.iass:iass-data-protection:jar:1.3.1:compile" ;
            |	"uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile" -> "log4j:log4j:jar:1.2.17:compile" ;
            |	"uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile" -> "org.opensaml:opensaml-core:jar:3.3.0:compile" ;
            |	"uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile" -> "org.opensaml:opensaml-saml-impl:jar:3.3.0:compile" ;
            |	"uk.gov.hmrc.portal:reauthdecrypt:jar:3.3.0:compile" -> "org.opensaml:opensaml-saml-api:jar:3.3.0:compile" ;
            | "org.opensaml:opensaml-core:jar:3.3.0:compile" -> "net.shibboleth.utilities:java-support:jar:7.3.0:compile" ;
            |}""".stripMargin)
        .dependenciesWithImportPath should contain theSameElementsAs Seq(
          (reauthenticationClientApp, Seq(reauthenticationClientApp, emcs)),
          (reauthdecrypt, Seq(reauthdecrypt, reauthenticationClientApp, emcs)),
          (iassDataProtection, Seq(iassDataProtection, reauthdecrypt, reauthenticationClientApp, emcs)),
          (log4j, Seq(log4j, reauthdecrypt, reauthenticationClientApp, emcs)),
          (opensamlCore, Seq(opensamlCore, reauthdecrypt, reauthenticationClientApp, emcs)),
          (opensamlSamlImpl, Seq(opensamlSamlImpl, reauthdecrypt, reauthenticationClientApp, emcs)),
          (opensamlSamlApi, Seq(opensamlSamlApi, reauthdecrypt, reauthenticationClientApp, emcs)),
          (javaSupport, Seq(javaSupport, opensamlCore, reauthdecrypt, reauthenticationClientApp, emcs))
        )
    }

    "Should work with trailing spaces" in {
      // note the trailing spaces and tabs
      val input = "digraph \"uk.gov.hmrc.jdc:platops-example-classic-service:war:0.53.0\" { \n" +
        "\t\"uk.gov.hmrc.jdc:platops-example-classic-service:war:0.53.0\" -> \"uk.gov.hmrc.jdc:platops-example-classic-service-business:jar:0.53.0:compile\" ; \n" +
        " } "
      val graph = DependencyGraphParser.parse(input)
      graph.dependencies should contain theSameElementsAs Seq(
        ServiceDependency("uk.gov.hmrc.jdc", "platops-example-classic-service", "0.53.0"),
        ServiceDependency("uk.gov.hmrc.jdc", "platops-example-classic-service-business", "0.53.0"),
      )
    }
  }
}
