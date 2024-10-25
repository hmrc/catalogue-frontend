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
import play.api.libs.json.Reads
import play.api.libs.ws.readableAsString
import uk.gov.hmrc.cataloguefrontend.jsondata.{JsonData, TeamsAndRepositoriesJsonData}
import uk.gov.hmrc.cataloguefrontend.util.DateHelper._
import uk.gov.hmrc.cataloguefrontend.test.{FakeApplicationBuilder, UnitSpec}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{JsonCodecs, WhatsRunningWhere}
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterType

import scala.io.Source
import scala.jdk.CollectionConverters._

class ServicePageSpec extends UnitSpec with FakeApplicationBuilder {

  override def beforeEach(): Unit =
    super.beforeEach()
    setupAuthEndpoint()
    setupEnableBranchProtectionAuthEndpoint()
    serviceEndpoint(GET, "/reports/repositories", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/service-configs/routes?serviceName=service-1", willRespondWith = (200, Some(JsonData.serviceConfigsServiceService1)))
    serviceEndpoint(GET, "/releases-api/apis/production", willRespondWith = (200, Some(JsonData.releasesApiContext)))
    serviceEndpoint(GET, "/service-configs?serviceName=service-1&routeType=frontend", willRespondWith = (200, Some(JsonData.serviceConfigsServiceService1)))
    serviceEndpoint(GET, "/api/repositories/service-1/module-dependencies?version=latest",willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/api/repositories/service-1/module-dependencies?version=0.0.1", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/service-configs/service-relationships/service-1",    willRespondWith = (200, Some(JsonData.serviceRelationships)))
    serviceEndpoint(GET, "/service-configs/service-relationships/service-name", willRespondWith = (200, Some(JsonData.serviceRelationships)))
    serviceEndpoint(GET, "/api/v2/repositories", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/service-configs/deployment-config?serviceName=repo1", willRespondWith = (200, Some(JsonData.deploymentConfigsService1)))
    serviceEndpoint(GET, "/service-configs/deployment-config?serviceName=service-1", willRespondWith = (200, Some(JsonData.deploymentConfigsService1)))
    serviceEndpoint(GET, "/service-metrics/service-1/log-metrics", willRespondWith = (200, Some("""
      [{
        "id": "slow-running-query",
        "displayName": "Slow Running Query",
        "environments": {"qa": {"kibanaLink": "http://some/link", "count": 13 }}
      }]""")))
    serviceEndpoint(GET, "/service-metrics/service-1/collections", willRespondWith = (200, Some("""[{"database": "database-1", "service": "service-1", "sizeBytes": 1024, "date": "2023-11-06", "collection": "collection-1", "environment": "qa", "queryTypes": []}]""")))
    serviceEndpoint(GET, "/service-commissioning-status/services/service-1/lifecycleStatus", willRespondWith = (200, Some("""{ "lifecycleStatus" : "Active" }""")))
    serviceEndpoint(GET, s"/vulnerabilities/api/reports/latest/counts?service=%22service-1%22", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, s"/vulnerabilities/api/reports/qa/counts?service=%22service-1%22"    , willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, s"/vulnerabilities/api/reports/production/counts?service=%22service-1%22", willRespondWith = (200, Some("[]")))
    ShutterType.values.map: shutterType =>
      serviceEndpoint(GET, s"/shutter-api/qa/${shutterType.asString}/states?serviceName=service-1", willRespondWith = (200, Some("[]")))
      serviceEndpoint(GET, s"/shutter-api/production/${shutterType.asString}/states?serviceName=service-1", willRespondWith = (200, Some("[]")))

  given Reads[WhatsRunningWhere] = JsonCodecs.whatsRunningWhereReads

  "A service page" should {
    "return a 404 when a Library is viewed as a service" in {
      serviceEndpoint(GET, "/api/v2/repositories/serv", willRespondWith = (200, Some(JsonData.libraryDetailsData)))

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

    "return a 404 when no repo exist with that name and does not have a service to repo name mapping in service configs" in {
      val serviceName = "serv"
      serviceEndpoint(GET, s"/api/v2/repositories/$serviceName", willRespondWith = (404, None))
      serviceEndpoint(GET, s"/service-configs/services/repo-name?serviceName=$serviceName",  willRespondWith = (404, None))
      val response = wsClient.url(s"http://localhost:$port/service/$serviceName").withAuthToken("Token token").get().futureValue
      response.status shouldBe 404
    }

    behave like ServicePageBehaviour(serviceName = "service-1", repoName = "service-1")
  }

  "A service page with a repository with different name" should {
    behave like ServicePageBehaviour("service-1", "repo1")
  }

  case class ServicePageBehaviour(serviceName: String, repoName: String) {
    trait Setup {
      if (serviceName == repoName)
        serviceEndpoint(GET, s"/api/v2/repositories/$serviceName", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.repositoryData(repoName))))
      else {
        serviceEndpoint(GET, s"/api/v2/repositories/$serviceName", willRespondWith = (404, None))

        serviceEndpoint(GET, s"/service-configs/services/repo-name?serviceName=$serviceName", willRespondWith = (200, Some("\"repo1\"")))

        serviceEndpoint(GET, s"/api/v2/repositories/$repoName", willRespondWith = (200, Some(TeamsAndRepositoriesJsonData.repositoryData(repoName))))
        serviceEndpoint(GET, s"/api/repositories/$repoName/module-dependencies?version=0.0.1" , willRespondWith = (200, Some("[]")))
        serviceEndpoint(GET, s"/api/repositories/$repoName/module-dependencies?version=latest", willRespondWith = (200, Some("[]")))
      }
    }

    "show the teams owning the service with github, ci and environment links and info box" in new Setup {
      serviceEndpoint(GET, s"/api/v2/repositories/$repoName/jenkins-jobs", willRespondWith = (200, Some(JsonData.serviceJenkinsBuildData)))

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

    "not render mongodb related links" when {
      "the service doesn't use mongo" in new Setup {
        serviceEndpoint(GET, "/service-metrics/service-1/collections", willRespondWith = (200, Some("""[]""")))

        val response = wsClient.url(s"http://localhost:$port/service/$serviceName").withAuthToken("Token token").get().futureValue
        response.status shouldBe 200

        val document = Jsoup.parse(response.body)
        document.select("#production-telemetry").eachText().asScala.mkString should not include "Slow Running Queries"
      }
    }
  }

  def readFile(jsonFilePath: String): String =
    val path = "__files/" + jsonFilePath
    try {
      Source.fromResource(path).getLines().mkString("\n")
    } catch {
      case _: NullPointerException => sys.error(s"Could not find file $path")
    }
}
