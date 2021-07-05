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

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest._
import play.api.test.FakeRequest
import uk.gov.hmrc.cataloguefrontend.FakeApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.play.http.HeaderCarrierConverter

class ReleasesConnectorSpec extends UnitSpec with FakeApplicationBuilder with EitherValues {

  private lazy val releasesConnector = app.injector.instanceOf[ReleasesConnector]

  "WhatsRunningWhere" should {
    "return all releases if profile not supplied" in {
      serviceEndpoint(
        GET,
        "/releases-api/whats-running-where",
        queryParameters = Seq.empty,
        willRespondWith = (
          200,
          Some("""[
                 |  {
                 |    "applicationName": "api-definition",
                 |    "versions": [
                 |      {
                 |        "environment": "integration",
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
                 |        "versionNumber": "0.44.0",
                 |        "lastSeen": "2019-05-29T14:09:46Z"
                 |      }
                 |    ]
                 |  }
                 |]""".stripMargin))
      )

      val response =
        releasesConnector.releases(profile = None)(HeaderCarrierConverter.fromRequest(FakeRequest())).futureValue

      response should contain theSameElementsAs Seq(
        WhatsRunningWhere(
          ServiceName("api-definition"),
          List(
            WhatsRunningWhereVersion(Environment.Integration, VersionNumber("1.57.0"))
          )
        ),
        WhatsRunningWhere(
          ServiceName("api-documentation"),
          List(
            WhatsRunningWhereVersion(Environment.Integration, VersionNumber("0.44.0"))
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
          "profileType" -> profileType.asString
        ),
        willRespondWith = (
          200,
          Some("""[
                   {
                     "applicationName": "api-definition",
                     "versions": [
                       {
                         "environment": "integration",
                         "versionNumber": "1.57.0"
                       }
                     ]
                   }
                 ]"""))
      )

      val response =
        releasesConnector
          .releases(profile = Some(Profile(profileType, profileName)))(
            HeaderCarrierConverter.fromRequest(FakeRequest())
          )
          .futureValue

      response should contain theSameElementsAs Seq(
        WhatsRunningWhere(
          ServiceName("api-definition"),
          List(
            WhatsRunningWhereVersion(Environment.Integration, VersionNumber("1.57.0"))
          )
        )
      )
    }

    "return empty upon error" in {
      serviceEndpoint(
        GET,
        s"/releases-api/whats-running-where",
        queryParameters = Seq.empty,
        willRespondWith = (500, Some("errors!"))
      )

      val response =
        releasesConnector.releases(profile = None)(HeaderCarrierConverter.fromRequest(FakeRequest())).futureValue

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
        Profile(ProfileType.Team, ProfileName("trusts"))
      )
    }
  }

  "deploymentHistory" should {
    "return a paginated deployment history with total extracted from header" in {

      serviceEndpoint(
        GET,
        s"/releases-api/deployments/production",
        queryParameters = Seq(
          "service" -> "income-tax-submission-frontend",
          "skip"    -> "20",
          "limit"   -> "1"
        ),
        extraHeaders = Map(("X-Total-Count" -> "100")),
        willRespondWith = (
          200,
          Some("""[
              |{
              | "serviceName":"income-tax-submission-frontend",
              | "environment":"production",
              | "version":"0.98.0",
              | "teams":[],
              | "time":"2021-03-24T14:16:41Z",
              | "username":"remoteRequest"
              |}
              |]""".stripMargin))
      )

      val response = releasesConnector
        .deploymentHistory(Environment.Production, service = Some("income-tax-submission-frontend"), skip = Some(20), limit = Some(1))(
          HeaderCarrierConverter.fromRequest(FakeRequest()))
        .futureValue
      response.total          shouldBe 100
      response.history.length shouldBe 1
    }
  }
}
