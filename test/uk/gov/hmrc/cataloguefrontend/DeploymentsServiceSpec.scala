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

import java.time.{Instant, LocalDateTime, ZoneOffset}

import TeamsAndRepositoriesConnector._
import org.mockito
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{EitherValues, Matchers, OptionValues, WordSpec}
import play.api.test.FakeHeaders
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class DeploymentsServiceSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures with OptionValues with EitherValues {

  val now = LocalDateTime.now()


  "Deployments service" should {

    implicit val hc = HeaderCarrier.fromHeadersAndSession(FakeHeaders())

    "Combine release and team information given an empty filter" in {
      val deploymentsConnector = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(deploymentsConnector.getDeployments()).thenReturn(Future.successful(Seq(
        Release("a-service", productionDate = productionDate, version = "0.1.0", deployers = Seq(Deployer("abc.xyz",productionDate))),
        Release("b-service", productionDate = productionDate, version = "0.2.0"))))

      when(teamsAndServicesConnector.allTeamsByService()).thenReturn(Future.successful(
        new Timestamped[Map[ServiceName, Seq[TeamName]]](Map(
          "a-service" -> Seq("a-team", "b-team"),
          "b-service" -> Seq("c-team")), Some(Instant.now))))

      val service = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments = service.getDeployments().futureValue

      deployments should contain(TeamRelease("a-service", teams = Seq("a-team", "b-team"), productionDate = productionDate, version = "0.1.0", latestDeployer = Some(Deployer("abc.xyz",productionDate))))
      deployments should contain(TeamRelease("b-service", teams = Seq("c-team"), productionDate = productionDate, version = "0.2.0"))
    }

    "Cope with deployments for services that are not known to the catalogue" in {
      val deploymentsConnector = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(deploymentsConnector.getDeployments()).thenReturn(Future.successful(Seq(
        Release("a-service", productionDate = productionDate, version = "0.1.0"))))

      when(teamsAndServicesConnector.allTeamsByService()).thenReturn(Future.successful(
        new Timestamped[Map[ServiceName, Seq[TeamName]]](Map(), Some(Instant.now))))

      val service = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments = service.getDeployments().futureValue

      deployments should contain(TeamRelease("a-service", teams = Seq(), productionDate = productionDate, version = "0.1.0"))
    }

    "Filter results given a team name" in {
      val deploymentsConnector = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(deploymentsConnector.getDeployments(Seq("a-service", "b-service"))).thenReturn(Future.successful(Seq(
        Release("a-service", productionDate = productionDate, version = "0.1.0"),
        Release("b-service", productionDate = productionDate, version = "0.2.0"))))

      when(teamsAndServicesConnector.teamInfo("b-team")).thenReturn(Future.successful(
        Some(new Timestamped[Team](
          Team(name = "teamName", None, None, None,
          repos = Some(Map("Service" -> Seq("a-service", "b-service")))), Some(Instant.now)))))

      when(teamsAndServicesConnector.teamsByService(Seq("a-service", "b-service"))).thenReturn(
        Future.successful(new Timestamped[Map[ServiceName, Seq[TeamName]]](
          Map("a-service" -> Seq("a-team", "b-team"), "b-service" -> Seq("b-team", "c-team")), Some(Instant.now))))

      val service = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments = service.getDeployments(teamName = Some("b-team")).futureValue

      deployments should contain(TeamRelease("a-service", teams = Seq("a-team", "b-team"), productionDate = productionDate, version = "0.1.0"))
      deployments should contain(TeamRelease("b-service", teams = Seq("b-team", "c-team"), productionDate = productionDate, version = "0.2.0"))
    }

    "Filter results given a service name" in {
      val deploymentsConnector = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(deploymentsConnector.getDeployments(Seq("a-service"))).thenReturn(Future.successful(Seq(
        Release("a-service", productionDate = productionDate, version = "0.1.0"))))

      when(teamsAndServicesConnector.repositoryDetails("a-service")).thenReturn(
        Future.successful(Some(new Timestamped[RepositoryDetails](
          RepositoryDetails("a-service", "some description", now, now, Seq("a-team", "b-team"), Seq(), Seq(), None, RepoType.Service), Some(Instant.now)))))

      val service = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments = service.getDeployments(serviceName = Some("a-service")).futureValue

      deployments should contain(TeamRelease("a-service", teams = Seq("a-team", "b-team"), productionDate = productionDate, version = "0.1.0"))
    }

    "Give precedence to the service name filter over the team name filter as it is more specific" in {
      val deploymentsConnector = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(deploymentsConnector.getDeployments(Seq("a-service"))).thenReturn(Future.successful(Seq(
        Release("a-service", productionDate = productionDate, version = "0.1.0"))))

      when(teamsAndServicesConnector.repositoryDetails("a-service")).thenReturn(
        Future.successful(Some(new Timestamped[RepositoryDetails](
          RepositoryDetails("a-service", "some description", now, now, Seq("a-team", "b-team"), Seq(), Seq(), None, RepoType.Service), Some(Instant.now)))))

      val service = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments = service.getDeployments(serviceName = Some("a-service"), teamName = Some("non-matching-team")).futureValue

      deployments should contain(TeamRelease("a-service", teams = Seq("a-team", "b-team"), productionDate = productionDate, version = "0.1.0"))
    }


    "Not make unnecessary calls if a team does not exist" in {
      val deploymentsConnector = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      when(teamsAndServicesConnector.teamInfo("a-team")).thenReturn(Future.successful(None))

      val service = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments = service.getDeployments(teamName = Some("a-team")).futureValue

      deployments shouldBe empty
    }

    "Not make unnecessary calls if a service does not exist" in {
      val deploymentsConnector = mock[ServiceDeploymentsConnector]
      val teamsAndServicesConnector = mock[TeamsAndRepositoriesConnector]

      when(teamsAndServicesConnector.repositoryDetails("a-service")).thenReturn(Future.successful(None))

      val service = new DeploymentsService(deploymentsConnector, teamsAndServicesConnector)
      val deployments = service.getDeployments(serviceName = Some("a-service")).futureValue

      deployments shouldBe empty
    }

    "should delegate to DeploymentConnector for getting whatIsRunningWhere " in {
      val deploymentsConnector = mock[ServiceDeploymentsConnector]

      val appName = "app-1"
      
      val cannedWhatIsRunningWhere = Right(WhatIsRunningWhere(appName, Seq(DeployedEnvironmentVO("qa", "qa"))))
      when(deploymentsConnector.getWhatIsRunningWhere(any())(any())).thenReturn(Future.successful(cannedWhatIsRunningWhere))

      val service = new DeploymentsService(deploymentsConnector, mock[TeamsAndRepositoriesConnector])

      val whatIsRunningWhere = service.getWhatsRunningWhere(appName).futureValue

      whatIsRunningWhere shouldBe cannedWhatIsRunningWhere
      verify(deploymentsConnector).getWhatIsRunningWhere(appName)
    }
  }


}
