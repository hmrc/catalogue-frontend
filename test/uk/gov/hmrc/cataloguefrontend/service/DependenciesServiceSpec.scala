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
import uk.gov.hmrc.cataloguefrontend.connector.ServiceDependenciesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.{ServiceWithDependency, Version, VersionOp}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SlugInfoServiceSpec
  extends UnitSpec
     with MockitoSugar {

  implicit val hc = mock[HeaderCarrier]

  val group    = "group"
  val artefact = "artefact"

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
    "filter results by version" in {

      val boot = Boot.init

      when(boot.mockedServiceDependenciesConnector.getServicesWithDependency(group, artefact))
        .thenReturn(Future(Seq(v100, v200, v205)))

      await(boot.service.getServicesWithDependency(optTeam = None, group, artefact, versionOp = VersionOp.Gte, version = Version("1.0.1"))) shouldBe Seq(v205, v200)
      await(boot.service.getServicesWithDependency(optTeam = None, group, artefact, versionOp = VersionOp.Lte, version = Version("1.0.1"))) shouldBe Seq(v100)
      await(boot.service.getServicesWithDependency(optTeam = None, group, artefact, versionOp = VersionOp.Eq,  version = Version("2.0.0"))) shouldBe Seq(v200)
    }

    "include non-parseable versions" in {

      val boot = Boot.init

      val bad = v100.copy(depVersion  = "r938", depSemanticVersion = None)

      when(boot.mockedServiceDependenciesConnector.getServicesWithDependency(group, artefact))
        .thenReturn(Future(Seq(v100, v200, v205, bad)))

      await(boot.service.getServicesWithDependency(optTeam = None, group, artefact, versionOp = VersionOp.Gte, version = Version("1.0.1"))) shouldBe Seq(v205, v200, bad)
      await(boot.service.getServicesWithDependency(optTeam = None, group, artefact, versionOp = VersionOp.Lte, version = Version("1.0.1"))) shouldBe Seq(v100, bad)
    }

    "filter results by team" in {

      val boot = Boot.init

      when(boot.mockedServiceDependenciesConnector.getServicesWithDependency(group, artefact))
        .thenReturn(Future(Seq(v100, v200, v205)))

      await(boot.service.getServicesWithDependency(optTeam = Some("T1"), group, artefact, versionOp = VersionOp.Gte, version = Version("1.0.1"))) shouldBe Seq(v200)
      await(boot.service.getServicesWithDependency(optTeam = Some("T2"), group, artefact, versionOp = VersionOp.Gte, version = Version("1.0.1"))) shouldBe Seq(v205, v200)
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
