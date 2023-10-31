/*
 * Copyright 2023 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.jsoup.Jsoup
import uk.gov.hmrc.cataloguefrontend.DateHelper._
import uk.gov.hmrc.cataloguefrontend.JsonData._
import uk.gov.hmrc.cataloguefrontend.jsondata.TeamsAndRepositories._
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.JsonCodecs

import scala.io.Source

class ServicePageSpec extends UnitSpec with FakeApplicationBuilder {

  override def beforeEach(): Unit = {
    super.beforeEach()
    setupAuthEndpoint()
    setupEnableBranchProtectionAuthEndpoint()
    serviceEndpoint(GET, "/reports/repositories", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/service-configs/frontend-route/service-1",    willRespondWith = (200, Some(serviceConfigsServiceService1)))
    serviceEndpoint(GET, "/service-configs/frontend-route/service-name", willRespondWith = (200, Some(serviceConfigsServiceService1)))
    serviceEndpoint(GET, "/api/jenkins-url/service-1", willRespondWith = (200, Some(jenkinsData)))
    serviceEndpoint(GET, "/api/repositories/service-1/module-dependencies?version=latest",willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/api/repositories/service-1/module-dependencies?version=0.0.1", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/service-configs/service-relationships/service-1",    willRespondWith = (200, Some(serviceRelationships)))
    serviceEndpoint(GET, "/service-configs/service-relationships/service-name", willRespondWith = (200, Some(serviceRelationships)))
    serviceEndpoint(GET, "/api/v2/repositories", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/service-configs/deployment-config?serviceName=repo1", willRespondWith = (200, Some(deploymentConfigsService1)))
    serviceEndpoint(GET, "/service-configs/deployment-config?serviceName=service-1", willRespondWith = (200, Some(deploymentConfigsService1)))
    serviceEndpoint(GET, "/service-metrics/service-1/non-performant-queries", willRespondWith = (200, Some("""[{"service": "service-1", "environment": "qa", "queryTypes": []}]""")))
  }

  implicit val wrwf = JsonCodecs.whatsRunningWhereReads

  "A service page" should {
    "return a 404 when a Library is viewed as a service" in {
      serviceEndpoint(GET, "/api/v2/repositories/serv", willRespondWith = (200, Some(libraryDetailsData)))

      val response = wsClient.url(s"http://localhost:$port/service/serv").withAuthToken("Token token").get().futureValue
      response.status shouldBe 404
    }

    "return a 404 when no repo exist with that name and the service does not have config" in {
      serviceEndpoint(GET, "/api/v2/repositories/serv", willRespondWith = (404, None))
      serviceEndpoint(GET, "/service-configs/config-by-env/serv?latest=true",  willRespondWith = (200, Some("""{}""")))
      serviceEndpoint(GET, "/service-configs/config-by-env/serv?latest=false", willRespondWith = (200, Some("""{}""")))

      val response = wsClient.url(s"http://localhost:$port/service/serv").withAuthToken("Token token").get().futureValue
      response.status shouldBe 404
    }

    "return a 404 when no repo exist with that name and the service does not have a artifact name" in {
      serviceEndpoint(GET, "/api/v2/repositories/serv", willRespondWith = (404, None))

      val configRsp = """{
        "production": [{"source": "appConfig", "entries": {"key1": "value1"}}]
      }"""
      serviceEndpoint(GET, "/service-configs/config-by-env/serv?latest=true",  willRespondWith = (200, Some(configRsp)))
      serviceEndpoint(GET, "/service-configs/config-by-env/serv?latest=false", willRespondWith = (200, Some(configRsp)))
      val response = wsClient.url(s"http://localhost:$port/service/serv").withAuthToken("Token token").get().futureValue
      response.status shouldBe 404
    }

    "return a 500 when no repo exist with that name and the service have multiple artifact names in different environments" in {
      serviceEndpoint(GET, "/api/v2/repositories/serv", willRespondWith = (404, None))
      val configRsp = """{
        "production": [{"source": "appConfig", "entries": {"artifact_name": "repo1"}}],
        "qa":         [{"source": "appConfig", "entries": {"artifact_name": "repo2"}}]
      }"""
      serviceEndpoint(GET, "/service-configs/config-by-env/serv?latest=true",  willRespondWith = (200, Some(configRsp)))
      serviceEndpoint(GET, "/service-configs/config-by-env/serv?latest=false", willRespondWith = (200, Some(configRsp)))
      val response = wsClient.url(s"http://localhost:$port/service/serv").withAuthToken("Token token").get().futureValue
      response.status shouldBe 500
    }

    behave like ServicePageBehaviour(serviceName = "service-1", repoName = "service-1")
  }

  "A service page with a repository with different name" should {
    behave like ServicePageBehaviour("service-1", "repo1")
  }

  case class ServicePageBehaviour(serviceName: String, repoName: String) {
    trait Setup {
      if (serviceName == repoName)
        serviceEndpoint(GET, s"/api/v2/repositories/$serviceName", willRespondWith = (200, Some(repositoryData(repoName))))
      else {
        serviceEndpoint(GET, s"/api/v2/repositories/$serviceName", willRespondWith = (404, None))
        val configRsp = s"""{
          "production": [{"source": "appConfig", "entries": {"artifact_name": "$repoName"}}],
          "qa":         [{"source": "appConfig", "entries": {"artifact_name": "$repoName"}}]
        }"""
        serviceEndpoint(GET, s"/service-configs/config-by-env/$serviceName?latest=true",  willRespondWith = (200, Some(configRsp)))
        serviceEndpoint(GET, s"/service-configs/config-by-env/$serviceName?latest=false", willRespondWith = (200, Some(configRsp)))

        serviceEndpoint(GET, s"/api/v2/repositories/$repoName", willRespondWith = (200, Some(repositoryData(repoName))))
        serviceEndpoint(GET, s"/api/repositories/$repoName/module-dependencies?version=0.0.1" , willRespondWith = (200, Some("[]")))
        serviceEndpoint(GET, s"/api/repositories/$repoName/module-dependencies?version=latest", willRespondWith = (200, Some("[]")))
      }
    }

    "show the teams owning the service with github, ci and environment links and info box" in new Setup {
      serviceEndpoint(GET, s"/api/jenkins-jobs/$repoName", willRespondWith = (200, Some(serviceJenkinsBuildData)))

      serviceEndpoint(
        GET,
        s"/releases-api/whats-running-where/$serviceName",
        willRespondWith = (200, Some(
          s"""{
            "applicationName": "service-1",
            "versions": [
              {"environment": "production", "platform": "heritage", "versionNumber": "0.0.1", "lastSeen": "2020-02-14T00:59:33Z", "config": []},
              {"environment": "qa"        , "platform": "heritage", "versionNumber": "0.0.1", "lastSeen": "2020-02-14T01:00:14Z", "config": [] }
            ]
          }"""
        ))
      )
      val response = wsClient.url(s"http://localhost:$port/service/$serviceName").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200
      response.body     should include("links on this page are automatically generated")
      response.body     should include("teamA")
      response.body     should include("teamB")
      response.body     should include(s"$serviceName")
      response.body     should include("github.com")
      response.body     should include(s"http://jenkins/$serviceName/")
      response.body     should include("Grafana")
      response.body     should include("some description")

      response.body should include(JsonData.createdAt.displayFormat)
      response.body should include(JsonData.lastActiveAt.displayFormat)
    }

    "show shuttered environments when they are shuttered" in new Setup {

      val response = wsClient.url(s"http://localhost:$port/service/$serviceName").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200
      val document = Jsoup.parse(response.body)

      import scala.jdk.CollectionConverters._
      val qaTabElements = document.getElementById("qa-tab").children().asScala
      qaTabElements.exists(_.hasClass("shutter_badge")) && !qaTabElements.exists(_.hasClass("noshutter_badge"))

      val prodTabElements = document.getElementById("production-tab").children().asScala
      prodTabElements.exists(_.hasClass("noshutter_badge")) && !prodTabElements.exists(_.hasClass("shutter_badge"))
    }

    "render platform dependencies section" in new Setup {
      val response = wsClient.url(s"http://localhost:$port/service/$serviceName").withAuthToken("Token token").get().futureValue
      response.status shouldBe 200

      val document = Jsoup.parse(response.body)
      document.select("#platform-dependencies-latest").size() should be > 0
    }
  }

  def readFile(jsonFilePath: String): String = {
    val path = "__files/" + jsonFilePath
    try {
      Source.fromResource(path).getLines().mkString("\n")
    } catch {
      case _: NullPointerException => sys.error(s"Could not find file $path")
    }
  }
}
