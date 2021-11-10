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

package uk.gov.hmrc.cataloguefrontend.platforminitiatives

import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.any
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status}
import uk.gov.hmrc.cataloguefrontend.FakeApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.platforminitiatives.DisplayType.Chart
import uk.gov.hmrc.cataloguefrontend.platforminitiatives.html.PlatformInitiativesListPage
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.HttpClientSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PlatformInitiativesControllerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with FakeApplicationBuilder
    with OptionValues
    with ScalaFutures
    with HttpClientSupport
    with IntegrationPatience {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  "Platform Initiatives controller" should {
    "have the correct url set up for the initiatives list" in {
      uk.gov.hmrc.cataloguefrontend.platforminitiatives.routes.PlatformInitiativesController.platformInitiatives()
        .url shouldBe  "/platform-initiatives"
    }
  }

  "PlatformInitiativesController.platformInitiatives" should {
    "respond with status 200 and contain specified elements" in new Setup {
      val mockInitiatives: Seq[PlatformInitiative] = Seq(
        PlatformInitiative(
          initiativeName        = "Test initiative",
          initiativeDescription = "Test initiative description",
          currentProgress       = 10,
          targetProgress        = 100,
          completedLegend       = "Completed",
          inProgressLegend      = "Not completed"
        ),
        PlatformInitiative(
          initiativeName        = "Update Dependency",
          initiativeDescription = "Update Dependency description",
          currentProgress       = 50,
          targetProgress        = 70,
          completedLegend       = "Completed",
          inProgressLegend      = "Not completed"
        )
      )
      when(mockPIConnector.allInitiatives(any[HeaderCarrier])) thenReturn {
        Future.successful(mockInitiatives)
      }

      val result: Future[Result] = controller
        .platformInitiatives(display = Chart)
        .apply(FakeRequest())

      status(result) shouldBe 200
      println(contentAsString(result))
      contentAsString(result) should include("""<h3> Test initiative </h3>""")
      contentAsString(result) should include("""<div id="chart_div_Test initiative"></div>""")
      contentAsString(result) should include("""<p>Test initiative description</p>""")
      contentAsString(result) should include("""<h3> Update Dependency </h3>""")
      contentAsString(result) should include("""<div id="chart_div_Update Dependency"></div>""")
    }
  }

  private trait Setup {
    implicit val hc     : HeaderCarrier                 = HeaderCarrier()
    val mcc             : MessagesControllerComponents  = app.injector.instanceOf[MessagesControllerComponents]
    val mockPIView      : PlatformInitiativesListPage   = app.injector.instanceOf[PlatformInitiativesListPage]
    val mockPIConnector : PlatformInitiativesConnector  = mock[PlatformInitiativesConnector]
    val controller = new PlatformInitiativesController(mcc, mockPIConnector, mockPIView)
  }
}
