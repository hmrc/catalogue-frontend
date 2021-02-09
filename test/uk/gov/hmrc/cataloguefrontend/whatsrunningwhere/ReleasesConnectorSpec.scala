/*
 * Copyright 2021 HM Revenue & Customs
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

import java.time.Instant

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import uk.gov.hmrc.cataloguefrontend.WireMockEndpoints
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.play.http.HeaderCarrierConverter

class ReleasesConnectorSpec extends UnitSpec with GuiceOneServerPerSuite with WireMockEndpoints with EitherValues {

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(Map(
        "microservice.services.releases-api.port" -> endpointPort,
        "microservice.services.releases-api.host" -> host,
        "play.http.requestHandler"                -> "play.api.http.DefaultHttpRequestHandler",
        "metrics.jvm"                             -> false
      ))
      .build()

  private lazy val releasesConnector = app.injector.instanceOf[ReleasesConnector]

  "ecsWhatsRunningWhere" should {
    "return all releases if profile not supplied" in {
      serviceEndpoint(
        GET,
        "/releases-api/whats-running-where",
        queryParameters = Seq("platform" -> Platform.ECS.asString),
        willRespondWith = (
          200,
          Some("""[
                 |  {
                 |    "applicationName": "api-definition",
                 |    "versions": [
                 |      {
                 |        "environment": "integration",
                 |        "platform": "ecs",
                 |        "versionNumber": "1.57.0",
                 |        "lastSeen": "2019-05-29T14:09:48Z"
                 |      }
                 |    ]
                 |  },
                 |  {
                 |    "applicationName": "api-documentation",
                 |    "versions": [
                 |      {
                 |        "environment": "integration",
                 |        "platform": "ecs",
                 |        "versionNumber": "0.44.0",
                 |        "lastSeen": "2019-05-29T14:09:46Z"
                 |      }
                 |    ]
                 |  }
                 |]""".stripMargin))
      )

      val response =
        releasesConnector.releases(profile = None, Platform.ECS)(HeaderCarrierConverter.fromRequest(FakeRequest())).futureValue

      response should contain theSameElementsAs Seq(
        WhatsRunningWhere(
          ServiceName("api-definition"),
          List(
            WhatsRunningWhereVersion(Environment.Integration, Platform.ECS, VersionNumber("1.57.0"), lastSeen = TimeSeen(Instant.parse("2019-05-29T14:09:48Z")))
          )
        ),
        WhatsRunningWhere(
          ServiceName("api-documentation"),
          List(
            WhatsRunningWhereVersion(Environment.Integration, Platform.ECS, VersionNumber("0.44.0"), lastSeen = TimeSeen(Instant.parse("2019-05-29T14:09:46Z")))
          )
        )
      )
    }

    "return all releases for given profile" in {
      val profileType = ProfileType.ServiceManager
      val profileName = ProfileName("profile1")

      serviceEndpoint(
        GET,
        s"/releases-api/whats-running-where",
        queryParameters = Seq(
          "profileName" -> profileName.asString,
          "profileType" -> profileType.asString,
          "platform"    -> Platform.ECS.asString
        ),
        willRespondWith = (
          200,
          Some("""[
                 |  {
                 |    "applicationName": "api-definition",
                 |    "versions": [
                 |      {
                 |        "environment": "integration",
                 |        "platform": "ecs",
                 |        "versionNumber": "1.57.0",
                 |        "lastSeen": "2019-05-29T14:09:48Z"
                 |      }
                 |    ]
                 |  }
                 |]""".stripMargin))
      )

      val response =
        releasesConnector
          .releases(profile = Some(Profile(profileType, profileName)), platform = Platform.ECS)(
            HeaderCarrierConverter.fromRequest(FakeRequest())
          )
          .futureValue

      response should contain theSameElementsAs Seq(
        WhatsRunningWhere(
          ServiceName("api-definition"),
          List(
            WhatsRunningWhereVersion(Environment.Integration, Platform.ECS, VersionNumber("1.57.0"), lastSeen = TimeSeen(Instant.parse("2019-05-29T14:09:48Z")))
          )
        )
      )
    }

    "return empty upon error" in {
      serviceEndpoint(
        GET,
        s"/releases-api/whats-running-where",
        queryParameters = Seq("platform" -> Platform.ECS.asString),
        willRespondWith = (500, Some("errors!"))
      )

      val response =
        releasesConnector.releases(profile = None, Platform.ECS)(HeaderCarrierConverter.fromRequest(FakeRequest())).futureValue

      response shouldBe Seq.empty
    }
  }

  "releases" should {

    "return all releases if profile not supplied" in {
      serviceEndpoint(
        GET,
        "/releases-api/whats-running-where",
        queryParameters = Seq("platform" -> Platform.Heritage.asString),
        willRespondWith = (
          200,
          Some("""[
              |  {
              |    "applicationName": "api-definition",
              |    "versions": [
              |      {
              |        "environment": "integration",
              |        "platform": "heritage",
              |        "versionNumber": "1.57.0",
              |        "lastSeen": "2019-05-29T14:09:48Z"
              |      }
              |    ]
              |  },
              |  {
              |    "applicationName": "api-documentation",
              |    "versions": [
              |      {
              |        "environment": "integration",
              |        "platform": "heritage",
              |        "versionNumber": "0.44.0",
              |        "lastSeen": "2019-05-29T14:09:46Z"
              |      }
              |    ]
              |  }
              |]""".stripMargin))
      )

      val response =
        releasesConnector.releases(profile = None, Platform.Heritage)(HeaderCarrierConverter.fromRequest(FakeRequest())).futureValue

      response should contain theSameElementsAs Seq(
        WhatsRunningWhere(
          ServiceName("api-definition"),
          List(
            WhatsRunningWhereVersion(Environment.Integration, Platform.Heritage, VersionNumber("1.57.0"), lastSeen = TimeSeen(Instant.parse("2019-05-29T14:09:48Z")))
          )
        ),
        WhatsRunningWhere(
          ServiceName("api-documentation"),
          List(
            WhatsRunningWhereVersion(Environment.Integration, Platform.Heritage, VersionNumber("0.44.0"), lastSeen = TimeSeen(Instant.parse("2019-05-29T14:09:46Z")))
          )
        )
      )
    }

    "return all releases for given profile" in {
      val profileType = ProfileType.ServiceManager
      val profileName = ProfileName("profile1")

      serviceEndpoint(
        GET,
        s"/releases-api/whats-running-where",
        queryParameters = Seq(
          "profileName" -> profileName.asString,
          "profileType" -> profileType.asString,
          "platform"    -> Platform.Heritage.asString
          ),
        willRespondWith = (
          200,
          Some("""[
              |  {
              |    "applicationName": "api-definition",
              |    "versions": [
              |      {
              |        "environment": "integration",
              |        "platform": "heritage",
              |        "versionNumber": "1.57.0",
              |        "lastSeen": "2019-05-29T14:09:48Z"
              |      }
              |    ]
              |  }
              |]""".stripMargin))
      )

      val response =
        releasesConnector
          .releases(profile = Some(Profile(profileType, profileName)), Platform.Heritage)(
            HeaderCarrierConverter.fromRequest(FakeRequest())
          )
          .futureValue

      response should contain theSameElementsAs Seq(
        WhatsRunningWhere(
          ServiceName("api-definition"),
          List(
            WhatsRunningWhereVersion(Environment.Integration, Platform.Heritage, VersionNumber("1.57.0"), lastSeen = TimeSeen(Instant.parse("2019-05-29T14:09:48Z")))
          )
        )
      )
    }

    "return empty upon error" in {
      serviceEndpoint(GET, s"/releases-api/whats-running-where", willRespondWith = (500, Some("errors!")))

      val response =
        releasesConnector.releases(profile = None, Platform.Heritage)(HeaderCarrierConverter.fromRequest(FakeRequest())).futureValue

      response shouldBe Seq.empty
    }
  }

  "profiles" should {
    "return all profileNames" in {
      serviceEndpoint(
        GET,
        s"/releases-api/profiles",
        willRespondWith = (
          200,
          Some("""[
              |  {"type": "servicemanager",
              |   "name": "tcs_all",
              |   "apps": [
              |     "identity-verification-frontend",
              |     "identity-verification"
              |    ]
              |  },
              |  {"type": "servicemanager",
              |   "name": "tpsa",
              |   "apps": [
              |     "dtxe",
              |     "dtxe-validator"
              |    ]
              |  },
              |  {"type": "team",
              |   "name": "trusts",
              |   "apps": [
              |     "trust-registration-api",
              |     "trust-registration-stub"
              |   ]
              |  }
              |]""".stripMargin))
      )

      val response =
        releasesConnector.profiles(HeaderCarrierConverter.fromRequest(FakeRequest())).futureValue

      response should contain theSameElementsAs Seq(
        Profile(ProfileType.ServiceManager, ProfileName("tcs_all")),
        Profile(ProfileType.ServiceManager, ProfileName("tpsa")),
        Profile(ProfileType.Team          , ProfileName("trusts"))
      )
    }
  }
}
