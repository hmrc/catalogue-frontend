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

package uk.gov.hmrc.cataloguefrontend.service

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.cataloguefrontend.connector.RepoType.Service
import uk.gov.hmrc.cataloguefrontend.connector.model._
import uk.gov.hmrc.cataloguefrontend.connector.ServiceDependenciesConnector
import uk.gov.hmrc.cataloguefrontend.model.{SlugInfoFlag, TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SlugInfoServiceSpec
  extends UnitSpec
     with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val group        = "group"
  val artefact     = "artefact"
  val versionRange = BobbyVersionRange("[1.0.1,)")

  val v100 =
    ServiceWithDependency(
      repoName           = "service1",
      repoVersion        = Version("v1"),
      teams              = List(TeamName("T1")),
      depGroup           = group,
      depArtefact        = artefact,
      depVersion         = Version("1.0.0"),
      scopes             = Set(DependencyScope.Compile),
      repoType           = Service
    )

  val v200 =
    ServiceWithDependency(
      repoName           = "service1",
      repoVersion        = Version("v1"),
      teams              = List(TeamName("T1"), TeamName("T2")),
      depGroup           = group,
      depArtefact        = artefact,
      depVersion         = Version("2.0.0"),
      scopes             = Set(DependencyScope.Compile),
      repoType           = Service
    )

  val v205 =
    ServiceWithDependency(
      repoName           = "service1",
      repoVersion        = Version("v1"),
      teams              = List(TeamName("T2")),
      depGroup           = group,
      depArtefact        = artefact,
      depVersion         = Version("2.0.5"),
      scopes             = Set(DependencyScope.Compile),
      repoType           = Service
    )

  val scopes = List(DependencyScope.Compile)

  "DependenciesService.getServicesWithDependency" should {
    "filter results by team" in {

      val boot = Boot.init

      when(boot.mockedServiceDependenciesConnector.getDependenciesFromMetaData(SlugInfoFlag.Latest, group, artefact, List(Service), versionRange, scopes))
        .thenReturn(Future.successful(Seq(v100, v200, v205)))

      boot.service.getServicesWithDependency(optTeam = Some(TeamName("T1")), SlugInfoFlag.Latest, List(Service), group, artefact, versionRange, scopes).futureValue shouldBe Seq(v200, v100)
      boot.service.getServicesWithDependency(optTeam = Some(TeamName("T2")), SlugInfoFlag.Latest, List(Service), group, artefact, versionRange, scopes).futureValue shouldBe Seq(v205, v200)
    }
  }

  "DependenciesService.getJdkCountsForEnv" should {
    "return totals of each jdk in an environment" in {
      val boot = Boot.init

      val jdk1 = JdkVersion(name = "test1", version = Version("1.181.1"), vendor = Vendor.Oracle , kind = Kind.JDK)
      val jdk2 = JdkVersion(name = "test2", version = Version("1.181.1"), vendor = Vendor.Oracle , kind = Kind.JDK)
      val jdk3 = JdkVersion(name = "test3", version = Version("1.191.1"), vendor = Vendor.OpenJDK, kind = Kind.JRE)
      val jdk4 = JdkVersion(name = "test4", version = Version("1.121.1"), vendor = Vendor.OpenJDK, kind = Kind.JRE)

      when(boot.mockedServiceDependenciesConnector.getJdkVersions(teamName = None, flag = SlugInfoFlag.Latest))
        .thenReturn(Future.successful(List(jdk1, jdk2, jdk3, jdk4)))

      val res = boot.service.getJdkCountsForEnv(env = SlugInfoFlag.Latest, teamName = None).futureValue

      res.usage(JdkVersion("", Version("1.181.1"), Vendor.Oracle , Kind.JDK)) shouldBe 2
      res.usage(JdkVersion("", Version("1.191.1"), Vendor.OpenJDK, Kind.JDK)) shouldBe 1
      res.usage(JdkVersion("", Version("1.121.1"), Vendor.OpenJDK, Kind.JDK)) shouldBe 1
    }

    "still returns a value when no matches are found for env" in {
      val boot = Boot.init

      when(boot.mockedServiceDependenciesConnector.getJdkVersions(teamName = None, flag = SlugInfoFlag.Latest))
        .thenReturn(Future.successful(List.empty[JdkVersion]))

      boot.service.getJdkCountsForEnv(env = SlugInfoFlag.Latest, teamName = None).futureValue shouldBe JdkUsageByEnv(SlugInfoFlag.Latest, Map.empty[JdkVersion, Int])
    }
  }

  "DependenciesService.sortAndSeparateDependencies" should {
    "return the original dependency list and no transitive dependencies when theres no dot files" in {
      val depFoo = ServiceDependency("org.foo", "foo", "1.0.0")
      val serviceDeps = ServiceDependencies(
        uri           = "",
        name          = "test",
        version       = Version("1.0.0"),
        runnerVersion = "0.5.4",
        java          = ServiceJdkVersion("1.8.222", "openjdk", "jre"),
        classpath     = "",
        dependencies  = Seq(depFoo),
        environment   = None
      )
      DependenciesService.sortAndSeparateDependencies(serviceDeps) shouldBe ((Seq(depFoo), Seq.empty))
    }

    "return a list of direct and transitive dependencies when there is a dot file" in {
      val depFoo = ServiceDependency("org.foo", "foo", "1.0.0")
      val depBar = ServiceDependency("org.bar", "bar", "1.0.0")
      val serviceDeps = ServiceDependencies(
        uri                  = "",
        name                 = "test",
        version              = Version("1.0.0"),
        runnerVersion        = "0.5.4",
        java                 = ServiceJdkVersion("1.8.222", "openjdk", "jre"),
        classpath            = "",
        dependencies         = Seq(depFoo, depBar),
        environment          = None,
        dependencyDotCompile = Some(
          """
            |"org.foo:foo:1.0.0"[label=""]
            |"org.bar:bar:1.0.0"[label=""]
            |"root:root:0.0.0"[label=""]
            |"root:root:0.0.0" -> "org.foo:foo:1.0.0"
            |"org.foo:foo:1.0.0" -> "org.bar:bar:1.0.0"
            |""".stripMargin)
      )
      DependenciesService.sortAndSeparateDependencies(serviceDeps)._1 shouldBe Seq(depFoo)
      DependenciesService.sortAndSeparateDependencies(serviceDeps)._2 shouldBe Seq(TransitiveServiceDependency(depBar, depFoo))
    }
  }


  case class Boot(
    mockedServiceDependenciesConnector: ServiceDependenciesConnector,
    service                           : DependenciesService)

  object Boot {
    def init: Boot = {
      val mockedServiceDependenciesConnector = mock[ServiceDependenciesConnector]
      val dependenciesService = new DependenciesService(mockedServiceDependenciesConnector)
      Boot(
        mockedServiceDependenciesConnector,
        dependenciesService)
    }
  }
}
