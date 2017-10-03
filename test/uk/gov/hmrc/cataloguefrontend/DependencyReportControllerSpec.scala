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

package uk.gov.hmrc.cataloguefrontend

import java.time.LocalDateTime

import akka.stream.Materializer
import com.github.tomakehurst.wiremock.http.RequestMethod.{GET => WIREMOCK_GET}
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.Environment
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.cataloguefrontend.DigitalService.DigitalServiceRepository
import uk.gov.hmrc.cataloguefrontend.RepoType._
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.TeamMember
import uk.gov.hmrc.cataloguefrontend.connector.ServiceDependenciesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependencies, LibraryDependencyState, SbtPluginsDependenciesState, Version}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.http.HttpClient

class DependencyReportControllerSpec extends UnitSpec with BeforeAndAfterEach with OneServerPerSuite with WireMockEndpoints with MockitoSugar with ScalaFutures {


  val now = LocalDateTime.now()

  implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())

  implicit override lazy val app = new GuiceApplicationBuilder().configure (
    "microservice.services.teams-and-services.host" -> host,
    "microservice.services.teams-and-services.port" -> endpointPort,
    "play.ws.ssl.loose.acceptAnyCertificate" -> true,
    "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler").build()

  implicit val materializer = app.injector.instanceOf[Materializer]

  val mockedTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
  val mockedDependenciesConnector = mock[ServiceDependenciesConnector]

  val dependencyReportController = new DependencyReportController(
    mock[HttpClient],
    app.configuration, mock[play.api.Environment],
    mockedTeamsAndRepositoriesConnector,
    mockedDependenciesConnector)

  "dependencyReport" should {

    val digitalService1 = "digital-service-1"
    val digitalService2 = "digital-service-2"

    def mockDigitalService() = {
      when(mockedTeamsAndRepositoriesConnector.allDigitalServices(any()))
        .thenReturn(Future.successful(Seq(digitalService1, digitalService2)))

      
      when(mockedTeamsAndRepositoriesConnector.digitalServiceInfo(eqTo(digitalService1))(any()))
        .thenReturn(Future.successful(Right(DigitalService(digitalService1, 1,
          Seq(digitalServiceRepository("repo-1"))))))

      when(mockedTeamsAndRepositoriesConnector.digitalServiceInfo(eqTo(digitalService2))(any()))
        .thenReturn(Future.successful(Right(DigitalService(digitalService2, 1,
          Seq(digitalServiceRepository("repo-2"))))))
    }


    def mockTeamsAndTheirRepositories() = {
      def team(teamName: String, repositories: Seq[String]) = Team(teamName, None, None, None, Some(Map("Service" -> Seq(), "Library" -> Seq(), "Prototype" -> Seq(), "Other" -> repositories)))

      when(mockedTeamsAndRepositoriesConnector.teamsWithRepositories()(any()))
        .thenReturn(Future.successful(Seq(team("team1", Seq("repo-1")), team("team2", Seq("repo-2")))))
    }


    def mockAllDependencies() = {
      when(mockedDependenciesConnector.getAllDependencies()(any()))
        .thenReturn(Future.successful(Seq(
          Dependencies(
            repositoryName = "repo-1",
            libraryDependenciesState = Seq(
              libraryDependencyState("LIBRARY-1", 1, "green"),
              libraryDependencyState("LIBRARY-2", 2, "red")
            ),
            sbtPluginsDependenciesState = Seq(
              sbtPluginsDependencyState("PLUGIN-1", 1, "amber" ),
              sbtPluginsDependencyState("PLUGIN-2", 2, "red" )
            ), Seq()
          ),
          Dependencies(
            repositoryName = "repo-2",
            libraryDependenciesState = Seq(
              libraryDependencyState("LIBRARY-3", 3, "green")
            ),
            sbtPluginsDependenciesState = Seq(
              sbtPluginsDependencyState("PLUGIN-3", 3, "red")
            ), Seq()
          )
        )))
    }

    "return the dependencyReport in csv" in {

      mockDigitalService()
      mockTeamsAndTheirRepositories()
      mockAllDependencies()

      val response: Result = dependencyReportController.dependencyReport()(FakeRequest()).futureValue

      response.header.status shouldBe 200

      val csvLines = contentAsString(response).lines.toList

      csvLines.length shouldBe 7

      csvLines(0) shouldBe "repository,dependencyName,digitalService,colour,dependencyType,team,latestVersion,currentVersion"
      csvLines should contain ("repo-1,LIBRARY-1,digital-service-1,green,library,team1,1.0.0,1.0.0")
      csvLines should contain ("repo-1,LIBRARY-2,digital-service-1,red,library,team1,3.0.0,2.0.0")
      csvLines should contain ("repo-1,PLUGIN-1,digital-service-1,amber,plugin,team1,1.1.0,1.0.0")
      csvLines should contain ("repo-1,PLUGIN-2,digital-service-1,red,plugin,team1,3.0.0,2.0.0")
      csvLines should contain ("repo-2,LIBRARY-3,digital-service-2,green,library,team2,3.0.0,3.0.0")
      csvLines should contain ("repo-2,PLUGIN-3,digital-service-2,red,plugin,team2,4.0.0,3.0.0")
    }

  }




  def latestVersion(currentVersion: Version,colour: String): Option[Version] = {
    colour match {
      case "green" => Some(currentVersion)
      case "amber" => Some(currentVersion + Version(0,1,0))
      case "red" => Some(currentVersion + Version(1,0,0))
    }
  }

  private def sbtPluginsDependencyState(sbtPluginName: String, majorVersion: Int, colour: String) = {
    val currentVersion = Version(majorVersion, 0, 0)
    SbtPluginsDependenciesState(sbtPluginName, currentVersion, latestVersion(currentVersion, colour))
  }

  private def libraryDependencyState(libraryName: String, majorVersion: Int, colour: String) = {
    val currentVersion = Version(majorVersion, 0, 0)
    LibraryDependencyState(libraryName, currentVersion, latestVersion(currentVersion, colour))
  }

  private def digitalServiceRepository(repoName: String) = {
    DigitalServiceRepository(repoName, now, now, Service, Nil)
  }

  private def repositoryDetails(repoName: String) = {
    RepositoryDisplayDetails(repoName, now, now, Service)
  }

  private def teamMember(displayName: String, userName: String) = {
    TeamMember(Some(displayName), None, None, None, None, Some(userName))
  }

}
