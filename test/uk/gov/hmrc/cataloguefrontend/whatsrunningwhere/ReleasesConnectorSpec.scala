/*
 * Copyright 2023 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.EitherValues
import uk.gov.hmrc.cataloguefrontend.FakeApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport

class ReleasesConnectorSpec
  extends UnitSpec
     with FakeApplicationBuilder
     with WireMockSupport
     with EitherValues {

  private lazy val releasesConnector = app.injector.instanceOf[ReleasesConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "WhatsRunningWhere" should {
    "return all releases if profile not supplied" in {
      stubFor(
        get(urlPathEqualTo("/releases-api/whats-running-where"))
          .willReturn(
            aResponse()
            .withBody(
              """[
                   {
                     "applicationName": "api-definition",
                     "versions": [
                       {
                         "environment": "integration",
                         "versionNumber": "1.57.0",
                         "lastSeen": "2019-05-29T14:09:48Z"
                       }
                     ]
                   },
                   {
                     "applicationName": "api-documentation",
                     "versions": [
                       {
                         "environment": "integration",
                         "versionNumber": "0.44.0",
                         "lastSeen": "2019-05-29T14:09:46Z"
                       }
                     ]
                   }
                 ]"""
            )
          )
      )

      val response =
        releasesConnector.releases(profile = None).futureValue

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

      stubFor(
        get(urlPathEqualTo("/releases-api/whats-running-where"))
          .willReturn(
            aResponse()
            .withBody(
              """[
                   {
                     "applicationName": "api-definition",
                     "versions": [
                       {
                         "environment": "integration",
                         "versionNumber": "1.57.0"
                       }
                     ]
                   }
                 ]"""
            )
          )
      )

      val response =
        releasesConnector
          .releases(profile = Some(Profile(profileType, profileName)))
          .futureValue

      response should contain theSameElementsAs Seq(
        WhatsRunningWhere(
          ServiceName("api-definition"),
          List(
            WhatsRunningWhereVersion(Environment.Integration, VersionNumber("1.57.0"))
          )
        )
      )

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/releases-api/whats-running-where"))
          .withQueryParam("profileName", equalTo(profileName.asString))
          .withQueryParam("profileType", equalTo(profileType.asString))
      )
    }

    "return empty upon error" in {
      stubFor(
        get(urlPathEqualTo("/releases-api/whats-running-where"))
          .willReturn(
            aResponse()
            .withStatus(500)
            .withBody("errors!")
          )
      )

      val response =
        releasesConnector.releases(profile = None).futureValue

      response shouldBe Seq.empty
    }
  }

  "profiles" should {
    "return all profileNames" in {
      stubFor(
        get(urlPathEqualTo("/releases-api/profiles"))
          .willReturn(
            aResponse()
            .withBody(
              """[
                { "type": "servicemanager",
                  "name": "tcs_all",
                  "apps": [
                    "identity-verification-frontend",
                    "identity-verification"
                   ]
                },
                { "type": "servicemanager",
                  "name": "tpsa",
                  "apps": [
                    "dtxe",
                    "dtxe-validator"
                  ]
                },
                { "type": "team",
                  "name": "trusts",
                  "apps": [
                    "trust-registration-api",
                    "trust-registration-stub"
                  ]
                }
              ]"""
            )
          )
      )

      val response =
        releasesConnector.profiles.futureValue

      response should contain theSameElementsAs Seq(
        Profile(ProfileType.ServiceManager, ProfileName("tcs_all")),
        Profile(ProfileType.ServiceManager, ProfileName("tpsa")),
        Profile(ProfileType.Team, ProfileName("trusts"))
      )
    }
  }

  "deploymentHistory" should {
    "return a paginated deployment history with total extracted from header" in {
      stubFor(
        get(urlPathEqualTo("/releases-api/deployments/production"))
          .willReturn(
            aResponse()
            .withBody(
              """[
                {
                "serviceName":"income-tax-submission-frontend",
                "environment":"production",
                "version":"0.98.0",
                "teams":[],
                "time":"2021-03-24T14:16:41Z",
                "username":"remoteRequest"
                }
              ]"""
            )
            .withHeader("X-Total-Count", "100")
          )
      )

      val response = releasesConnector
        .deploymentHistory(
          Environment.Production,
          service = Some("income-tax-submission-frontend"),
          skip    = Some(20),
          limit   = Some(1)
        )
        .futureValue
      response.total          shouldBe 100
      response.history.length shouldBe 1

      wireMockServer.verify(
        getRequestedFor(urlPathEqualTo("/releases-api/deployments/production"))
          .withQueryParam("service", equalTo("income-tax-submission-frontend"))
          .withQueryParam("skip"   , equalTo("20"))
          .withQueryParam("limit"  , equalTo("1"))
      )
    }
  }
}
