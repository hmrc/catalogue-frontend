/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.connector

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.play.http.HeaderCarrierConverter

class TeamsAndRepositoriesConnectorSpec
  extends AnyWordSpec
     with Matchers
     with BeforeAndAfterEach
     with ScalaFutures
     with IntegrationPatience
     with GuiceOneAppPerSuite
     with WireMockSupport
     with TypeCheckedTripleEquals
     with OptionValues
     with EitherValues {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(
        Map(
          "microservice.services.teams-and-repositories.host" -> wireMockHost,
          "microservice.services.teams-and-repositories.port" -> wireMockPort,
          "metrics.jvm"            -> false
        ))
      .build()

  private lazy val teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector =
    app.injector.instanceOf[TeamsAndRepositoriesConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "lookUpLink" should {
    "return a Link if exists" in {
      stubFor(
        get(urlEqualTo("/api/jenkins-url/serviceA"))
          .willReturn(
            aResponse()
            .withBody(
              """
                {
                  "service": "serviceA",
                  "jenkinsURL": "http.jenkins/serviceA"
                }
              """
          )
        )
      )

      val response = teamsAndRepositoriesConnector
        .lookupLink("serviceA")(HeaderCarrierConverter.fromRequest(FakeRequest()))
        .futureValue

      response shouldBe Some(Link("serviceA", "Build", "http.jenkins/serviceA"))
    }

    "return None if Not Found" in {
      stubFor(
        get(urlEqualTo("/api/jenkins-url/serviceA"))
          .willReturn(aResponse().withStatus(404))
      )

      val response = teamsAndRepositoriesConnector
        .lookupLink("serviceA")(HeaderCarrierConverter.fromRequest(FakeRequest()))
        .futureValue

      response shouldBe None
    }
  }

  /*
  "repositoryDetails" should {
    "convert the json string to RepositoryDetails" in {
      stubFor(
        get(urlEqualTo("/api/repositories/service-1"))
          .willReturn(aResponse().withBody(JsonData.repositoryData()))
      )
      stubFor(
        post(urlEqualTo("/api/jenkins-url/service01"))
          .willReturn(aResponse().withBody(JsonData.serviceJenkinsData))
      )

      val responseData: RepositoryDetails =
        teamsAndRepositoriesConnector
          .repositoryDetails("service-1")(HeaderCarrierConverter.fromRequest(FakeRequest()))
          .futureValue
          .value

      responseData.name        shouldBe "service-1"
      responseData.description shouldBe "some description"
      responseData.createdAt   shouldBe createdAt
      responseData.lastActive  shouldBe lastActiveAt
      responseData.teamNames   should ===(Seq(TeamName("teamA"), TeamName("teamB")))
      responseData.githubUrl   should ===(Link("github", "github.com", "https://github.com/hmrc/service-1"))
      responseData.jenkinsURL should === (None)

      responseData.repoType shouldBe RepoType.Service
      responseData.isArchived shouldBe false
    }
  }

  "allRepositories" should {
    "return all the repositories returned by the api" in {
      stubFor(
        get(urlEqualTo("/api/repositories"))
          .willReturn(aResponse().withBody(JsonData.repositoriesData))
      )

      val repositories: Seq[RepositoryDisplayDetails] = teamsAndRepositoriesConnector
        .allRepositories(HeaderCarrierConverter.fromRequest(FakeRequest()))
        .futureValue

      repositories.headOption.value.name          shouldBe "teamA-serv"
      repositories.headOption.value.createdAt     shouldBe createdAt
      repositories.headOption.value.lastUpdatedAt shouldBe lastActiveAt
      repositories.headOption.value.repoType      shouldBe RepoType.Service

      repositories(1).name          shouldBe "teamB-library"
      repositories(1).createdAt     shouldBe createdAt
      repositories(1).lastUpdatedAt shouldBe lastActiveAt
      repositories(1).repoType      shouldBe RepoType.Library

      repositories(2).name          shouldBe "teamB-other"
      repositories(2).createdAt     shouldBe createdAt
      repositories(2).lastUpdatedAt shouldBe lastActiveAt
      repositories(2).repoType      shouldBe RepoType.Other
    }
  }


  "archivedRepositories" should {
    "return all the archived repositories returned by the api" in {
      stubFor(
        get(urlEqualTo("/api/repositories?archived=true"))
          .willReturn(aResponse().withBody(JsonData.repositoriesData))
      )

      val repositories: Seq[RepositoryDisplayDetails] = teamsAndRepositoriesConnector
        .archivedRepositories(HeaderCarrierConverter.fromRequest(FakeRequest()))
        .futureValue

      repositories.headOption.value.name          shouldBe "teamA-serv"
      repositories.headOption.value.createdAt     shouldBe createdAt
      repositories.headOption.value.lastUpdatedAt shouldBe lastActiveAt
      repositories.headOption.value.repoType      shouldBe RepoType.Service

      repositories(1).name          shouldBe "teamB-library"
      repositories(1).createdAt     shouldBe createdAt
      repositories(1).lastUpdatedAt shouldBe lastActiveAt
      repositories(1).repoType      shouldBe RepoType.Library

      repositories(2).name          shouldBe "teamB-other"
      repositories(2).createdAt     shouldBe createdAt
      repositories(2).lastUpdatedAt shouldBe lastActiveAt
      repositories(2).repoType      shouldBe RepoType.Other
    }
  }

  "digitalServiceInfo" should {
    "convert the json string to DigitalServiceDetails" in {
      stubFor(
        get(urlEqualTo("/api/digital-services/service-1"))
          .willReturn(aResponse().withBody(JsonData.digitalServiceData))
      )

      val responseData =
        teamsAndRepositoriesConnector
          .digitalServiceInfo("service-1")(HeaderCarrierConverter.fromRequest(FakeRequest()))
          .futureValue
          .value

      responseData.name shouldBe "service-1"

      responseData.repositories.size shouldBe 3
    }
  }

  "allDigitalServices" should {
    "return all the digital service names" in {
      stubFor(
        get(urlEqualTo("/api/digital-services"))
          .willReturn(aResponse().withBody(JsonData.digitalServiceNamesData))
      )

      val digitalServiceNames: Seq[String] =
        teamsAndRepositoriesConnector
          .allDigitalServices(HeaderCarrierConverter.fromRequest(FakeRequest()))
          .futureValue

      digitalServiceNames shouldBe Seq("digital-service-1", "digital-service-2", "digital-service-3")
    }
  }

  "teams" should {
    "return all the teams and their repositories" in {
      stubFor(
        get(urlEqualTo("/api/teams?includeRepos=true"))
          .willReturn(aResponse().withBody(JsonData.teams))
      )

      val teams: Seq[Team] =
        teamsAndRepositoriesConnector
          .allTeams(HeaderCarrierConverter.fromRequest(FakeRequest()))
          .futureValue

      teams.size shouldBe 2
      teams      should contain theSameElementsAs
        Seq(
          Team(
            name           = TeamName("team1"),
            createdDate    = None,
            lastActiveDate = None,
            repos          = Some(Map(
                              RepoType.Service   -> Seq("service1", "service2"),
                              RepoType.Library   -> Seq("lib1", "lib2"),
                              RepoType.Prototype -> Seq(),
                              RepoType.Other     -> Seq("other1", "other2")
                             ))
          ),
          Team(
            name           = TeamName("team2"),
            createdDate    = None,
            lastActiveDate = None,
            repos          = Some(Map(
                               RepoType.Service   -> Seq("service3", "service4"),
                               RepoType.Library   -> Seq("lib3", "lib4"),
                               RepoType.Prototype -> Seq("prototype1"),
                               RepoType.Other     -> Seq("other3", "other4")
                             ))
          )
        )
    }
  }

   */
}
