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

import org.mockito.MockitoSugar
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.mvc.Results
import uk.gov.hmrc.http.HeaderCarrier
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.test.{HttpClientSupport, WireMockSupport}

import scala.language.postfixOps

class PlatformInitiativesConnectorSpec
  extends AnyWordSpec
    with Matchers
    with Results
    with MockitoSugar
    with GuiceOneAppPerSuite
    with HttpClientSupport
    with WireMockSupport {
  implicit val hc: HeaderCarrier = HeaderCarrier()
  override lazy val resetWireMockMappings = false
  override lazy val wireMockRootDirectory = "test/resources"

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .disable(classOf[com.kenshoo.play.metrics.PlayModule])
      .configure(
        Map(
          "microservice.services.platform-initiatives.host" -> wireMockHost,
          "microservice.services.platform-initiatives.port" -> wireMockPort,
          "metrics.jvm" -> false
        )).build()

  private val connector = app.injector.instanceOf[PlatformInitiativesConnector]

  "PlatformInitiativesConnector.allInitiatives" should {
    "return correct JSON for Platform Initiatives" in {
      stubFor(
        get(urlEqualTo(s"/platform-initiatives/initiatives"))
          .willReturn(aResponse().withBodyFile("/platform-initiatives.json"))
      )
      val initiatives = connector.allInitiatives.futureValue
      initiatives.head.initiativeName mustBe "Initiative-1"
    }
  }
}