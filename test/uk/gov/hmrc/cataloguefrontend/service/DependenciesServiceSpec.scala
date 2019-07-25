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

package uk.gov.hmrc.cataloguefrontend.service

import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.cataloguefrontend.connector.{ServiceDependenciesConnector, SlugInfoFlag}
import uk.gov.hmrc.cataloguefrontend.connector.model._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SlugInfoServiceSpec
  extends UnitSpec
     with MockitoSugar {

  implicit val hc = mock[HeaderCarrier]

  val group        = "group"
  val artefact     = "artefact"
  val versionRange = BobbyVersionRange("[1.0.1,)")

  val v100 =
    ServiceWithDependency(
      slugName           = "service1",
      slugVersion        = "v1",
      teams              = List("T1"),
      depGroup           = group,
      depArtefact        = artefact,
      depVersion         = "1.0.0",
      depSemanticVersion = Version.parse("1.0.0"))

  val v200 =
    ServiceWithDependency(
      slugName           = "service1",
      slugVersion        = "v1",
      teams              = List("T1", "T2"),
      depGroup           = group,
      depArtefact        = artefact,
      depVersion         = "2.0.0",
      depSemanticVersion = Version.parse("2.0.0"))

  val v205 =
    ServiceWithDependency(
      slugName           = "service1",
      slugVersion        = "v1",
      teams              = List("T2"),
      depGroup           = group,
      depArtefact        = artefact,
      depVersion         = "2.0.5",
      depSemanticVersion = Version.parse("2.0.5"))

  "DependenciesService.getServicesWithDependency" should {
    "filter results by team" in {

      val boot = Boot.init

      when(boot.mockedServiceDependenciesConnector.getServicesWithDependency(SlugInfoFlag.Latest, group, artefact, versionRange))
        .thenReturn(Future(Seq(v100, v200, v205)))

      await(boot.service.getServicesWithDependency(optTeam = Some("T1"), SlugInfoFlag.Latest, group, artefact, versionRange)) shouldBe Seq(v200, v100)
      await(boot.service.getServicesWithDependency(optTeam = Some("T2"), SlugInfoFlag.Latest, group, artefact, versionRange)) shouldBe Seq(v205, v200)
    }
  }

  "DependenciesService.getJDKCountsForEnv" should {
    "return totals of each jdk in an environment" in {
      val boot = Boot.init

      when(boot.mockedServiceDependenciesConnector.getJDKVersions(SlugInfoFlag.Latest))
        .thenReturn(Future(List(
          JDKVersion(name = "test1", version = "1.181.1", vendor = "Oracle", kind = "JDK"),
          JDKVersion(name = "test2", version = "1.181.1", vendor = "Oracle", kind = "JDK"),
          JDKVersion(name = "test3", version = "1.191.1", vendor = "OpenJDK", kind = "JRE"),
          JDKVersion(name = "test4", version = "1.121.1", vendor = "OpenJDK", kind = "JRE"))))

      await(boot.service.getJDKCountsForEnv(SlugInfoFlag.Latest)) shouldBe JDKUsageByEnv(SlugInfoFlag.Latest.asString, Map("1.181.1"-> 2, "1.191.1"->1, "1.121.1" -> 1))
    }

    "still returns a value when no matches are found for env" in {
      val boot = Boot.init

      when(boot.mockedServiceDependenciesConnector.getJDKVersions(SlugInfoFlag.Latest))
        .thenReturn(Future(List.empty[JDKVersion]))

      await(boot.service.getJDKCountsForEnv(SlugInfoFlag.Latest)) shouldBe JDKUsageByEnv(SlugInfoFlag.Latest.asString, Map.empty[String, Int])
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
