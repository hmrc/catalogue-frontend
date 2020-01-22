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

package uk.gov.hmrc.cataloguefrontend

import java.time.{LocalDateTime, ZoneOffset}

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{EitherValues, Matchers, OptionValues, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.FakeHeaders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector.ServiceName
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.connector.model.{TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.{DeploymentsService, TeamRelease}
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class DeploymentsServiceSpec
    extends WordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with OptionValues
    with EitherValues {

  import ExecutionContext.Implicits.global

  val now: LocalDateTime = LocalDateTime.now()

  "Deployments service" should {

    implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())

    "Combine release and team information given an empty filter" in {
      val deploymentsConnector      = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(deploymentsConnector.getDeployments()).thenReturn(Future.successful(Seq(
        Release(
          "a-service",
          productionDate                    = productionDate,
          version                           = "0.1.0",
          deployers                         = Seq(Deployer("abc.xyz", productionDate))),
        Release("b-service", productionDate = productionDate, version = "0.2.0")
      )))

      when(teamsAndServicesConnector.allTeamsByService()).thenReturn(
        Future.successful(
          Map(
            "a-service" -> Seq(TeamName("a-team"), TeamName("b-team")),
            "b-service" -> Seq(TeamName("c-team"))
          )
        ))

      val service     = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments = service.getDeployments(teamName = None, serviceName = None).futureValue

      deployments should contain(
        TeamRelease(
          "a-service",
          teams          = Seq(TeamName("a-team"), TeamName("b-team")),
          productionDate = productionDate,
          version        = "0.1.0",
          latestDeployer = Some(Deployer("abc.xyz", productionDate))))
      deployments should contain(
        TeamRelease("b-service", teams = Seq(TeamName("c-team")), productionDate = productionDate, version = "0.2.0"))
    }

    "Cope with deployments for services that are not known to the catalogue" in {
      val deploymentsConnector      = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(deploymentsConnector.getDeployments())
        .thenReturn(Future.successful(Seq(Release("a-service", productionDate = productionDate, version = "0.1.0"))))

      when(teamsAndServicesConnector.allTeamsByService())
        .thenReturn(Future.successful(Map.empty[ServiceName, Seq[TeamName]]))

      val service     = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments = service.getDeployments(teamName = None, serviceName = None).futureValue

      deployments should contain(
        TeamRelease("a-service", teams = Seq(), productionDate = productionDate, version = "0.1.0"))
    }

    "Filter results given a team name" in {
      val deploymentsConnector      = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(deploymentsConnector.getDeployments(Set("a-service", "b-service"))).thenReturn(
        Future.successful(
          Seq(
            Release("a-service", productionDate = productionDate, version = "0.1.0"),
            Release("b-service", productionDate = productionDate, version = "0.2.0"))))

      when(teamsAndServicesConnector.teamInfo(TeamName("b-team"))).thenReturn(Future.successful(
        Some(Team(name = TeamName("teamName"), None, None, None, repos = Some(Map("Service" -> Seq("a-service", "b-service")))))))

      when(teamsAndServicesConnector.teamsByService(Seq("a-service", "b-service"))).thenReturn(
        Future.successful(Map("a-service" -> Seq(TeamName("a-team"), TeamName("b-team")), "b-service" -> Seq(TeamName("b-team"), TeamName("c-team")))))

      val service     = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments = service.getDeployments(teamName = Some(TeamName("b-team")), serviceName = None).futureValue

      deployments should contain(
        TeamRelease("a-service", teams = Seq(TeamName("a-team"), TeamName("b-team")), productionDate = productionDate, version = "0.1.0"))
      deployments should contain(
        TeamRelease("b-service", teams = Seq(TeamName("b-team"), TeamName("c-team")), productionDate = productionDate, version = "0.2.0"))
    }

    "Filter results given a service name" in {
      val deploymentsConnector      = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(deploymentsConnector.getDeployments(Set("a-service")))
        .thenReturn(Future.successful(Seq(Release("a-service", productionDate = productionDate, version = "0.1.0"))))

      when(teamsAndServicesConnector.repositoryDetails("a-service")).thenReturn(
        Future.successful(Some(RepositoryDetails(
          name         = "a-service",
          description  = "some description",
          createdAt    = now,
          lastActive   = now,
          owningTeams  = Seq.empty,
          teamNames    = Seq(TeamName("a-team"), TeamName("b-team")),
          githubUrl    = Link("github-com", "GitHub.com", "https://github.com/hmrc/a-service"),
          jenkinsURL   = None,
          environments = None,
          repoType     = RepoType.Service, isPrivate    = false))))

      val service     = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments = service.getDeployments(serviceName = Some("a-service"), teamName = None).futureValue

      deployments should contain(
        TeamRelease("a-service", teams = Seq(TeamName("a-team"), TeamName("b-team")), productionDate = productionDate, version = "0.1.0"))
    }

    "Give precedence to the service name filter over the team name filter as it is more specific" in {
      val deploymentsConnector      = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(deploymentsConnector.getDeployments(Set("a-service")))
        .thenReturn(Future.successful(Seq(Release("a-service", productionDate = productionDate, version = "0.1.0"))))

      when(teamsAndServicesConnector.repositoryDetails("a-service")).thenReturn(
        Future.successful(
          Some(RepositoryDetails(
            name         = "a-service",
            description  = "some description",
            createdAt    = now,
            lastActive   = now,
            owningTeams  = Seq(),
            teamNames    = Seq(TeamName("a-team"), TeamName("b-team")),
            githubUrl    = Link("github-com", "GitHub.com", "https://github.com/hmrc/a-service"),
            jenkinsURL   = None,
            environments = None,
            repoType     = RepoType.Service,
            isPrivate    = false)))
      )

      val service = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments =
        service.getDeployments(serviceName = Some("a-service"), teamName = Some(TeamName("non-matching-team")))
        .futureValue

      deployments should contain(
        TeamRelease("a-service", teams = Seq(TeamName("a-team"), TeamName("b-team")), productionDate = productionDate, version = "0.1.0"))
    }

    "Not make unnecessary calls if a team does not exist" in {
      val deploymentsConnector      = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      when(teamsAndServicesConnector.teamInfo(TeamName("a-team"))).thenReturn(Future.successful(None))

      val service     = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments = service.getDeployments(serviceName = None, teamName = Some(TeamName("a-team"))).futureValue

      deployments shouldBe empty
    }

    "Not make unnecessary calls if a service does not exist" in {
      val deploymentsConnector      = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      when(teamsAndServicesConnector.repositoryDetails("a-service")).thenReturn(Future.successful(None))

      val service     = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments = service.getDeployments(serviceName = Some("a-service"), teamName = None).futureValue

      deployments shouldBe empty
    }

    "should delegate to DeploymentConnector for getting whatIsRunningWhere " in {
      val deploymentsConnector = mock[ServiceDeploymentsConnector]

      val appName = "app-1"

      val cannedWhatIsRunningWhere =
        ServiceDeploymentInformation(
          appName,
          Seq(DeploymentVO(EnvironmentMapping("qa", Environment.QA), "skyscape-farnborough", Version("0.0.1"))))
      when(deploymentsConnector.getWhatIsRunningWhere(any())(any()))
        .thenReturn(Future.successful(cannedWhatIsRunningWhere))

      val service = new DeploymentsService(deploymentsConnector, mock[TeamsAndRepositoriesConnector])

      val whatIsRunningWhere = service.getWhatsRunningWhere(appName).futureValue

      whatIsRunningWhere shouldBe cannedWhatIsRunningWhere
      verify(deploymentsConnector).getWhatIsRunningWhere(appName)
    }
  }
}
