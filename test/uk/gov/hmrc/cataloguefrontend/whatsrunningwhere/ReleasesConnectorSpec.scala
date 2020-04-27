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
import uk.gov.hmrc.cataloguefrontend.model.Environment.Integration
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.play.HeaderCarrierConverter

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

  "releases" should {

    "return all releases if profile not supplied" in {
      serviceEndpoint(
        GET,
        "/releases-api/whats-running-where",
        willRespondWith = (
          200,
          Some("""[
              |  {
              |    "applicationName": "api-definition",
              |    "versions": [
              |      {
              |        "environment": "integration",
              |        "versionNumber": "1.57.0",
              |        "lastSeen": "2019-05-29T14:09:48"
              |      }
              |    ]
              |  },
              |  {
              |    "applicationName": "api-documentation",
              |    "versions": [
              |      {
              |        "environment": "integration",
              |        "versionNumber": "0.44.0",
              |        "lastSeen": "2019-05-29T14:09:46"
              |      }
              |    ]
              |  }
              |]""".stripMargin))
      )

      val response =
        releasesConnector.releases(profile = None)(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())).futureValue

      response should contain theSameElementsAs Seq(
        WhatsRunningWhere(
          ServiceName("api-definition"),
          List(
            WhatsRunningWhereVersion(
              Integration,
              VersionNumber("1.57.0"),
              lastSeen = TimeSeen(LocalDateTime.parse("2019-05-29T14:09:48")))
          )
        ),
        WhatsRunningWhere(
          ServiceName("api-documentation"),
          List(
            WhatsRunningWhereVersion(
              Integration,
              VersionNumber("0.44.0"),
              lastSeen = TimeSeen(LocalDateTime.parse("2019-05-29T14:09:46")))
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
        queryParameters = Seq("profileName" -> profileName.asString, "profileType" -> profileType.asString),
        willRespondWith = (
          200,
          Some("""[
              |  {
              |    "applicationName": "api-definition",
              |    "versions": [
              |      {
              |        "environment": "integration",
              |        "versionNumber": "1.57.0",
              |        "lastSeen": "2019-05-29T14:09:48"
              |      }
              |    ]
              |  }
              |]""".stripMargin))
      )

      val response =
        releasesConnector.releases(profile = Some(Profile(profileType, profileName)))(
            HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())
          ).futureValue

      response should contain theSameElementsAs Seq(
        WhatsRunningWhere(
          ServiceName("api-definition"),
          List(
            WhatsRunningWhereVersion(
              Integration,
              VersionNumber("1.57.0"),
              lastSeen = TimeSeen(LocalDateTime.parse("2019-05-29T14:09:48")))
          )
        )
      )
    }

    "return empty upon error" in {
      serviceEndpoint(GET, s"/releases-api/whats-running-where", willRespondWith = (500, Some("errors!")))

      val response =
        releasesConnector.releases(profile = None)(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())).futureValue

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
        releasesConnector.profiles(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())).futureValue

      response should contain theSameElementsAs Seq(
        Profile(ProfileType.ServiceManager, ProfileName("tcs_all")),
        Profile(ProfileType.ServiceManager, ProfileName("tpsa")),
        Profile(ProfileType.Team, ProfileName("trusts"))
      )
    }
  }

  "ecsReleases" should {

    "return all releases if profile not supplied" in {
      serviceEndpoint(
        GET,
        "/releases-api/ecs-deployment-events",
        willRespondWith = (
          200,
          Some("""[
              |  {
              |    "serviceName": "api-definition",
              |    "environment": "integration",
              |    "deploymentEvents": [],
              |    "lastCompleted": {
              |      "deploymentId": "some-stack-id",
              |      "status": "CREATE_COMPLETE",
              |      "version": "1.57.0",
              |      "time": "2019-05-29T14:09:48"
              |    }
              |  },
              |  {
              |    "serviceName": "api-documentation",
              |    "environment": "integration",
              |    "deploymentEvents": [],
              |    "lastCompleted": {
              |      "deploymentId": "some-other-stack-id",
              |      "status": "UPDATE_COMPLETE",
              |      "version": "0.44.0",
              |      "time": "2019-05-29T14:09:46"
              |    }
              |  }
              |]""".stripMargin))
      )

      val response =
        releasesConnector.ecsReleases(profile = None)(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())).futureValue

      response should contain theSameElementsAs Seq(
        ServiceDeployment(
          ServiceName("api-definition"),
          Integration,
          Seq.empty,
          Some(
            DeploymentEvent(
              "some-stack-id",
              DeploymentStatus("CREATE_COMPLETE"),
              VersionNumber("1.57.0"),
              TimeSeen(LocalDateTime.of(2019, 5, 29, 14, 9, 48))
            ))
        ),
        ServiceDeployment(
          ServiceName("api-documentation"),
          Integration,
          Seq.empty,
          Some(
            DeploymentEvent(
              "some-other-stack-id",
              DeploymentStatus("UPDATE_COMPLETE"),
              VersionNumber("0.44.0"),
              TimeSeen(LocalDateTime.of(2019, 5, 29, 14, 9, 46))
            ))
        ),
      )
    }

    "return all releases for given profile" in {
      val profileType = ProfileType.ServiceManager
      val profileName = ProfileName("profile1")

      serviceEndpoint(
        GET,
        s"/releases-api/ecs-deployment-events",
        queryParameters = Seq("profileName" -> profileName.asString, "profileType" -> profileType.asString),
        willRespondWith = (
          200,
          Some("""[
              |  {
              |    "serviceName": "api-definition",
              |    "environment": "integration",
              |    "deploymentEvents": [],
              |    "lastCompleted": {
              |      "deploymentId": "some-stack-id",
              |      "status": "CREATE_COMPLETE",
              |      "version": "1.57.0",
              |      "time": "2019-05-29T14:09:48"
              |    }
              |  }
              |]""".stripMargin))
      )

      val response =
        releasesConnector.ecsReleases(profile = Some(Profile(profileType, profileName)))(
          HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())
        ).futureValue

      response should contain theSameElementsAs Seq(
        ServiceDeployment(
          ServiceName("api-definition"),
          Integration,
          Seq.empty,
          Some(
            DeploymentEvent(
              "some-stack-id",
              DeploymentStatus("CREATE_COMPLETE"),
              VersionNumber("1.57.0"),
              TimeSeen(LocalDateTime.of(2019, 5, 29, 14, 9, 48))
            ))
        )
      )
    }

    "return empty upon error" in {
      serviceEndpoint(GET, s"/releases-api/ecs-deployment-events", willRespondWith = (500, Some("errors!")))

      val response =
        releasesConnector.ecsReleases(profile = None)(HeaderCarrierConverter.fromHeadersAndSession(FakeHeaders())).futureValue

      response shouldBe Seq.empty
    }
  }
}
