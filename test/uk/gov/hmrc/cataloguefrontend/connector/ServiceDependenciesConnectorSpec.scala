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
import org.mockito
import org.mockito.Matchers.any
import org.mockito.Mockito
import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.{Configuration, Environment}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeHeaders
import uk.gov.hmrc.cataloguefrontend.WireMockEndpoints
import uk.gov.hmrc.cataloguefrontend.connector.model.{LibraryDependencyState, OtherDependenciesState, SbtPluginsDependenciesState, Version}

import scala.concurrent.Future
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet, HttpResponse}
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.http.HttpClient

class ServiceDependenciesConnectorSpec
  extends FreeSpec
    with Matchers
    with BeforeAndAfter
    with OneServerPerSuite
    with WireMockEndpoints
    with EitherValues
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with IntegrationPatience {

  implicit override lazy val app = new GuiceApplicationBuilder()
    .disable(classOf[com.kenshoo.play.metrics.PlayModule])
    .configure (
      Map(
        "microservice.services.service-dependencies.port" -> endpointPort,
        "microservice.services.service-dependencies.host" -> host
      )).build()


  val serviceDependenciesConnector = app.injector.instanceOf[ServiceDependenciesConnector]

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
          |  ],
          |  "otherDependenciesState": [
          |    {
          |      "name": "sbt",
          |      "currentVersion": {
          |        "major": 0,
          |        "minor": 13,
          |        "patch": 8
          |      },
          |      "latestVersion": {
          |        "major": 0,
          |        "minor": 13,
          |        "patch": 15
          |      }
          |    }
          |  ]
          |}""".stripMargin
      )))

      val response = serviceDependenciesConnector.getDependencies("repo1")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())).futureValue.value



      response.libraryDependenciesState.size shouldBe 2

      response.repositoryName shouldBe "repo1"
      response.libraryDependenciesState should contain theSameElementsAs
        Seq(
          LibraryDependencyState("frontend-bootstrap", Version(7, 11, 0), Some(Version(8, 80, 0))),
          LibraryDependencyState("play-config", Version(3, 0, 0), Some(Version(7, 70, 0)))
        )

      response.sbtPluginsDependenciesState should contain theSameElementsAs
        Seq(
          SbtPluginsDependenciesState("plugin-1", Version(1, 0, 0), Some(Version(1, 1, 0)), true),
          SbtPluginsDependenciesState("plugin-2", Version(2, 0, 0), Some(Version(2, 1, 0)), false)
        )
      response.otherDependenciesState should contain theSameElementsAs
        Seq(
          OtherDependenciesState("sbt", Version(0, 13, 8), Some(Version(0, 13, 15)))
        )

    }

    "return a None for non existing repository" in {

      serviceEndpoint(GET, "/api/dependencies/non-existing-repo", willRespondWith = (404, None))

      val response = serviceDependenciesConnector.getDependencies("non-existing-repo")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())).futureValue

      response shouldBe None

    }

    "return a None for if a communication error occurs" in {

      val mockedHttpClient = mock[HttpClient]
      Mockito.when(mockedHttpClient.GET(any())(any(), any(), any())).thenReturn(Future.failed(new RuntimeException("Boom!!")))
      val failingServiceDependenciesConnector = new ServiceDependenciesConnector(mockedHttpClient, Configuration(), mock[Environment]) {
        override def servicesDependenciesBaseUrl = "chicken.com"
      }

      val response = failingServiceDependenciesConnector.getDependencies("non-existing-repo")(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())).futureValue

      response shouldBe None
    }
  }

  "GET all dependencies for report" - {

    "return dependencies for all repositories" in {

      serviceEndpoint(GET, "/api/service-dependencies/dependencies", willRespondWith = (200, Some(
        """[
          |  {
          |    "repositoryName": "repo1",
          |    "libraryDependenciesState": [
          |      {
          |        "libraryName": "frontend-bootstrap",
          |        "currentVersion": {
          |          "major": 7,
          |          "minor": 11,
          |          "patch": 0
          |        } ,
          |        "latestVersion": {
          |          "major": 8,
          |          "minor": 80,
          |          "patch": 0
          |        }
          |      },
          |      {
          |        "libraryName": "play-config",
          |        "currentVersion": {
          |          "major": 3,
          |          "minor": 0,
          |          "patch": 0
          |        },
          |        "latestVersion": {
          |          "major": 7,
          |          "minor": 70,
          |          "patch": 0
          |        }
          |      }
          |    ],
          |    "sbtPluginsDependenciesState": [
          |      {
          |        "sbtPluginName": "plugin-1",
          |        "currentVersion": {
          |          "major": 1,
          |          "minor": 0,
          |          "patch": 0
          |        } ,
          |        "latestVersion": {
          |          "major": 1,
          |          "minor": 1,
          |          "patch": 0
          |        },
          |        "isExternal": true
          |      },
          |      {
          |        "sbtPluginName": "plugin-2",
          |        "currentVersion": {
          |          "major": 2,
          |          "minor": 0,
          |          "patch": 0
          |        },
          |        "latestVersion": {
          |          "major": 2,
          |          "minor": 1,
          |          "patch": 0
          |        },
          |        "isExternal": false
          |      }
          |    ],
          |    "otherDependenciesState": [
          |      {
          |        "name": "sbt",
          |        "currentVersion": {
          |          "major": 0,
          |          "minor": 13,
          |          "patch": 7
          |        },
          |        "latestVersion": {
          |          "major": 0,
          |          "minor": 13,
          |          "patch": 15
          |        }
          |      }
          |    ]
          |  },
          |  {
          |    "repositoryName": "repo2",
          |    "libraryDependenciesState": [
          |      {
          |        "libraryName": "some-lib-1",
          |        "currentVersion": {
          |          "major": 7,
          |          "minor": 77,
          |          "patch": 0
          |        } ,
          |        "latestVersion": {
          |          "major": 8,
          |          "minor": 80,
          |          "patch": 0
          |        }
          |      },
          |      {
          |        "libraryName": "some-lib-2",
          |        "currentVersion": {
          |          "major": 3,
          |          "minor": 0,
          |          "patch": 0
          |        },
          |        "latestVersion": {
          |          "major": 7,
          |          "minor": 70,
          |          "patch": 0
          |        }
          |      }
          |    ],
          |    "sbtPluginsDependenciesState": [
          |      {
          |        "sbtPluginName": "plugin-3",
          |        "currentVersion": {
          |          "major": 1,
          |          "minor": 0,
          |          "patch": 0
          |        } ,
          |        "latestVersion": {
          |          "major": 1,
          |          "minor": 1,
          |          "patch": 0
          |        },
          |        "isExternal": true
          |      },
          |      {
          |        "sbtPluginName": "plugin-4",
          |        "currentVersion": {
          |          "major": 2,
          |          "minor": 0,
          |          "patch": 0
          |        },
          |        "latestVersion": {
          |          "major": 2,
          |          "minor": 1,
          |          "patch": 0
          |        },
          |        "isExternal": false
          |      }
          |    ],
          |    "otherDependenciesState": [
          |      {
          |        "name": "sbt",
          |        "currentVersion": {
          |          "major": 0,
          |          "minor": 13,
          |          "patch": 8
          |        },
          |        "latestVersion": {
          |          "major": 0,
          |          "minor": 13,
          |          "patch": 15
          |        }
          |      }
          |    ]
          |  }
          |]""".stripMargin
      )))

      val response = serviceDependenciesConnector.getAllDependencies()(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())).futureValue

      response.size shouldBe 2

      response.head.libraryDependenciesState.size shouldBe 2

      response.head.repositoryName shouldBe "repo1"
      response.head.libraryDependenciesState should contain theSameElementsAs
        Seq(
          LibraryDependencyState("frontend-bootstrap", Version(7, 11, 0), Some(Version(8, 80, 0))),
          LibraryDependencyState("play-config", Version(3, 0, 0), Some(Version(7, 70, 0)))
        )

      response.head.sbtPluginsDependenciesState should contain theSameElementsAs
        Seq(
          SbtPluginsDependenciesState("plugin-1", Version(1, 0, 0), Some(Version(1, 1, 0)), true),
          SbtPluginsDependenciesState("plugin-2", Version(2, 0, 0), Some(Version(2, 1, 0)), false)
        )

      response.head.otherDependenciesState should contain theSameElementsAs
        Seq(
          OtherDependenciesState("sbt", Version(0, 13, 7), Some(Version(0, 13, 15)))
        )



      response.last.libraryDependenciesState.size shouldBe 2

      response.last.repositoryName shouldBe "repo2"
      response.last.libraryDependenciesState should contain theSameElementsAs
        Seq(
          LibraryDependencyState("some-lib-1", Version(7, 77, 0), Some(Version(8, 80, 0))),
          LibraryDependencyState("some-lib-2", Version(3, 0, 0), Some(Version(7, 70, 0)))
        )

      response.last.sbtPluginsDependenciesState should contain theSameElementsAs
        Seq(
          SbtPluginsDependenciesState("plugin-3", Version(1, 0, 0), Some(Version(1, 1, 0)), true),
          SbtPluginsDependenciesState("plugin-4", Version(2, 0, 0), Some(Version(2, 1, 0)), false)
        )

      response.last.otherDependenciesState should contain theSameElementsAs
        Seq(
          OtherDependenciesState("sbt", Version(0, 13, 8), Some(Version(0, 13, 15)))
        )
    }
  }

}
