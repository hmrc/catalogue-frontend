/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import java.time.LocalDateTime

import akka.stream.Materializer
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.connector.DigitalService.DigitalServiceRepository
import uk.gov.hmrc.cataloguefrontend.connector.RepoType._
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependencies, Dependency, Version}
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.Future

class DependencyReportControllerSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite {

  private val now: LocalDateTime = LocalDateTime.now()

  private implicit lazy val materializer: Materializer = app.materializer
  private lazy val mockedTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
  private lazy val mockedDependenciesConnector         = mock[ServiceDependenciesConnector]

  private lazy val dependencyReportController = new DependencyReportController(
    mockedTeamsAndRepositoriesConnector,
    mockedDependenciesConnector,
    stubMessagesControllerComponents()
  )

  "dependencyReport" should {

    val digitalService1 = "digital-service-1"
    val digitalService2 = "digital-service-2"

    def mockDigitalService() = {
      when(mockedTeamsAndRepositoriesConnector.allDigitalServices(any()))
        .thenReturn(Future.successful(Seq(digitalService1, digitalService2)))

      when(mockedTeamsAndRepositoriesConnector.digitalServiceInfo(eqTo(digitalService1))(any()))
        .thenReturn(
          Future.successful(Some(DigitalService(digitalService1, 1, Seq(digitalServiceRepository("repo-1"))))))

      when(mockedTeamsAndRepositoriesConnector.digitalServiceInfo(eqTo(digitalService2))(any()))
        .thenReturn(
          Future.successful(Some(DigitalService(digitalService2, 1, Seq(digitalServiceRepository("repo-2"))))))
    }

    def mockTeamsAndTheirRepositories(): OngoingStubbing[Future[Seq[Team]]] = {
      def team(teamName: String, repositories: Seq[String]) =
        Team(
          teamName,
          None,
          None,
          None,
          Some(Map("Service" -> Seq(), "Library" -> Seq(), "Prototype" -> Seq(), "Other" -> repositories))
        )

      when(mockedTeamsAndRepositoriesConnector.teamsWithRepositories()(any()))
        .thenReturn(Future.successful(Seq(team("team1", Seq("repo-1")), team("team2", Seq("repo-2")))))
    }

    def mockAllDependencies() =
      when(mockedDependenciesConnector.getAllDependencies()(any()))
        .thenReturn(Future.successful(Seq(
          Dependencies(
            repositoryName = "repo-1",
            libraryDependencies = Seq(
              libraryDependency("LIBRARY-1", 1, "green"),
              libraryDependency("LIBRARY-2", 2, "red")
            ),
            sbtPluginsDependencies = Seq(
              sbtPluginsDependencyState("PLUGIN-1", 1, "amber"),
              sbtPluginsDependencyState("PLUGIN-2", 2, "red")
            ),
            Seq(),
            lastUpdated = DateTimeUtils.now
          ),
          Dependencies(
            repositoryName = "repo-2",
            libraryDependencies = Seq(
              libraryDependency("LIBRARY-3", 3, "green")
            ),
            sbtPluginsDependencies = Seq(
              sbtPluginsDependencyState("PLUGIN-3", 3, "red")
            ),
            Seq(),
            lastUpdated = DateTimeUtils.now
          )
        )))

    "return the dependencyReport in csv" in {

      mockDigitalService()
      mockTeamsAndTheirRepositories()
      mockAllDependencies()

      val response = dependencyReportController.dependencyReport()(FakeRequest())

      status(response) shouldBe 200

      val csvLines = contentAsString(response).lines.toList

      csvLines.length shouldBe 7

      csvLines(0) shouldBe "repository,dependencyName,digitalService,colour,dependencyType,team,latestVersion,currentVersion"
      csvLines    should contain("repo-1,LIBRARY-1,digital-service-1,green,library,team1,1.0.0,1.0.0")
      csvLines    should contain("repo-1,LIBRARY-2,digital-service-1,red,library,team1,3.0.0,2.0.0")
      csvLines    should contain("repo-1,PLUGIN-1,digital-service-1,amber,plugin,team1,1.1.0,1.0.0")
      csvLines    should contain("repo-1,PLUGIN-2,digital-service-1,red,plugin,team1,3.0.0,2.0.0")
      csvLines    should contain("repo-2,LIBRARY-3,digital-service-2,green,library,team2,3.0.0,3.0.0")
      csvLines    should contain("repo-2,PLUGIN-3,digital-service-2,red,plugin,team2,4.0.0,3.0.0")
    }
  }

  def latestVersion(currentVersion: Version, colour: String): Option[Version] =
    colour match {
      case "green" => Some(currentVersion)
      case "amber" => Some(currentVersion + Version(0, 1, 0))
      case "red"   => Some(currentVersion + Version(1, 0, 0))
    }

  private def sbtPluginsDependencyState(sbtPluginName: String, majorVersion: Int, colour: String) = {
    val currentVersion = Version(majorVersion, 0, 0)
    Dependency(sbtPluginName, currentVersion, latestVersion(currentVersion, colour))
  }

  private def libraryDependency(libraryName: String, majorVersion: Int, colour: String) = {
    val currentVersion = Version(majorVersion, 0, 0)
    Dependency(libraryName, currentVersion, latestVersion(currentVersion, colour))
  }

  private def digitalServiceRepository(repoName: String) =
    DigitalServiceRepository(repoName, now, now, Service, Nil)
}
