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

package uk.gov.hmrc.cataloguefrontend.connector

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest.EitherValues
import uk.gov.hmrc.cataloguefrontend.FakeApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterGroup, ShutterGroupsConnector}
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class ShutterGroupsConnectorSpec
    extends UnitSpec
    with FakeApplicationBuilder
    with EitherValues {

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

      val response = shutterGroupsConnnector.shutterGroups.futureValue

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

      val response = shutterGroupsConnnector.shutterGroups.futureValue

      response shouldBe List.empty
    }

    "return an empty list of shutter groups if the file is not found" in {
      serviceEndpoint(
        GET,
        "/hmrc/outage-pages/master/conf/shutter-groups.json",
        willRespondWith = (
          404, None
      ))

      val response = shutterGroupsConnnector.shutterGroups.futureValue

      response shouldBe List.empty
    }
  }
}
