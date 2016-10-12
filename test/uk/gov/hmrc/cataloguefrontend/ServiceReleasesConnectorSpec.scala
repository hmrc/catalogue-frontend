/*
 * Copyright 2016 HM Revenue & Customs
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
import org.scalatestplus.play.OneServerPerTest
import play.api.test.{FakeApplication, FakeHeaders}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class ServiceReleasesConnectorSpec extends UnitSpec with BeforeAndAfter with OneServerPerTest with WireMockEndpoints {

  override def newAppForTest(testData: TestData): FakeApplication = new FakeApplication(
    additionalConfiguration = Map(
      "microservice.services.service-releases.port" -> endpointPort,
      "microservice.services.service-releases.host" -> host
    ))


  "getReleases" should {

    "return all releases if service name is none" in {
      serviceEndpoint(GET, "/api/releases", willRespondWith = (200, Some(
        """
          |[
          |	{
          |		"name": "serviceA",
          |		"version": "8.96.0",
          |		"creationDate": 1452701233,
          |		"productionDate": 1453731429,
          |		"interval": 7,
          |		"leadTime": 12
          |	},
          |	{
          |		"name": "serviceB",
          |		"version": "2.38.0",
          |		"productionDate": 1453713911,
          |		"interval": 5
          |	}]
        """.stripMargin
      )))

      val response = await(ServiceReleasesConnector.getReleases()(HeaderCarrier.fromHeadersAndSession(FakeHeaders())))

      response.size shouldBe 2
      response(0) shouldBe Release("serviceA", productionDate = toLocalDateTime(1453731429), creationDate = Some(toLocalDateTime(1452701233)), interval = Some(7), leadTime = Some(12), version = "8.96.0")
      response(1) shouldBe Release("serviceB", productionDate = toLocalDateTime(1453713911), creationDate = None, interval = Some(5), leadTime = None, version = "2.38.0")
    }

    "return all releases for a  service if name is given" in {
      val serviceName = "serviceNameA"
      serviceEndpoint(GET, s"/api/releases/$serviceName", willRespondWith = (200, Some(
        """
          |[
          |	{
          |		"name": "serviceA",
          |		"version": "8.96.0",
          |		"creationDate": 1452701233,
          |		"productionDate": 1453731429,
          |		"interval": 7,
          |		"leadTime": 12
          |	},
          |	{
          |		"name": "serviceA",
          |		"version": "2.38.0",
          |		"productionDate": 1453713911,
          |		"interval": 5
          |	}]
        """.stripMargin
      )))

      val response = await(ServiceReleasesConnector.getReleases(Some("serviceNameA"))(HeaderCarrier.fromHeadersAndSession(FakeHeaders())))

      response.size shouldBe 2
      response(0) shouldBe Release("serviceA", productionDate = toLocalDateTime(1453731429), creationDate = Some(toLocalDateTime(1452701233)), interval = Some(7), leadTime = Some(12), version = "8.96.0")
      response(1) shouldBe Release("serviceA", productionDate = toLocalDateTime(1453713911), creationDate = None, interval = Some(5), leadTime = None, version = "2.38.0")
    }

    "return releases for all services mentioned in the body" in {
      val serviceNames = Seq("serviceNameA", "serviceNameB")

      serviceEndpoint(POST, s"/api/releases", willRespondWith = (200, Some(
        """
          |[
          |	{
          |		"name": "serviceNameA",
          |		"version": "8.96.0",
          |		"creationDate": 1452701233,
          |		"productionDate": 1453731429,
          |		"interval": 7,
          |		"leadTime": 12
          |	},
          |	{
          |		"name": "serviceNameB",
          |		"version": "2.38.0",
          |		"productionDate": 1453713911,
          |		"interval": 5
          |	}]
        """.stripMargin
      )), givenJsonBody = Some("[\"serviceNameA\",\"serviceNameB\"]"))

      val response = await(ServiceReleasesConnector.getReleases(serviceNames)(HeaderCarrier.fromHeadersAndSession(FakeHeaders())))

      response.size shouldBe 2
      response(0).name shouldBe "serviceNameA"
      response(1).name shouldBe "serviceNameB"
    }

    def toLocalDateTime(millis: Long): LocalDateTime = LocalDateTime.ofEpochSecond(millis, 0, ZoneOffset.UTC)

  }
}
