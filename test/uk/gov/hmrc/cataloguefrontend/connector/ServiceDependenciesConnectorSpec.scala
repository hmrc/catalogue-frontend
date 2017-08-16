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
import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.OneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeHeaders
import uk.gov.hmrc.cataloguefrontend.WireMockEndpoints
import uk.gov.hmrc.cataloguefrontend.connector.model.{LibraryDependencyState, SbtPluginsDependenciesState, Version}
import uk.gov.hmrc.play.http.hooks.HttpHook
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpResponse}

import scala.concurrent.Future

class ServiceDependenciesConnectorSpec extends FreeSpec with Matchers with BeforeAndAfter with OneServerPerSuite with WireMockEndpoints with EitherValues with OptionValues with ScalaFutures with IntegrationPatience {

  implicit override lazy val app = new GuiceApplicationBuilder()
    .disable(classOf[com.kenshoo.play.metrics.PlayModule])
    .configure (
      Map(
        "microservice.services.service-dependencies.port" -> endpointPort,
        "microservice.services.service-dependencies.host" -> host
      )).build()


  "GET Dependencies" - {

    "return a list of dependencies for a repository" in {

      serviceEndpoint(GET, "/api/service-dependencies/dependencies/repo1", willRespondWith = (200, Some(
        """{
          |  "repositoryName": "repo1",
          |  "libraryDependenciesState": [
          |    {
          |      "libraryName": "frontend-bootstrap",
          |      "currentVersion": {
          |        "major": 7,
          |        "minor": 11,
          |        "patch": 0
          |      } ,
          |      "latestVersion": {
          |        "major": 8,
          |        "minor": 80,
          |        "patch": 0
          |      }
          |    },
          |    {
          |      "libraryName": "play-config",
          |      "currentVersion": {
          |        "major": 3,
          |        "minor": 0,
          |        "patch": 0
          |      },
          |      "latestVersion": {
          |        "major": 7,
          |        "minor": 70,
          |        "patch": 0
          |      }
          |    }
          |  ],
          |  "sbtPluginsDependenciesState": [
          |    {
          |      "sbtPluginName": "plugin-1",
          |      "currentVersion": {
          |        "major": 1,
          |        "minor": 0,
          |        "patch": 0
          |      } ,
          |      "latestVersion": {
          |        "major": 1,
          |        "minor": 1,
          |        "patch": 0
          |      },
          |      "isExternal": true
          |    },
          |    {
          |      "sbtPluginName": "plugin-2",
          |      "currentVersion": {
          |        "major": 2,
          |        "minor": 0,
          |        "patch": 0
          |      },
          |      "latestVersion": {
          |        "major": 2,
          |        "minor": 1,
          |        "patch": 0
          |      },
          |      "isExternal": false
          |    }
          |  ]
          |}""".stripMargin
      )))

      val response = ServiceDependenciesConnector.getDependencies("repo1")(HeaderCarrier.fromHeadersAndSession(FakeHeaders())).futureValue

      response.value.libraryDependenciesState.size shouldBe 2

      response.value.repositoryName shouldBe "repo1"
      response.value.libraryDependenciesState should contain theSameElementsAs
        Seq(
          LibraryDependencyState("frontend-bootstrap", Version(7, 11, 0), Some(Version(8, 80, 0))),
          LibraryDependencyState("play-config", Version(3, 0, 0), Some(Version(7, 70, 0)))
        )
      
      response.value.sbtPluginsDependenciesState should contain theSameElementsAs
        Seq(
          SbtPluginsDependenciesState("plugin-1", Version(1, 0, 0), Some(Version(1, 1, 0)), true),
          SbtPluginsDependenciesState("plugin-2", Version(2, 0, 0), Some(Version(2, 1, 0)), false)
        )
    }

    "return a None for non existing repository" in {

      serviceEndpoint(GET, "/api/dependencies/non-existing-repo", willRespondWith = (404, None))

      val response = ServiceDependenciesConnector.getDependencies("non-existing-repo")(HeaderCarrier.fromHeadersAndSession(FakeHeaders())).futureValue

      response shouldBe None

    }

    "return a None for if a communication error occurs" in {

      val failingServiceDependenciesConnector = new ServiceDependenciesConnector {
        override def servicesDependenciesBaseUrl: String = "some.url"

        override val http: HttpGet = new HttpGet {
          override protected def doGet(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = Future.failed(new RuntimeException("Boom!!"))
          override val hooks: Seq[HttpHook] = Nil
        }
      }

      val response = failingServiceDependenciesConnector.getDependencies("non-existing-repo")(HeaderCarrier.fromHeadersAndSession(FakeHeaders())).futureValue

      response shouldBe None
    }
  }

}
