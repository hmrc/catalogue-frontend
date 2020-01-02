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

package uk.gov.hmrc.cataloguefrontend.connector

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.WireMockEndpoints
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterGroup, ShutterGroupsConnector}
import uk.gov.hmrc.play.test.UnitSpec

class ShutterGroupsConnectorSpec
    extends UnitSpec
    with GuiceOneServerPerSuite
    with WireMockEndpoints
    with EitherValues {

  override def fakeApplication: Application = new GuiceApplicationBuilder()
    .disable(classOf[com.kenshoo.play.metrics.PlayModule])
    .configure(Map(
      "github.open.api.rawurl"     -> endpointMockUrl,
      "github.open.api.token"      -> "",
      "play.http.requestHandler"   -> "play.api.http.DefaultHttpRequestHandler",
      "metrics.jvm"                -> false
    ))
    .build()

  private lazy val shutterGroupsConnnector = app.injector.instanceOf[ShutterGroupsConnector]

  "shutterGroups" should {

    "return all shutter groups if the file is valid" in {
      serviceEndpoint(
        GET,
        "/hmrc/outage-pages/master/conf/shutter-groups.json",
        willRespondWith = (
          200,
          Some(
           """{
             |  "FE-GROUP": [
             |    "fe1",
             |    "fe2"
             |  ],
             |  "API-GROUP": [
             |    "api1",
             |    "api2"
             |  ]
             |}""".stripMargin
          ))
      )

      val response = await(
        shutterGroupsConnnector.shutterGroups
      )

      response should contain theSameElementsAs List(
        ShutterGroup("FE-GROUP", List("fe1", "fe2")),
        ShutterGroup("API-GROUP", List("api1", "api2"))
      )
    }

    "return an empty list of shutter groups if there is a problem parsing the file (invalid json)" in {
      serviceEndpoint(
        GET,
        "/hmrc/outage-pages/master/conf/shutter-groups.json",
        willRespondWith = (
          200,
          Some(
            """{
              |  "FE-GROUP": [
              |    "fe1"
              |    "fe2"
              |  ]
              |}""".stripMargin
          ))
      )

      val response = await(
        shutterGroupsConnnector.shutterGroups
      )

      response shouldBe List.empty
    }

    "return an empty list of shutter groups if the file is not found" in {
      serviceEndpoint(
        GET,
        "/hmrc/outage-pages/master/conf/shutter-groups.json",
        willRespondWith = (
          404, None
      ))

      val response = await(
        shutterGroupsConnnector.shutterGroups
      )

      response shouldBe List.empty
    }

  }
}
