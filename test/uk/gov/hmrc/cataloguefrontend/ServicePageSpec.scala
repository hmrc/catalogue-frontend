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

package uk.gov.hmrc.cataloguefrontend

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.jsoup.Jsoup
import uk.gov.hmrc.cataloguefrontend.DateHelper._
import uk.gov.hmrc.cataloguefrontend.JsonData._
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.JsonCodecs

import scala.io.Source

class ServicePageSpec extends UnitSpec with FakeApplicationBuilder {

  override def beforeEach(): Unit = {
    super.beforeEach()
    setupAuthEndpoint()
    setupEnableBranchProtectionAuthEndpoint()
    serviceEndpoint(GET, "/reports/repositories", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/frontend-route/service-1", willRespondWith = (200, Some(configServiceService1)))
    serviceEndpoint(GET, "/frontend-route/service-name", willRespondWith = (200, Some(configServiceService1)))
    serviceEndpoint(GET, "/api/jenkins-url/service-1", willRespondWith = (200, Some(jenkinsData)))
    serviceEndpoint(GET, "/api/module-dependencies/service-1",willRespondWith = (404, None))
    serviceEndpoint(GET, "/api/module-dependencies/service-1?version=0.0.1",willRespondWith = (404, None))
    serviceEndpoint(GET, "/app-config/service-1", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/app-config/service-name", willRespondWith = (200, Some("[]")))
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
      serviceEndpoint(GET, "/config-by-key/serv", willRespondWith = (200, Some("""{}""")))

      val response = wsClient.url(s"http://localhost:$port/service/serv").withAuthToken("Token token").get().futureValue
      response.status shouldBe 404
    }

    "return a 404 when no repo exist with that name and the service does not have a artifact name" in {
      serviceEndpoint(GET, "/api/v2/repositories/serv", willRespondWith = (404, None))
      serviceEndpoint(
        GET,
        "/config-by-key/serv",
        willRespondWith = (200, Some(
          """{
            "key1": {
              "production": [
                {
                  "source": "appConfig",
                  "precedence": 10,
                  "value": "value1"
                }
              ]
            }
          }"""
        ))
      )

      val response = wsClient.url(s"http://localhost:$port/service/serv").withAuthToken("Token token").get().futureValue
      response.status shouldBe 404
    }

    "return a 500 when no repo exist with that name and the service have multiple artifact names in different environments" in {
      serviceEndpoint(GET, "/api/v2/repositories/serv", willRespondWith = (404, None))
      serviceEndpoint(
        GET,
        "/config-by-key/serv",
        willRespondWith = (200, Some(
          """{
            "artifact_name": {
              "production": [
                {
                  "source": "appConfig",
                  "precedence": 10,
                  "value": "repo1"
                }
              ],
              "qa": [
                {
                  "source": "appConfig",
                  "precedence": 10,
                  "value": "repo2"
                }
              ]
            }
          }"""
        ))
      )

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
        serviceEndpoint(
          GET,
          s"/config-by-key/$serviceName",
          willRespondWith = (200, Some(
            s"""{
              "artifact_name": {
                "production": [
                  {
                    "source": "appConfig",
                    "precedence": 10,
                    "value": "$repoName"
                  }
                ],
                "qa": [
                  {
                    "source": "appConfig",
                    "precedence": 10,
                    "value": "$repoName"
                  }
                ]
              }
            }"""
          ))
        )

        serviceEndpoint(GET, s"/api/v2/repositories/$repoName", willRespondWith = (200, Some(repositoryData(repoName))))
      }
    }

    "show the teams owning the service with github, ci and environment links and info box" in new Setup {
      serviceEndpoint(GET, s"/api/jenkins-jobs/$repoName", willRespondWith = (200, Some(serviceJenkinsBuildData)))
      serviceEndpoint(GET, "/api/module-dependencies/service-1?version=0.0.1",willRespondWith = (404, None))

      serviceEndpoint(
        GET,
        s"/releases-api/whats-running-where/$serviceName",
        willRespondWith = (200, Some(
          s"""{
            "applicationName": "service-1",
            "versions": [
              {"environment": "production", "platform": "heritage", "versionNumber": "0.0.1", "lastSeen": "2020-02-14T00:59:33Z" },
              {"environment": "qa"        , "platform": "heritage", "versionNumber": "0.0.1", "lastSeen": "2020-02-14T01:00:14Z" }
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

      response.body should include(createdAt.displayFormat)
      response.body should include(lastActiveAt.displayFormat)
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
