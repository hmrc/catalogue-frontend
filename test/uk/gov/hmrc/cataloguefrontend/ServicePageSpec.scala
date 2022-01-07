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
import play.api.libs.ws._
import uk.gov.hmrc.cataloguefrontend.DateHelper._
import uk.gov.hmrc.cataloguefrontend.JsonData._
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.JsonCodecs

class ServicePageSpec extends UnitSpec with FakeApplicationBuilder {

  private[this] lazy val ws = app.injector.instanceOf[WSClient]

  override def beforeEach(): Unit = {
    super.beforeEach()
    serviceEndpoint(GET, "/reports/repositories", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/frontend-route/service-1", willRespondWith = (200, Some(configServiceService1)))
    serviceEndpoint(GET, "/frontend-route/service-name", willRespondWith = (200, Some(configServiceService1)))
  }

  implicit val wrwf = JsonCodecs.whatsRunningWhereReads

  "A service page" should {
    "return a 404 when a Library is viewed as a service" in {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (200, Some(libraryDetailsData)))
      val response = ws.url(s"http://localhost:$port/service/serv").get.futureValue
      response.status shouldBe 404
    }

    "return a 404 when no repo exist with that name and the service does not have config" in {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (404, None))
      serviceEndpoint(GET, "/config-by-key/serv", willRespondWith = (200, Some("""{}""")))

      val response = ws.url(s"http://localhost:$port/service/serv").get.futureValue
      response.status shouldBe 404
    }

    "return a 404 when no repo exist with that name and the service does not have a artifact name" in {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (404, None))
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

      val response = ws.url(s"http://localhost:$port/service/serv").get.futureValue
      response.status shouldBe 404
    }

    "return a 500 when no repo exist with that name and the service have multiple artifact names in different environments" in {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (404, None))
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

      val response = ws.url(s"http://localhost:$port/service/serv").get.futureValue
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
        serviceEndpoint(GET, s"/api/repositories/$serviceName", willRespondWith = (200, Some(repositoryData(repoName))))
      else {
        serviceEndpoint(GET, s"/api/repositories/$serviceName", willRespondWith = (404, None))
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

        serviceEndpoint(GET, s"/api/repositories/$repoName", willRespondWith = (200, Some(repositoryData(repoName))))
      }
    }

    "show the teams owning the service with github, ci and environment links and info box" in new Setup {
      serviceEndpoint(GET, s"/api/jenkins-url/$repoName", willRespondWith = (200, Some(serviceJenkinsData)))
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

      val response = ws.url(s"http://localhost:$port/service/$serviceName").get.futureValue
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
      val response = ws.url(s"http://localhost:$port/service/$serviceName").get.futureValue
      response.status shouldBe 200
      val document = Jsoup.parse(response.body)

      import scala.collection.JavaConverters._
      val qaTabElements = document.getElementById("qa-tab").children().asScala
      qaTabElements.exists(_.hasClass("shutter_badge")) && !qaTabElements.exists(_.hasClass("noshutter_badge"))

      val prodTabElements = document.getElementById("production-tab").children().asScala
      prodTabElements.exists(_.hasClass("noshutter_badge")) && !prodTabElements.exists(_.hasClass("shutter_badge"))
    }

    "link to environments" should {
      "show only show links to envs for which the service is deployed to" in new Setup {
        serviceEndpoint(GET, s"/api/jenkins-url/$repoName", willRespondWith = (200, Some(serviceJenkinsData)))
        serviceEndpoint(
          GET,
          s"/releases-api/whats-running-where/$serviceName",
          willRespondWith = (200, Some(
            """{
              "applicationName": "service-1",
              "versions": [
                {"environment": "production",  "platform": "heritage", "versionNumber": "0.0.1", "lastSeen": "2020-02-14T00:59:33Z" },
                {"environment": "development", "platform": "heritage", "versionNumber": "0.0.1", "lastSeen": "2020-02-14T01:00:14Z" }
              ]
            }"""
          ))
        )
        val response = ws.url(s"http://localhost:$port/service/$serviceName").get.futureValue
        response.status shouldBe 200

        response.body should include("links on this page are automatically generated")
        response.body should include("teamA")
        response.body should include("teamB")
        response.body should include("service-1")
        response.body should include("github.com")
        response.body should include("http://jenkins/service-1/")
        response.body should include("Grafana")

        response.body should include("some description")

        response.body should include(createdAt.displayFormat)
        response.body should include(lastActiveAt.displayFormat)

        response.body should include("https://grafana-prod.co.uk/#/dashboard")
        response.body should include("https://grafana-dev.co.uk/#/dashboard")
        response.body should not include "https://grafana-datacentred-sal01-qa.co.uk/#/dashboard"
      }

      "show 'Not deployed' for envs in which the service is not deployed" in new Setup  {
        val response = ws.url(s"http://localhost:$port/service/$serviceName").get.futureValue
        response.status shouldBe 200

        // Links for environments should not be present
        response.body should not include regex("""https:\/\/(?!grafana-dev).*\/#\/dashboard""")

        countSubstring(response.body, "Not deployed") shouldBe 6

        def countSubstring(str: String, substr: String) =
          substr.r.findAllMatchIn(str).length
      }

      "omit Jenkins from telemetry links" in new Setup {
        serviceEndpoint(
          GET,
          s"/releases-api/whats-running-where/$serviceName",
          willRespondWith = (200, Some(
            """{
              "applicationName":"service-1",
              "versions":[
                {"environment":"development","platform":"heritage","versionNumber":"0.0.1","lastSeen":"2020-02-14T01:00:14Z"}
              ]
            }"""
          ))
        )

        val response = ws.url(s"http://localhost:$port/service/$serviceName").get.futureValue
        response.status shouldBe 200

        response.body should not include "Jenkins"
        response.body should include("Grafana")
      }
    }

    "render platform dependencies section" in new Setup {
      val response = ws.url(s"http://localhost:$port/service/$serviceName").get.futureValue
      response.status shouldBe 200

      val document = Jsoup.parse(response.body)
      document.select("#platform-dependencies-latest").size() should be > 0
    }
  }
}
