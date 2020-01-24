/*
 * Copyright 2020 HM Revenue & Customs
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
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws._
import uk.gov.hmrc.cataloguefrontend.DateHelper._
import uk.gov.hmrc.cataloguefrontend.JsonData._
import uk.gov.hmrc.cataloguefrontend.connector.{DeploymentVO, EnvironmentMapping, ServiceDeploymentInformation}
import uk.gov.hmrc.cataloguefrontend.connector.model.Version
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterStatusValue, ShutterType}
import uk.gov.hmrc.play.test.UnitSpec

class ServicePageSpec extends UnitSpec with GuiceOneServerPerSuite with WireMockEndpoints {

  override def fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.teams-and-repositories.port"   -> endpointPort,
      "microservice.services.teams-and-repositories.host"   -> host,
      "microservice.services.indicators.port"           -> endpointPort,
      "microservice.services.indicators.host"           -> host,
      "microservice.services.service-dependencies.host" -> host,
      "microservice.services.service-dependencies.port" -> endpointPort,
      "microservice.services.service-deployments.port"  -> endpointPort,
      "microservice.services.service-deployments.host"  -> host,
      "microservice.services.leak-detection.port"       -> endpointPort,
      "microservice.services.leak-detection.host"       -> host,
      "microservice.services.service-configs.port"      -> endpointPort,
      "microservice.services.service-configs.host"      -> host,
      "microservice.services.shutter-api.port"          -> endpointPort,
      "microservice.services.shutter-api.host"          -> host,
      "play.http.requestHandler"                        -> "play.api.http.DefaultHttpRequestHandler",
      "metrics.jvm"                                     -> false
    )
    .build()

  private[this] lazy val ws = app.injector.instanceOf[WSClient]

  override def beforeEach(): Unit = {
    super.beforeEach()
    serviceEndpoint(GET, "/reports/repositories", willRespondWith = (200, Some("[]")))
    serviceEndpoint(GET, "/frontend-route/service-1", willRespondWith = (200, Some(configServiceService1)))
    serviceEndpoint(GET, "/frontend-route/service-name", willRespondWith = (200, Some(configServiceService1)))
  }

  implicit val sdi = ServiceDeploymentInformation.format

  "A service page" should {

    "return a 404 when teams and services returns a 404" in {
      serviceEndpoint(GET, "/frontend-route/serv", willRespondWith = (200, Some(configServiceEmpty)))
      serviceEndpoint(GET, "/api/services/serv", willRespondWith = (404, None))

      val response = await(ws.url(s"http://localhost:$port/repositories/serv").get)
      response.status shouldBe 404
    }

    "return a 404 when a Library is viewed as a service" in {
      serviceEndpoint(GET, "/frontend-route/serv", willRespondWith = (200, Some(configServiceEmpty)))
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (200, Some(libraryDetailsData)))
      serviceEndpoint(GET, "/api/whatsrunningwhere/serv",
        willRespondWith = (200, Some(Json.toJson(Some(ServiceDeploymentInformation("serv", Nil))).toString())))
      serviceEndpoint(GET, "/shutter-api/production/frontend/states/serv"  , willRespondWith = (200, Some(shutterApiData(ShutterType.Frontend, Environment.Production, ShutterStatusValue.Unshuttered))))
      serviceEndpoint(GET, "/shutter-api/externaltest/frontend/states/serv", willRespondWith = (404, None))
      serviceEndpoint(GET, "/shutter-api/qa/frontend/states/serv"          , willRespondWith = (404, None))
      serviceEndpoint(GET, "/shutter-api/staging/frontend/states/serv"     , willRespondWith = (404, None))
      serviceEndpoint(GET, "/shutter-api/integration/frontend/states/serv" , willRespondWith = (404, None))
      serviceEndpoint(GET, "/shutter-api/development/frontend/states/serv" , willRespondWith = (404, None))
      serviceEndpoint(GET, "/api/sluginfo?name=serv"                       , willRespondWith = (200, Some(serviceDependenciesData)))

      val response = await(ws.url(s"http://localhost:$port/service/serv").get)
      response.status shouldBe 404
    }

    "show the teams owning the service with github, ci and environment links and info box" in {
      serviceEndpoint(GET, "/api/repositories/service-1", willRespondWith = (200, Some(serviceDetailsData)))
      serviceEndpoint(GET, "/api/jenkins-url/service-1", willRespondWith = (200, Some(serviceJenkinsData)))
      serviceEndpoint(
        GET,
        "/api/whatsrunningwhere/service-1",
        willRespondWith = (
          200,
          Some(
            Json
              .toJson(Some(ServiceDeploymentInformation(
                "service-1",
                Seq(
                  DeploymentVO(EnvironmentMapping("production", Environment.Production), "skyscape-farnborough", Version("0.0.1")),
                  DeploymentVO(EnvironmentMapping("qa", Environment.QA), "skyscape-farnborough", Version("0.0.1"))
                )
              )))
              .toString()))
      )
      serviceEndpoint(GET, "/shutter-api/production/frontend/states/service-1"  , willRespondWith = (200, Some(shutterApiData(ShutterType.Frontend, Environment.Production, ShutterStatusValue.Unshuttered))))
      serviceEndpoint(GET, "/shutter-api/externaltest/frontend/states/service-1", willRespondWith = (404, None))
      serviceEndpoint(GET, "/shutter-api/qa/frontend/states/service-1"          , willRespondWith = (404, None))
      serviceEndpoint(GET, "/shutter-api/staging/frontend/states/service-1"     , willRespondWith = (404, None))
      serviceEndpoint(GET, "/shutter-api/integration/frontend/states/service-1" , willRespondWith = (404, None))
      serviceEndpoint(GET, "/shutter-api/development/frontend/states/service-1" , willRespondWith = (404, None))
      serviceEndpoint(GET, "/api/sluginfo?name=service-1"                       , willRespondWith = (200, Some(serviceDependenciesData)))

      val response = await(ws.url(s"http://localhost:$port/service/service-1").get)
      response.status shouldBe 200
      response.body   should include("links on this page are automatically generated")
      response.body   should include("teamA")
      response.body   should include("teamB")
      response.body   should include("service-1")
      response.body   should include("github.com")
      response.body   should include("http://jenkins/service-1/")
      response.body   should include("Grafana")
      response.body   should include("some description")

      response.body should include(createdAt.displayFormat)
      response.body should include(lastActiveAt.displayFormat)
    }

    "show shuttered environments when they are shuttered" in {
      FeatureSwitch.enable(CatalogueFrontendSwitches.shuttering)
      serviceEndpoint(GET, "/api/repositories/service-1", willRespondWith = (200, Some(serviceDetailsData)))
      serviceEndpoint(
        GET,
        "/api/whatsrunningwhere/service-1",
        willRespondWith = (200, Some(Json.toJson(Some(ServiceDeploymentInformation("service-1",
                Seq(
                  DeploymentVO(EnvironmentMapping("production", Environment.Production), "skyscape-farnborough", Version("0.0.1")),
                  DeploymentVO(EnvironmentMapping("qa", Environment.QA), "skyscape-farnborough", Version("0.0.1"))
                )))).toString())))
      serviceEndpoint(GET, "/shutter-api/production/frontend/states/service-1"  , willRespondWith = (200, Some(shutterApiData(ShutterType.Frontend, Environment.Production, ShutterStatusValue.Unshuttered))))
      serviceEndpoint(GET, "/shutter-api/externaltest/frontend/states/service-1", willRespondWith = (404, None))
      serviceEndpoint(GET, "/shutter-api/qa/frontend/states/service-1"          , willRespondWith = (200, Some(shutterApiData(ShutterType.Frontend, Environment.QA, ShutterStatusValue.Shuttered))))
      serviceEndpoint(GET, "/shutter-api/staging/frontend/states/service-1"     , willRespondWith = (404, None))
      serviceEndpoint(GET, "/shutter-api/integration/frontend/states/service-1" , willRespondWith = (404, None))
      serviceEndpoint(GET, "/shutter-api/development/frontend/states/service-1" , willRespondWith = (404, None))
      serviceEndpoint(GET, "/api/sluginfo?name=service-1"                       , willRespondWith = (200, Some(serviceDependenciesData)))

      val response = await(ws.url(s"http://localhost:$port/service/service-1").get)
      response.status shouldBe 200
      val document = Jsoup.parse(response.body)

      import scala.collection.JavaConverters._
      val qaTabElements = document.getElementById("qa-tab").children().asScala
      qaTabElements.exists(_.hasClass("shutter_badge")) && !qaTabElements.exists(_.hasClass("noshutter_badge"))

      val prodTabElements = document.getElementById("production-tab").children().asScala
      prodTabElements.exists(_.hasClass("noshutter_badge")) && !prodTabElements.exists(_.hasClass("shutter_badge"))
    }

    "link to environments" should {

      "show only show links to envs for which the service is deployed to" in {
        serviceEndpoint(GET, "/api/repositories/service-1", willRespondWith = (200, Some(serviceDetailsData)))
        serviceEndpoint(GET, "/api/jenkins-url/service-1", willRespondWith = (200, Some(serviceJenkinsData)))
        serviceEndpoint(
          GET,
          "/api/whatsrunningwhere/service-1",
          willRespondWith = (
            200,
            Some(
              Json
                .toJson(Some(ServiceDeploymentInformation(
                  "service-1",
                  Seq(DeploymentVO(EnvironmentMapping("production", Environment.Production), "skyscape-farnborough", Version("0.0.1")),
                    DeploymentVO(EnvironmentMapping("development", Environment.Development), "skyscape-farnborough", Version("0.0.1"))))))
                .toString()))
        )
        serviceEndpoint(GET, "/api/sluginfo?name=service-1",
          willRespondWith = (200, Some(serviceDependenciesData)))


        val response = await(ws.url(s"http://localhost:$port/service/service-1").get)
        response.status shouldBe 200
        response.body   should include("links on this page are automatically generated")
        response.body   should include("teamA")
        response.body   should include("teamB")
        response.body   should include("service-1")
        response.body   should include("github.com")
        response.body   should include("http://jenkins/service-1/")
        response.body   should include("Grafana")

        response.body should include("some description")

        response.body should include(createdAt.displayFormat)
        response.body should include(lastActiveAt.displayFormat)

        response.body should include("https://grafana-prod.co.uk/#/dashboard")
        response.body should include("https://grafana-dev.co.uk/#/dashboard")
        response.body should not include "https://grafana-datacentred-sal01-qa.co.uk/#/dashboard"
      }

      "show 'Not deployed' for envs in which the service is not deployed" in {
        serviceEndpoint(GET, "/api/whatsrunningwhere/service-1", willRespondWith = (404, None))
        serviceEndpoint(GET, "/api/repositories/service-1"     , willRespondWith = (200, Some(serviceDetailsData)))
        serviceEndpoint(GET, "/api/sluginfo?name=service-1"    , willRespondWith = (200, Some(serviceDependenciesData)))

        val response = await(ws.url(s"http://localhost:$port/service/service-1").get)

        // Links for environments should not be present
        response.body should not include regex("""https:\/\/(?!grafana-dev).*\/#\/dashboard""")

        countSubstring(response.body, "Not deployed") shouldBe 6

        def countSubstring(str: String, substr: String) =
          substr.r.findAllMatchIn(str).length
      }

      "omit Jenkins from telemetry links" in {
        serviceEndpoint(
          GET,
          "/api/whatsrunningwhere/service-1",
          willRespondWith = (
            200,
            Some(
              Json
                .toJson(Some(ServiceDeploymentInformation(
                  "service-1",
                  Seq(DeploymentVO(EnvironmentMapping("development", Environment.Development), "datacentred-sal01", Version("0.0.1"))))))
                .toString()))
        )
        serviceEndpoint(GET, "/api/repositories/service-1"     , willRespondWith = (200, Some(serviceDetailsData)))
        serviceEndpoint(GET, "/api/sluginfo?name=service-1"    , willRespondWith = (200, Some(serviceDependenciesData)))

        val response = await(ws.url(s"http://localhost:$port/service/service-1").get)

        response.body should not include "Jenkins"
        response.body should include("Grafana")
      }
    }

    "Render platform dependencies section" in {
      serviceEndpoint(GET, "/api/repositories/service-name",
        willRespondWith = (200, Some(serviceDetailsData)))
      serviceEndpoint(GET, "/api/indicators/service/service-name/deployments",
        willRespondWith = (500, None))
      serviceEndpoint(GET, "/api/whatsrunningwhere/service-name",
        willRespondWith = (200, Some(Json.toJson(Some(ServiceDeploymentInformation("service-name", Nil))).toString)))
      serviceEndpoint(GET, "/api/service-dependencies/dependencies/service-name",
        willRespondWith = (200, None))
      serviceEndpoint(GET, "/api/sluginfo?name=service-name",
        willRespondWith = (200, Some(serviceDependenciesData)))

      val response = await(ws.url(s"http://localhost:$port/service/service-name").get)

      val document = Jsoup.parse(response.body)

      document.select("#platform-dependencies-latest").size() should be > 0
    }
  }
}
