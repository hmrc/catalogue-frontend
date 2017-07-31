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

package uk.gov.hmrc.cataloguefrontend

import uk.gov.hmrc.cataloguefrontend.DateHelper._
import java.time.{LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.jsoup.Jsoup
import org.scalatestplus.play.OneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WS
import uk.gov.hmrc.cataloguefrontend.JsonData._
import uk.gov.hmrc.play.test.UnitSpec


class ServicePageSpec extends UnitSpec with OneServerPerSuite with WireMockEndpoints {

  implicit override lazy val app = new GuiceApplicationBuilder().configure(
    "microservice.services.teams-and-services.port" -> endpointPort,
    "microservice.services.teams-and-services.host" -> host,
    "microservice.services.indicators.port" -> endpointPort,
    "microservice.services.indicators.host" -> host,
    "microservice.services.service-dependencies.host" -> host,
    "microservice.services.service-dependencies.port" -> endpointPort,
    "microservice.services.service-deployments.port" -> endpointPort,
    "microservice.services.service-deployments.host" -> host,
    "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler").build()


  "A service page" should {

    "return a 404 when teams and services returns a 404" in {
      serviceEndpoint(GET, "/api/services/serv", willRespondWith = (404, None))

      val response = await(WS.url(s"http://localhost:$port/repositories/serv").get)
      response.status shouldBe 404
    }

    "return a 404 when a Library is viewed as a service" in {
      serviceEndpoint(GET, "/api/repositories/serv", willRespondWith = (200, Some(libraryDetailsData)))
      serviceEndpoint(GET, "/api/whatsrunningwhere/serv", willRespondWith = (200, Some(Json.toJson(Some(ServiceDeploymentInformation("serv", Nil))).toString())))

      val response = await(WS.url(s"http://localhost:$port/service/serv").get)
      response.status shouldBe 404
    }

    "show the teams owning the service with github, ci and environment links and info box" in {

      serviceEndpoint(GET, "/api/repositories/service-1", willRespondWith = (200, Some(serviceDetailsData)))
      serviceEndpoint(GET, "/api/indicators/service/service-1/throughput", willRespondWith = (200, Some(deploymentThroughputData)))
      serviceEndpoint(GET, "/api/whatsrunningwhere/service-1", willRespondWith = (200, Some(Json.toJson(Some(ServiceDeploymentInformation("service-1", Seq(
        DeploymentVO(EnvironmentMapping("production", "production"), "skyscape-farnborough", "0.0.1"),
        DeploymentVO(EnvironmentMapping("qa", "qa"), "skyscape-farnborough", "0.0.1"))
      ))).toString())))

      val response = await(WS.url(s"http://localhost:$port/service/service-1").get)
      response.status shouldBe 200
      response.body should include("links on this page are automatically generated")
      response.body should include("teamA")
      response.body should include("teamB")
      response.body should include("open 1")
      response.body should include("open 2")
      response.body should include("github.com")
      response.body should include("http://open1/service-1")
      response.body should include("http://open2/service-2")
      response.body should include("Jenkins")
      response.body should include("Grafana")
      response.body should include("https://deploy-qa.co.uk/job/deploy-microservice")
      response.body should include("https://deploy-prod.co.uk/job/deploy-microservice")
      response.body should include("some description")

      response.body should include(createdAt.displayFormat)
      response.body should include(lastActiveAt.displayFormat)
    }


    "link to environments" should {

      "show only show links to envs for which the service is deployed to" in {
        import ServiceDeploymentInformation._

        serviceEndpoint(GET, "/api/repositories/service-1", willRespondWith = (200, Some(serviceDetailsData)))
        serviceEndpoint(GET, "/api/indicators/service/service-1/throughput", willRespondWith = (200, Some(deploymentThroughputData)))
        serviceEndpoint(GET, "/api/whatsrunningwhere/service-1", willRespondWith =
          (200, Some(Json.toJson(Some(ServiceDeploymentInformation("service-1", Seq(DeploymentVO(EnvironmentMapping("production", "production"), "skyscape-farnborough", "0.0.1"))))).toString())))

        val response = await(WS.url(s"http://localhost:$port/service/service-1").get)
        response.status shouldBe 200
        response.body should include("links on this page are automatically generated")
        response.body should include("teamA")
        response.body should include("teamB")
        response.body should include("open 1")
        response.body should include("open 2")
        response.body should include("github.com")
        response.body should include("http://open1/service-1")
        response.body should include("http://open2/service-2")
        response.body should include("Jenkins")
        response.body should include("Grafana")

        response.body should include("some description")

        response.body should include(createdAt.displayFormat)
        response.body should include(lastActiveAt.displayFormat)

        response.body should include("https://deploy-prod.co.uk/job/deploy-microservice")
        response.body should include("https://grafana-prod.co.uk/#/dashboard")
        response.body should include("https://deploy-dev.co.uk/job/deploy-microservice")
        response.body should include("https://grafana-dev.co.uk/#/dashboard")

        response.body should not include ("https://deploy-qa.co.uk/job/deploy-microservice")
        response.body should not include ("https://grafana-datacentred-sal01-qa.co.uk/#/dashboard")
      }
      
      "show show links to devs by default" in {
        import ServiceDeploymentInformation._

        serviceEndpoint(GET, "/api/whatsrunningwhere/service-1", willRespondWith =
          (200, Some(Json.toJson(Some(ServiceDeploymentInformation("service-1", Seq(DeploymentVO(EnvironmentMapping("production", "production"), "datacentred-sal01", "0.0.1"))))).toString())))
        serviceEndpoint(GET, "/api/repositories/service-1", willRespondWith = (200, Some(serviceDetailsData)))
        serviceEndpoint(GET, "/api/indicators/service/service-1/throughput", willRespondWith = (200, Some(deploymentThroughputData)))

        val response = await(WS.url(s"http://localhost:$port/service/service-1").get)

        response.body should include("https://deploy-dev.co.uk/job/deploy-microservice")
        response.body should include("https://grafana-dev.co.uk/#/dashboard")

      }

      "show show 'Not deployed' for envs in which the service is not deployed" in {
        import ServiceDeploymentInformation._

        serviceEndpoint(GET, "/api/whatsrunningwhere/service-1", willRespondWith = (404, None))
        serviceEndpoint(GET, "/api/repositories/service-1", willRespondWith = (200, Some(serviceDetailsData)))
        serviceEndpoint(GET, "/api/indicators/service/service-1/throughput", willRespondWith = (200, Some(deploymentThroughputData)))

        val response = await(WS.url(s"http://localhost:$port/service/service-1").get)

        // Dev links should always be present
        response.body should include regex """https:\/\/(deploy-dev).*\/job\/deploy-microservice.*"""
        response.body should include regex """https:\/\/(grafana-dev).*\/#\/dashboard"""

        // Links for other environments should not be present
        response.body should not include regex ("""https:\/\/(?!deploy-dev).*\/job\/deploy-microservice.*""")
        response.body should not include regex ("""https:\/\/(?!grafana-dev).*\/#\/dashboard""")

        countSubstring(response.body , "Not deployed") shouldBe 2

        def countSubstring( str:String, substr:String ) = substr.r.findAllMatchIn(str).length

      }

    }
    
    "Render the frequent production indicators graph with throughput and stability" in {
      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith = (200, Some(serviceDetailsData)))
      serviceEndpoint(GET, "/api/indicators/service/service-name/deployments", willRespondWith = (200, Some(deploymentThroughputData)))
      serviceEndpoint(GET, "/api/whatsrunningwhere/service-name", willRespondWith = (200, Some(Json.toJson(Some(ServiceDeploymentInformation("serv", Nil))).toString())))

      val response = await(WS.url(s"http://localhost:$port/service/service-name").get)
      response.status shouldBe 200
      response.body should include(s"""data.addColumn('string', 'Period');""")
      response.body should include(s"""data.addColumn('number', 'Lead Time');""")
      response.body should include(s"""data.addColumn('number', 'Interval');""")

      response.body should include(s"""data.addColumn({'type': 'string', 'role': 'tooltip', 'p': {'html': true}});""")
      response.body should include(s"""data.addColumn('number', 'Interval');""")
      response.body should include(s"""data.addColumn({'type': 'string', 'role': 'tooltip', 'p': {'html': true}});""")

      response.body should include(s"""chart.draw(data, options);""")

      response.body should include(s"""data.addColumn('string', 'Period');""")
      response.body should include(s"""data.addColumn('number', "Hotfix Rate");""")
      response.body should include(s"""data.addColumn({'type': 'string', 'role': 'tooltip', 'p': {'html': true}});""")
    }


    "Render a message if the indicators service returns 404" in {
      val today = LocalDateTime.now
      val dayInterval = createdAt.until(today, ChronoUnit.DAYS) + 1

      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith = (200, Some(serviceDetailsData)))
      serviceEndpoint(GET, "/api/whatsrunningwhere/service-name", willRespondWith = (200, Some(Json.toJson(Some(ServiceDeploymentInformation("xyz", Nil))).toString())))
      serviceEndpoint(GET, "/api/indicators/service/service-name/deployments", willRespondWith = (404, None))


      val response = await(WS.url(s"http://localhost:$port/service/service-name").get)
      response.status shouldBe 200
      response.body should include(s"""No production deployments for $dayInterval days""")
      response.body should include(ViewMessages.noIndicatorsData)

      response.body shouldNot include(s"""chart.draw(data, options);""")
    }

    "Render a message if the indicators service encounters an error" in {
      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith = (200, Some(serviceDetailsData)))
      serviceEndpoint(GET, "/api/indicators/service/service-name/deployments", willRespondWith = (500, None))
      serviceEndpoint(GET, "/api/whatsrunningwhere/service-name", willRespondWith = (200, Some(Json.toJson(Some(ServiceDeploymentInformation("xyz", Nil))).toString())))

      val response = await(WS.url(s"http://localhost:$port/service/service-name").get)
      response.status shouldBe 200
      response.body should include(s"""The catalogue encountered an error""")
      response.body should include(ViewMessages.indicatorsServiceError)

      response.body shouldNot include(s"""chart.draw(data, options);""")
    }

    "Render platform dependencies section" in  {

      serviceEndpoint(GET, "/api/repositories/service-name", willRespondWith = (200, Some(serviceDetailsData)))
      serviceEndpoint(GET, "/api/indicators/service/service-name/deployments", willRespondWith = (500, None))
      serviceEndpoint(GET, "/api/whatsrunningwhere/service-name", willRespondWith = (200, Some(Json.toJson(Some(ServiceDeploymentInformation("xyz", Nil))).toString())))

      serviceEndpoint(GET, "/api/service-dependencies/dependencies/service-name", willRespondWith = (200, None))

      val response = await(WS.url(s"http://localhost:$port/service/service-name").get)

      val document = Jsoup.parse(response.body)

      document.select("#platform-dependencies").size() should be > 0

    }

  }


}
