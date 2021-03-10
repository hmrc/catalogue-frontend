/*
 * Copyright 2021 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.mockito.MockitoSugar
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfter, EitherValues, OptionValues}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.{FakeApplicationBuilder, JsonData}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

class TeamsAndRepositoriesConnectorSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfter
    with ScalaFutures
    with FakeApplicationBuilder
    with TypeCheckedTripleEquals
    with OptionValues
    with EitherValues
    with MockitoSugar {

  import JsonData.{createdAt, lastActiveAt}

  implicit val defaultPatienceConfig: PatienceConfig = PatienceConfig(Span(200, Millis), Span(15, Millis))

  private lazy val teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector =
    app.injector.instanceOf[TeamsAndRepositoriesConnector]

  "lookUpLink" should {

    "return a Link if exists" in {

      serviceEndpoint(
        GET,
        "/api/jenkins-url/serviceA",
        willRespondWith = (
          200,
          Some(
            """
              |	{
              |		"service": "serviceA",
              |   "jenkinsURL": "http.jenkins/serviceA"
              |	}
              | """.stripMargin
          )
        )
      )

      val response = teamsAndRepositoriesConnector
        .lookupLink("serviceA")(HeaderCarrierConverter.fromRequest(FakeRequest()))
        .futureValue

      response shouldBe Some(Link("serviceA", "Build", "http.jenkins/serviceA"))
    }

    "return None if Not Found" in {

      serviceEndpoint(
        GET,
        "/api/jenkins-url/serviceA",
        willRespondWith = (404, None)
      )

      val response = teamsAndRepositoriesConnector
        .lookupLink("serviceA")(HeaderCarrierConverter.fromRequest(FakeRequest()))
        .futureValue

      response shouldBe None
    }
  }

  "teamsByService" should {

    "return a list of team information for each given service" in {

      serviceEndpoint(
        POST,
        "/api/services?teamDetails=true",
        givenJsonBody = Some(Json.arr("serviceA", "serviceB").toString()),
        willRespondWith = (
          200,
          Some(
            """
          |	{
          |		"serviceA": ["teamA","teamB"],
          |		"serviceB": ["teamA"]
          |	}
          | """.stripMargin
          )
        )
      )

      val response = teamsAndRepositoriesConnector
        .teamsByService(Seq("serviceA", "serviceB"))(HeaderCarrierConverter.fromRequest(FakeRequest()))
        .futureValue

      response.size        shouldBe 2
      response("serviceA") shouldBe Seq(TeamName("teamA"), TeamName("teamB"))
      response("serviceB") shouldBe Seq(TeamName("teamA"))
    }
  }

  "repositoryDetails" should {
    "convert the json string to RepositoryDetails" in {
      serviceEndpoint(GET, "/api/repositories/service-1", willRespondWith = (200, Some(JsonData.serviceDetailsData)))
      serviceEndpoint(GET, "/api/jenkins-url/service-1", willRespondWith = (200, Some(JsonData.serviceJenkinsData)))

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
      responseData.environments should ===(
        Some(Seq(
          TargetEnvironment(
            Environment.Development,
            Seq(
              Link("jenkins", "Jenkins", "https://deploy-dev.co.uk/job/deploy-microservice"),
              Link("grafana", "Grafana", "https://grafana-dev.co.uk/#/dashboard")
            )
          ),
          TargetEnvironment(
            Environment.QA,
            Seq(
              Link("jenkins", "Jenkins", "https://deploy-qa.co.uk/job/deploy-microservice"),
              Link("grafana", "Grafana", "https://grafana-datacentred-sal01-qa.co.uk/#/dashboard")
            )
          ),
          TargetEnvironment(
            Environment.Production,
            Seq(
              Link("jenkins", "Jenkins", "https://deploy-prod.co.uk/job/deploy-microservice"),
              Link("grafana", "Grafana", "https://grafana-prod.co.uk/#/dashboard")
            )
          )
        )))

      responseData.repoType shouldBe RepoType.Service
      responseData.isArchived shouldBe false
    }
  }

  "allRepositories" should {
    "return all the repositories returned by the api" in {
      serviceEndpoint(GET, "/api/repositories", willRespondWith = 200 -> Some(JsonData.repositoriesData))

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
      serviceEndpoint(GET, "/api/repositories?archived=true", willRespondWith = 200 -> Some(JsonData.repositoriesData))

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
      serviceEndpoint(
        GET,
        "/api/digital-services/service-1",
        willRespondWith = (200, Some(JsonData.digitalServiceData)))

      val responseData =
        teamsAndRepositoriesConnector
          .digitalServiceInfo("service-1")(HeaderCarrierConverter.fromRequest(FakeRequest()))
          .futureValue
          .get

      responseData.name shouldBe "service-1"

      responseData.repositories.size should ===(3)
    }
  }

  "allDigitalServices" should {
    "return all the digital service names" in {
      serviceEndpoint(GET, "/api/digital-services", willRespondWith = (200, Some(JsonData.digitalServiceNamesData)))

      val digitalServiceNames: Seq[String] =
        teamsAndRepositoriesConnector
          .allDigitalServices(HeaderCarrierConverter.fromRequest(FakeRequest()))
          .futureValue

      digitalServiceNames shouldBe Seq("digital-service-1", "digital-service-2", "digital-service-3")
    }
  }

  "teamsWithRepositories" should {
    "return all the teams and their repositories" in {
      serviceEndpoint(GET, "/api/teams_with_repositories", willRespondWith = (200, Some(JsonData.teamsWithRepos)))

      val teams: Seq[Team] =
        teamsAndRepositoriesConnector
          .teamsWithRepositories(HeaderCarrierConverter.fromRequest(FakeRequest()))
          .futureValue

      teams.size shouldBe 2
      teams      should contain theSameElementsAs
        Seq(
          Team(
            TeamName("team1"),
            None,
            None,
            None,
            Some(
              Map(
                "Service"   -> Seq("service1", "service2"),
                "Library"   -> Seq("lib1", "lib2"),
                "Prototype" -> Seq(),
                "Other"     -> Seq("other1", "other2")
              ))
          ),
          Team(
            TeamName("team2"),
            None,
            None,
            None,
            Some(
              Map(
                "Service"   -> Seq("service3", "service4"),
                "Library"   -> Seq("lib3", "lib4"),
                "Prototype" -> Seq("prototype1"),
                "Other"     -> Seq("other3", "other4")
              ))
          )
        )
    }
  }
}
