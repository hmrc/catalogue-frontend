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

package uk.gov.hmrc.cataloguefrontend.connector

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterGroup, ShutterGroupsConnector}

class ShutterGroupsConnectorSpec
  extends AnyWordSpec
     with Matchers
     with BeforeAndAfterEach
     with GuiceOneAppPerSuite
     with WireMockSupport
     with ScalaFutures
     with IntegrationPatience {

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(
        Map(
          "github.open.api.rawurl" -> wireMockUrl,
          "github.open.api.key"    -> "t",
          "metrics.jvm"            -> false
        ))
      .build()

  private lazy val shutterGroupsConnnector = app.injector.instanceOf[ShutterGroupsConnector]

  "shutterGroups" should {
    "return all shutter groups if the file is valid" in {
      stubFor(
        get(urlEqualTo("/hmrc/outage-pages/HEAD/conf/shutter-groups.json"))
          .willReturn(
            aResponse()
            .withBody(
               """{
                 "FE-GROUP": [
                   "fe1",
                   "fe2"
                 ],
                 "API-GROUP": [
                   "api1",
                   "api2"
                 ]
               }"""
          ))
      )

      val response = shutterGroupsConnnector.shutterGroups.futureValue

      response should contain theSameElementsAs List(
        ShutterGroup("FE-GROUP", List("fe1", "fe2")),
        ShutterGroup("API-GROUP", List("api1", "api2"))
      )

      verify(
        getRequestedFor(urlEqualTo("/hmrc/outage-pages/HEAD/conf/shutter-groups.json"))
          .withHeader("Authorization", equalTo("token t"))
      )
    }

    "return an empty list of shutter groups if there is a problem parsing the file (invalid json)" in {
      stubFor(
        get(urlEqualTo("/hmrc/outage-pages/HEAD/conf/shutter-groups.json"))
          .willReturn(
            aResponse()
            .withBody(
              """{
                "FE-GROUP": [
                  "fe1"
                  "fe2"
                ]
              }"""
            )
          )
      )

      val response = shutterGroupsConnnector.shutterGroups.futureValue

      response shouldBe List.empty
    }

    "return an empty list of shutter groups if the file is not found" in {
      stubFor(
        get(urlEqualTo("/hmrc/outage-pages/HEAD/conf/shutter-groups.json"))
          .willReturn(aResponse().withStatus(404))
      )

      val response = shutterGroupsConnnector.shutterGroups.futureValue

      response shouldBe List.empty
    }
  }
}
