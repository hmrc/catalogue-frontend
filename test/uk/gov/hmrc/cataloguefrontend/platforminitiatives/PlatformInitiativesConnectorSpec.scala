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

package uk.gov.hmrc.cataloguefrontend.platforminitiatives

import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport

class PlatformInitiativesConnectorSpec
  extends AnyWordSpec
    with Matchers
    with Results
    with MockitoSugar
    with GuiceOneAppPerSuite
    with WireMockSupport {
  implicit val hc: HeaderCarrier = HeaderCarrier()
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

  "PlatformInitiativesConnector.getInitiatives" should {
    "return correct JSON for all Platform Initiatives" in {
      stubFor(
        get(urlEqualTo("/platform-initiatives/initiatives"))
          .willReturn(aResponse().withBodyFile("platform-initiatives.json"))
      )
      stubFor(
        get(urlEqualTo("/platform-initiatives/teams/team/initiatives"))
          .willReturn(aResponse().withBodyFile("platform-initiatives.json"))
      )
      val initiatives     = connector.getInitiatives(None).futureValue
      val teamInitiatives = connector.getInitiatives(Some("team")).futureValue
      val result = Seq(
        PlatformInitiative(
          initiativeName        = "Initiative-1",
          initiativeDescription = "Test description",
          currentProgress       = 10,
          targetProgress        = 100,
          completedLegend       = "Updated",
          inProgressLegend      = "Not Updated"),
        PlatformInitiative(
          initiativeName        = "Initiative-2",
          initiativeDescription = "Test description",
          currentProgress       = 33,
          targetProgress        = 40,
          completedLegend       = "Completed",
          inProgressLegend      = "Not Completed"))
      initiatives mustBe result
      teamInitiatives mustBe result
    }
  }
}
