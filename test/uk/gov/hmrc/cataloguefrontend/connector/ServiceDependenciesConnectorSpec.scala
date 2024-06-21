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

package uk.gov.hmrc.cataloguefrontend.connector

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.cataloguefrontend.connector.model._
import uk.gov.hmrc.cataloguefrontend.model.{Environment, SlugInfoFlag, Version}
import uk.gov.hmrc.cataloguefrontend.service.ServiceDependencies
import uk.gov.hmrc.http.HeaderCarrier

class ServiceDependenciesConnectorSpec
  extends AnyWordSpec
     with Matchers
     with GuiceOneAppPerSuite
     with BeforeAndAfterEach
     with WireMockSupport
     with OptionValues
     with ScalaFutures
     with IntegrationPatience {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        Map(
          "microservice.services.service-dependencies.port" -> wireMockPort,
          "microservice.services.service-dependencies.host" -> wireMockHost,
        )
      )
      .build()

  private lazy val serviceDependenciesConnector = app.injector.instanceOf[ServiceDependenciesConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "getJdkVersions" should {
    "returns JDK versions with vendor" in {
      stubFor(
        get(urlEqualTo(s"/api/jdkVersions?flag=${SlugInfoFlag.ForEnvironment(Environment.Production).asString}"))
          .willReturn(
            aResponse()
            .withBody(
              """[
                  {"name":"something-api",  "version":"1.8.0_181", "vendor": "Oracle", "kind": "JDK"}
                 ,{"name":"service-backend","version":"1.8.0_191", "vendor": "OpenJDK", "kind": "JRE"}
                 ]"""
            )
          )
      )

      val response = serviceDependenciesConnector.getJdkVersions(teamName = None, flag = SlugInfoFlag.ForEnvironment(Environment.Production)).futureValue

      response.head.name    shouldBe "something-api"
      response.head.version shouldBe Version("1.8.0_181")
      response.head.vendor  shouldBe Vendor.Oracle
      response.head.kind    shouldBe Kind.JDK

      response(1).name    shouldBe "service-backend"
      response(1).version shouldBe Version("1.8.0_191")
      response(1).vendor  shouldBe Vendor.OpenJDK
      response(1).kind    shouldBe Kind.JRE
    }
  }

  "JSON Reader" should {
    "read json with java section" in {
      implicit val sdr = ServiceDependencies.reads
      val json = """{
        "uri" : "https://artefactory/slugs/mobile-stub/mobile-stub_0.12.0_0.5.2.tgz",
        "name" : "mobile-auth-stub",
        "version" : "0.12.0",
        "semanticVersion" : {
            "major" : 0,
            "minor" : 12,
            "patch" : 0
        },
        "versionLong" : 12000,
        "runnerVersion" : "0.5.2",
        "classpath" : "",
        "jdkVersion" : "1.8.0_191",
        "dependencies" : [
            {
                "path" : "./mobile-auth-stub-0.12.0/lib/org.slf4j.slf4j-api-1.7.25.jar",
                "version" : "1.7.25",
                "group" : "org.slf4j",
                "artifact" : "slf4j-api",
                "meta" : "fromPom"
            }
        ],
        "latest" : false,
        "qa" : false,
        "production" : false,
        "development" : false,
        "external test" : false,
        "staging" : false,
        "java" : {
            "version" : "1.8.0_191",
            "kind" : "JDK",
            "vendor" : "OpenJDK"
        },
        "dependencyDot": {
            "compile": "",
            "build": "",
            "test": ""
        }
      }"""
      val res = Json.fromJson[ServiceDependencies](Json.parse(json)).get

      res.java.version shouldBe "1.8.0_191"
      res.java.vendor  shouldBe "OpenJDK"
      res.java.kind    shouldBe "JDK"
    }
  }
}
