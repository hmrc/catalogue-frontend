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

package uk.gov.hmrc.cataloguefrontend.connector

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.mockito.Matchers.any
import org.mockito.Mockito
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{Millis, Span}
import org.scalatestplus.play.OneServerPerSuite
import play.api
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Writes
import play.api.test.FakeHeaders
import uk.gov.hmrc.cataloguefrontend.TeamsAndRepositoriesConnector.{ConnectionError, HTTPError}
import uk.gov.hmrc.cataloguefrontend.{Environment, JsonData, Link, RepoType, RepositoryDetails, RepositoryDisplayDetails, Team, TeamsAndRepositoriesConnector, WireMockEndpoints}
import uk.gov.hmrc.play.http._

import scala.concurrent.Future
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.http.HttpClient

class TeamsAndRepositoriesConnectorSpec extends WordSpec with Matchers with BeforeAndAfter with ScalaFutures with OneServerPerSuite with WireMockEndpoints with TypeCheckedTripleEquals with OptionValues with EitherValues with MockitoSugar {

  import uk.gov.hmrc.cataloguefrontend.JsonData._
  implicit val defaultPatienceConfig = PatienceConfig(Span(200, Millis), Span(15, Millis))

  implicit override lazy val app = new GuiceApplicationBuilder()
    .configure(
    "microservice.services.teams-and-services.host" -> host,
    "microservice.services.teams-and-services.port" -> endpointPort,
    "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler"
  ).build()

  val teamsAndRepositoriesConnector = app.injector.instanceOf[TeamsAndRepositoriesConnector]

  "teamsByService" should {

    "return a list of team information for each given service" in {

      serviceEndpoint(POST, "/api/services?teamDetails=true", willRespondWith = (200, Some(
        """
          |	{
          |		"serviceA": ["teamA","teamB"],
          |		"serviceB": ["teamA"]
          |	}
          | """.stripMargin
      )), givenJsonBody = Some("[\"serviceA\",\"serviceB\"]"))

      val response = teamsAndRepositoriesConnector.teamsByService(Seq("serviceA", "serviceB"))(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())).futureValue

      response.size shouldBe 2
      response("serviceA") shouldBe Seq("teamA", "teamB")
      response("serviceB") shouldBe Seq("teamA")

    }
  }

  "repositoryDetails" should {
    "convert the json string to RepositoryDetails" in {
      serviceEndpoint(GET, "/api/repositories/service-1", willRespondWith = (200, Some(JsonData.serviceDetailsData)))

      val responseData: RepositoryDetails =
        teamsAndRepositoriesConnector
          .repositoryDetails("service-1")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
          .futureValue
          .value

      responseData.name shouldBe "service-1"
      responseData.description shouldBe "some description"
      responseData.createdAt shouldBe createdAt
      responseData.lastActive shouldBe lastActiveAt
      responseData.teamNames should ===(Seq("teamA", "teamB"))
      responseData.githubUrls should ===(Seq(Link("github", "github.com", "https://github.com/hmrc/service-1")))
      responseData.ci should ===(Seq(Link("open1", "open 1", "http://open1/service-1"), Link("open2", "open 2", "http://open2/service-2")))
      responseData.environments should ===(Some(Seq(
        Environment(
          "Dev",
          Seq(
            Link("jenkins", "Jenkins", "https://deploy-dev.co.uk/job/deploy-microservice"),
            Link("grafana", "Grafana", "https://grafana-dev.co.uk/#/dashboard")
          )),
                Environment(
          "QA",
          Seq(
            Link("jenkins", "Jenkins", "https://deploy-qa.co.uk/job/deploy-microservice"),
            Link("grafana", "Grafana", "https://grafana-datacentred-sal01-qa.co.uk/#/dashboard")
          )),
        Environment(
          "Production",
          Seq(
            Link("jenkins", "Jenkins", "https://deploy-prod.co.uk/job/deploy-microservice"),
            Link("grafana", "Grafana", "https://grafana-prod.co.uk/#/dashboard")
          )
        ))))

      responseData.repoType should ===(RepoType.Service)
    }
  }

  "allRepositories" should {
    "return all the repositories returned by the api" in {
      serviceEndpoint(GET, "/api/repositories", willRespondWith = (200, Some(JsonData.repositoriesData)))

      val repositories: Seq[RepositoryDisplayDetails] = teamsAndRepositoriesConnector.allRepositories(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())).futureValue

      repositories(0).name shouldBe "teamA-serv"
      repositories(0).createdAt shouldBe JsonData.createdAt
      repositories(0).lastUpdatedAt shouldBe JsonData.lastActiveAt
      repositories(0).repoType shouldBe RepoType.Service

      repositories(1).name shouldBe "teamB-library"
      repositories(1).createdAt shouldBe JsonData.createdAt
      repositories(1).lastUpdatedAt shouldBe JsonData.lastActiveAt
      repositories(1).repoType shouldBe RepoType.Library

      repositories(2).name shouldBe "teamB-other"
      repositories(2).createdAt shouldBe JsonData.createdAt
      repositories(2).lastUpdatedAt shouldBe JsonData.lastActiveAt
      repositories(2).repoType shouldBe RepoType.Other

    }
  }


  "digitalServiceInfo" should {
    "convert the json string to DigitalServiceDetails" in {
      serviceEndpoint(GET, "/api/digital-services/service-1", willRespondWith = (200, Some(JsonData.digitalServiceData)))

      val responseData =
        teamsAndRepositoriesConnector
          .digitalServiceInfo("service-1")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
          .futureValue
          .right.value

      responseData.name shouldBe "service-1"

      responseData.repositories.size should ===(3)
    }

    "return a http error with the status code returned" in {
      serviceEndpoint(GET, "/api/digital-services/non-existing-service", willRespondWith = (300, None))

      val responseData =
        teamsAndRepositoriesConnector
          .digitalServiceInfo("non-existing-service")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
          .futureValue

      responseData.left.value shouldBe HTTPError(300)
    }

    "return a connection error if the connector fails to connect" in {
      val mockedHttpClient = mock[HttpClient]
      val exception = new RuntimeException("boom!")
      Mockito.when(mockedHttpClient.GET(any())(any(), any(), any())).thenReturn(Future.failed(exception))

      val responseData =
        teamsAndRepositoriesWithMockedHttp(mockedHttpClient)
          .digitalServiceInfo("non-existing-service")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
          .futureValue

      responseData.left.value shouldBe ConnectionError(exception)
    }

  }

  "allDigitalServices" should {
    "return all the digital service names" in {
      serviceEndpoint(GET, "/api/digital-services", willRespondWith = (200, Some(JsonData.digitalServiceNamesData)))

      val digitalServiceNames: Seq[String] =
        teamsAndRepositoriesConnector.allDigitalServices(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())).futureValue

      digitalServiceNames shouldBe Seq("digital-service-1", "digital-service-2", "digital-service-3")
    }
  }

  "teamsWithRepositories" should {
    "return all the teams and their repositories" in {
      serviceEndpoint(GET, "/api/teams_with_repositories", willRespondWith = (200, Some(JsonData.teamsWithRepos)))

      val teams: Seq[Team] =
        teamsAndRepositoriesConnector.teamsWithRepositories()(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())).futureValue

      teams.size shouldBe 2
      teams should contain theSameElementsAs
        Seq(
          Team("team1", None, None, None, Some(Map(
            "Service" -> Seq("service1", "service2"),
            "Library" -> Seq("lib1", "lib2"),
            "Prototype" -> Seq(),
            "Other" -> Seq("other1", "other2")
          ))),
          Team("team2", None, None, None, Some(Map(
            "Service" -> Seq("service3", "service4"),
            "Library" -> Seq("lib3", "lib4"),
            "Prototype" -> Seq("prototype1"),
            "Other" -> Seq("other3", "other4")
          )))
        )
    }
  }



  def teamsAndRepositoriesWithMockedHttp(httpClient: HttpClient) = new TeamsAndRepositoriesConnector(httpClient, Configuration(), mock[api.Environment]) {
    override def teamsAndServicesBaseUrl: String = "someUrl"
  }
}