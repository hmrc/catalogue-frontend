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

import java.time.{LocalDateTime, ZoneOffset}

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest._
import org.scalatestplus.play.OneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeHeaders
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class ServiceDeploymentsConnectorSpec extends UnitSpec with BeforeAndAfter with OneServerPerSuite with WireMockEndpoints with EitherValues {

  implicit override lazy val app = new GuiceApplicationBuilder().configure (
    Map(
    "microservice.services.service-deployments.port" -> endpointPort,
    "microservice.services.service-deployments.host" -> host,
     "play.http.requestHandler" -> "play.api.http.DefaultHttpRequestHandler"
  )).build()


  "getDeployments" should {

    "return all deployments if service name is none" in {
      serviceEndpoint(GET, "/api/deployments", willRespondWith = (200, Some(
        """
          |[
          |	{
          |		"name": "serviceA",
          |		"version": "8.96.0",
          |		"creationDate": 1452701233,
          |		"productionDate": 1453731429,
          |		"interval": 7,
          |		"leadTime": 12,
          |  "deployers":[{"name":"abcd.xyz", "deploymentDate": 1452701233}]
          |	},
          |	{
          |		"name": "serviceB",
          |		"version": "2.38.0",
          |		"productionDate": 1453713911,
          |		"interval": 5,
          |  "deployers":[]
          |	}]
        """.stripMargin
      )))

      val response = await(ServiceDeploymentsConnector.getDeployments()(HeaderCarrier.fromHeadersAndSession(FakeHeaders())))

      response.size shouldBe 2
      response(0) shouldBe Release("serviceA", productionDate = toLocalDateTime(1453731429), creationDate = Some(toLocalDateTime(1452701233)), interval = Some(7), leadTime = Some(12), version = "8.96.0" ,deployers=Seq(Deployer("abcd.xyz",toLocalDateTime(1452701233))))
      response(1) shouldBe Release("serviceB", productionDate = toLocalDateTime(1453713911), creationDate = None, interval = Some(5), leadTime = None, version = "2.38.0")
    }

    "return all deployments for a  service if name is given" in {
      val serviceName = "serviceNameA"
      serviceEndpoint(GET, s"/api/deployments/$serviceName", willRespondWith = (200, Some(
        """
          |[
          |	{
          |		"name": "serviceA",
          |		"version": "8.96.0",
          |		"creationDate": 1452701233,
          |		"productionDate": 1453731429,
          |		"interval": 7,
          |		"leadTime": 12,
          |  "deployers":[]
          |	},
          |	{
          |		"name": "serviceA",
          |		"version": "2.38.0",
          |		"productionDate": 1453713911,
          |		"interval": 5,
          |  "deployers":[]
          |	}]
        """.stripMargin
      )))

      val response = await(ServiceDeploymentsConnector.getDeployments(Some("serviceNameA"))(HeaderCarrier.fromHeadersAndSession(FakeHeaders())))

      response.size shouldBe 2
      response(0) shouldBe Release("serviceA", productionDate = toLocalDateTime(1453731429), creationDate = Some(toLocalDateTime(1452701233)), interval = Some(7), leadTime = Some(12), version = "8.96.0")
      response(1) shouldBe Release("serviceA", productionDate = toLocalDateTime(1453713911), creationDate = None, interval = Some(5), leadTime = None, version = "2.38.0")
    }

    "return deployments for all services mentioned in the body" in {
      val serviceNames = Seq("serviceNameA", "serviceNameB")

      serviceEndpoint(POST, s"/api/deployments", willRespondWith = (200, Some(
        """
          |[
          |	{
          |		"name": "serviceNameA",
          |		"version": "8.96.0",
          |		"creationDate": 1452701233,
          |		"productionDate": 1453731429,
          |		"interval": 7,
          |		"leadTime": 12,
          |  "deployers":[]
          |	},
          |	{
          |		"name": "serviceNameB",
          |		"version": "2.38.0",
          |		"productionDate": 1453713911,
          |		"interval": 5,
          |  "deployers":[]
          |	}]
        """.stripMargin
      )), givenJsonBody = Some("[\"serviceNameA\",\"serviceNameB\"]"))

      val response = await(ServiceDeploymentsConnector.getDeployments(serviceNames)(HeaderCarrier.fromHeadersAndSession(FakeHeaders())))

      response.size shouldBe 2
      response(0).name shouldBe "serviceNameA"
      response(1).name shouldBe "serviceNameB"
    }

    def toLocalDateTime(millis: Long): LocalDateTime = LocalDateTime.ofEpochSecond(millis, 0, ZoneOffset.UTC)

  }


  "getWhatIsRunningWhere" should {
    "return all whats running where for the given application name" in {
      val serviceName = "appNameA"
      serviceEndpoint(GET, s"/api/whatsrunningwhere/$serviceName", willRespondWith = (200, Some(
        s"""
           |  {
           |    "serviceName": "$serviceName",
           |    "deployments": [
           |      { "environmentMappings": {"name": "qa", "releasesAppId": "qa"}, "datacentre": "datacentred-sal01", "version": "0.0.1" },
           |      { "environmentMappings": {"name": "staging", "releasesAppId": "staging"}, "datacentre": "skyscape-farnborough", "version": "0.0.1" },
           |      { "environmentMappings": {"name": "staging", "releasesAppId": "staging"}, "datacentre": "datacentred-sal01", "version": "0.0.2" },
           |      { "environmentMappings": {"name": "production", "releasesAppId": "production"}, "datacentre": "skyscape-farnborough", "version": "0.0.1" },
           |      { "environmentMappings": {"name": "production", "releasesAppId": "production"}, "datacentre": "datacentred-sal01", "version": "0.0.2" },
           |      { "environmentMappings": {"name": "external test", "releasesAppId": "externaltest"}, "datacentre": "datacentred-sal01", "version": "0.0.1" }
           |    ]
           |  }
           |
        """.stripMargin
      )))

      val response = await(ServiceDeploymentsConnector.getWhatIsRunningWhere(serviceName)(HeaderCarrier.fromHeadersAndSession(FakeHeaders())))


      response.right.get.deployments shouldEqual Seq(
        DeploymentVO(EnvironmentMapping("qa", "qa"), "datacentred-sal01", "0.0.1"),
        DeploymentVO(EnvironmentMapping("staging", "staging"), "skyscape-farnborough", "0.0.1"),
        DeploymentVO(EnvironmentMapping("staging", "staging"), "datacentred-sal01", "0.0.2"),
        DeploymentVO(EnvironmentMapping("production", "production"), "skyscape-farnborough", "0.0.1"),
        DeploymentVO(EnvironmentMapping("production", "production"), "datacentred-sal01", "0.0.2"),
        DeploymentVO(EnvironmentMapping("external test", "externaltest"), "datacentred-sal01", "0.0.1"))
    }

    "return an empty list of environments in WhatsRunningWhere when the service is not deployed to any environments " in {
      val serviceName = "appNameA"
      serviceEndpoint(GET, s"/api/whatsrunningwhere/$serviceName", willRespondWith = (404, None))

      val response = await(ServiceDeploymentsConnector.getWhatIsRunningWhere(serviceName)(HeaderCarrier.fromHeadersAndSession(FakeHeaders())))

      response.right.get.deployments shouldBe Seq()
      response.right.get.serviceName shouldBe serviceName


    }

  }

}
