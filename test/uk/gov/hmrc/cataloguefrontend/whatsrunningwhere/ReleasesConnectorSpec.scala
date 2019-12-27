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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import java.time.LocalDateTime

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeHeaders
import uk.gov.hmrc.cataloguefrontend.WireMockEndpoints
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.test.UnitSpec

class ReleasesConnectorSpec
    extends UnitSpec
    with GuiceOneServerPerSuite
    with WireMockEndpoints
    with EitherValues {

  override def fakeApplication: Application = new GuiceApplicationBuilder()
    .disable(classOf[com.kenshoo.play.metrics.PlayModule])
    .configure(Map(
      "microservice.services.releases-api.port" -> endpointPort,
      "microservice.services.releases-api.host" -> host,
      "play.http.requestHandler"                -> "play.api.http.DefaultHttpRequestHandler",
      "metrics.jvm"                             -> false
    ))
    .build()

  private lazy val releasesConnector = app.injector.instanceOf[ReleasesConnector]

  "releases" should {

    "return all releases if profile not supplied" in {
      serviceEndpoint(
        GET,
        "/releases-api/whats-running-where",
        willRespondWith = (
          200,
          Some(
            """[
              |  {
              |    "applicationName": "api-definition",
              |    "versions": [
              |      {
              |        "environment": "integration-AWS-London",
              |        "versionNumber": "1.57.0",
              |        "lastSeen": "2019-05-29T14:09:48"
              |      }
              |    ]
              |  },
              |  {
              |    "applicationName": "api-documentation",
              |    "versions": [
              |      {
              |        "environment": "integration-AWS-London",
              |        "versionNumber": "0.44.0",
              |        "lastSeen": "2019-05-29T14:09:46"
              |      }
              |    ]
              |  }
              |]""".stripMargin)))

      val response = await(
        releasesConnector.releases(profileName = None)(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
      )

      response should contain theSameElementsAs Seq(
        WhatsRunningWhere(
          ApplicationName("api-definition"),
          List(
            WhatsRunningWhereVersion(
              Environment("integration"),
              VersionNumber("1.57.0"),
              lastSeen = TimeSeen(LocalDateTime.parse("2019-05-29T14:09:48")))
          )
        ),
        WhatsRunningWhere(
          ApplicationName("api-documentation"),
          List(
            WhatsRunningWhereVersion(
              Environment("integration"),
              VersionNumber("0.44.0"),
              lastSeen = TimeSeen(LocalDateTime.parse("2019-05-29T14:09:46")))
          )
        )
      )
    }

    "return all releases for given profileName" in {
      val profileName = ProfileName("profile1")

      serviceEndpoint(
        GET,
        s"/releases-api/whats-running-where",
        queryParameters = Seq(("profile", profileName.asString)),
        willRespondWith = (
          200,
          Some(
            """[
              |  {
              |    "applicationName": "api-definition",
              |    "versions": [
              |      {
              |        "environment": "integration-AWS-London",
              |        "versionNumber": "1.57.0",
              |        "lastSeen": "2019-05-29T14:09:48"
              |      }
              |    ]
              |  }
              |]""".stripMargin)))

      val response = await(
        releasesConnector.releases(profileName = Some(profileName))(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
      )

      response should contain theSameElementsAs Seq(
        WhatsRunningWhere(
          ApplicationName("api-definition"),
          List(
            WhatsRunningWhereVersion(
              Environment("integration"),
              VersionNumber("1.57.0"),
              lastSeen = TimeSeen(LocalDateTime.parse("2019-05-29T14:09:48")))
          )
        )
      )
    }

    "return empty upon error" in {
      serviceEndpoint(
        GET,
        s"/releases-api/whats-running-where",
        willRespondWith = (500, Some("errors!")))

      val response = await(
        releasesConnector.releases(profileName = None)(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
      )

      response shouldBe Seq.empty
    }
  }

  "return all profileNames" in {
      serviceEndpoint(
        GET,
        s"/releases-api/profiles",
        willRespondWith = (
          200,
          Some(
            """[
              |  {"name": "tcs_all",
              |   "apps": [
              |     "identity-verification-frontend",
              |     "identity-verification"
              |    ]
              |  },
              |  {"name": "tpsa",
              |   "apps": [
              |     "dtxe",
              |     "dtxe-validator"
              |    ]
              |  },
              |  {"name": "trusts",
              |   "apps": [
              |     "trust-registration-api",
              |     "trust-registration-stub"
              |   ]
              |  }
              |]""".stripMargin)))

      val response = await(
        releasesConnector.profiles(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders()))
      )

      response should contain theSameElementsAs Seq(
        ProfileName("tcs_all"),
        ProfileName("tpsa"),
        ProfileName("trusts"))
    }

}
