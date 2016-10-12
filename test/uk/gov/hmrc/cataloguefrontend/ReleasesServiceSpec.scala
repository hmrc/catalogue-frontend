/*
 * Copyright 2016 HM Revenue & Customs
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

import TeamsAndServicesConnector._
import org.mockito
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.test.FakeHeaders
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class ReleasesServiceSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures {

  "Releases service" should {

    implicit val hc = HeaderCarrier.fromHeadersAndSession(FakeHeaders())

    "Combine release and team information given an empty filter" in {
      val releasesConnector = mock[ServiceReleasesConnector]
      val teamsAndServicesConnector = mock[TeamsAndServicesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(releasesConnector.getReleases()).thenReturn(Future.successful(Seq(
        Release("a-service", productionDate = productionDate, version = "0.1.0"),
        Release("b-service", productionDate = productionDate, version = "0.2.0"))))

      when(teamsAndServicesConnector.allTeamsByService()).thenReturn(Future.successful(
        new CachedItem[Map[ServiceName, Seq[TeamName]]](Map(
          "a-service" -> Seq("a-team", "b-team"),
          "b-service" -> Seq("c-team")), "time")))

      val service = new ReleasesService(releasesConnector, teamsAndServicesConnector)
      val releases = service.getReleases().futureValue

      releases should contain(TeamRelease("a-service", teams = Seq("a-team", "b-team"), productionDate = productionDate, version = "0.1.0"))
      releases should contain(TeamRelease("b-service", teams = Seq("c-team"), productionDate = productionDate, version = "0.2.0"))
    }

    "Filter results given a team name" in {
      val releasesConnector = mock[ServiceReleasesConnector]
      val teamsAndServicesConnector = mock[TeamsAndServicesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(releasesConnector.getReleases(Seq("a-service", "b-service"))).thenReturn(Future.successful(Seq(
        Release("a-service", productionDate = productionDate, version = "0.1.0"),
        Release("b-service", productionDate = productionDate, version = "0.2.0"))))

      when(teamsAndServicesConnector.teamInfo("b-team")).thenReturn(Future.successful(
        Some(new CachedItem[Map[TeamName, Seq[ServiceName]]](
          Map("Deployable" -> Seq("a-service", "b-service")), "time"))))

      when(teamsAndServicesConnector.teamsByService(Seq("a-service", "b-service"))).thenReturn(
        Future.successful(new CachedItem[Map[ServiceName, Seq[TeamName]]](
          Map("a-service" -> Seq("a-team", "b-team"), "b-service" -> Seq("b-team", "c-team")), "time")))

      val service = new ReleasesService(releasesConnector, teamsAndServicesConnector)
      val releases = service.getReleases(teamName = Some("b-team")).futureValue

      releases should contain(TeamRelease("a-service", teams = Seq("a-team", "b-team"), productionDate = productionDate, version = "0.1.0"))
      releases should contain(TeamRelease("b-service", teams = Seq("b-team", "c-team"), productionDate = productionDate, version = "0.2.0"))
    }

    "Filter results given a service name" in {
      val releasesConnector = mock[ServiceReleasesConnector]
      val teamsAndServicesConnector = mock[TeamsAndServicesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(releasesConnector.getReleases(Seq("a-service"))).thenReturn(Future.successful(Seq(
        Release("a-service", productionDate = productionDate, version = "0.1.0"))))

      when(teamsAndServicesConnector.repositoryDetails("a-service")).thenReturn(
        Future.successful(Some(new CachedItem[RepositoryDetails](
          RepositoryDetails("a-service", Seq("a-team", "b-team"), Seq(), Seq(), None, RepoType.Deployable), "time"))))

      val service = new ReleasesService(releasesConnector, teamsAndServicesConnector)
      val releases = service.getReleases(serviceName = Some("a-service")).futureValue

      releases should contain(TeamRelease("a-service", teams = Seq("a-team", "b-team"), productionDate = productionDate, version = "0.1.0"))
    }

    "Give precedence to the service name filter over the team name filter as it is more specific" in {
      val releasesConnector = mock[ServiceReleasesConnector]
      val teamsAndServicesConnector = mock[TeamsAndServicesConnector]

      val productionDate = LocalDateTime.ofEpochSecond(1453731429, 0, ZoneOffset.UTC)
      when(releasesConnector.getReleases(Seq("a-service"))).thenReturn(Future.successful(Seq(
        Release("a-service", productionDate = productionDate, version = "0.1.0"))))

      when(teamsAndServicesConnector.repositoryDetails("a-service")).thenReturn(
        Future.successful(Some(new CachedItem[RepositoryDetails](
          RepositoryDetails("a-service", Seq("a-team", "b-team"), Seq(), Seq(), None, RepoType.Deployable), "time"))))

      val service = new ReleasesService(releasesConnector, teamsAndServicesConnector)
      val releases = service.getReleases(serviceName = Some("a-service"), teamName = Some("non-matching-team")).futureValue

      releases should contain(TeamRelease("a-service", teams = Seq("a-team", "b-team"), productionDate = productionDate, version = "0.1.0"))
    }


    "Not make unnecessary calls if a team does not exist" in {
      val releasesConnector = mock[ServiceReleasesConnector]
      val teamsAndServicesConnector = mock[TeamsAndServicesConnector]

      when(teamsAndServicesConnector.teamInfo("a-team")).thenReturn(Future.successful(None))

      val service = new ReleasesService(releasesConnector, teamsAndServicesConnector)
      val releases = service.getReleases(teamName = Some("a-team")).futureValue

      releases shouldBe empty
    }

    "Not make unnecessary calls if a service does not exist" in {
      val releasesConnector = mock[ServiceReleasesConnector]
      val teamsAndServicesConnector = mock[TeamsAndServicesConnector]

      when(teamsAndServicesConnector.repositoryDetails("a-service")).thenReturn(Future.successful(None))

      val service = new ReleasesService(releasesConnector, teamsAndServicesConnector)
      val releases = service.getReleases(serviceName = Some("a-service")).futureValue

      releases shouldBe empty
    }

  }

}
