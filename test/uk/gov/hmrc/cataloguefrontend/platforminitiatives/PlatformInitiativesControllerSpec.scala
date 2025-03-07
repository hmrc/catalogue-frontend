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

package uk.gov.hmrc.cataloguefrontend.platforminitiatives

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status}
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, TeamName}
import uk.gov.hmrc.cataloguefrontend.platforminitiatives.DisplayType.Chart
import uk.gov.hmrc.cataloguefrontend.platforminitiatives.view.html.PlatformInitiativesListPage
import uk.gov.hmrc.cataloguefrontend.test.FakeApplicationBuilder
import uk.gov.hmrc.internalauth.client.Retrieval
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PlatformInitiativesControllerSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with FakeApplicationBuilder
     with OptionValues
     with ScalaFutures
     with IntegrationPatience {
  given HeaderCarrier = HeaderCarrier()

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
          progress              = Progress(
            current       = 10,
            target        = 100
          ),
          completedLegend       = "Completed",
          inProgressLegend      = "Not completed"
        ),
        PlatformInitiative(
          initiativeName        = "Update Dependency",
          initiativeDescription = "Update Dependency description",
          progress              = Progress(
            current       = 50,
            target        = 70
          ),
          completedLegend       = "Completed",
          inProgressLegend      = "Not completed"
        )
      )

      when(authStubBehaviour.stubAuth(None, Retrieval.EmptyRetrieval))
        .thenReturn(Future.unit)

      when(mockTRConnector.allTeams(any)(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq()))

      when(mockTRConnector.allDigitalServices()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq()))

      when(mockPIConnector.getInitiatives(any[Option[TeamName]], any[Option[DigitalService]])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(mockInitiatives))

      val result: Future[Result] = controller
        .platformInitiatives(display = Chart, team = None, digitalService = None)
        .apply(FakeRequest().withSession(SessionKeys.authToken -> "Token token"))

      status(result) shouldBe 200
      contentAsString(result) should include("""Test initiative""")
      contentAsString(result) should include("""<div id="chart_div_Test initiative"></div>""")
      contentAsString(result) should include("""<p>Test initiative description</p>""")
      contentAsString(result) should include("""Update Dependency""")
      contentAsString(result) should include("""<div id="chart_div_Update Dependency"></div>""")
    }
  }

  private trait Setup {
    given HeaderCarrier = HeaderCarrier()

    given mcc: MessagesControllerComponents =
      app.injector.instanceOf[MessagesControllerComponents]

    val mockPIView        = app.injector.instanceOf[PlatformInitiativesListPage]
    val mockTRConnector   = mock[TeamsAndRepositoriesConnector]
    val mockPIConnector   = mock[PlatformInitiativesConnector]
    val authStubBehaviour = mock[StubBehaviour]
    val authComponent     = FrontendAuthComponentsStub(authStubBehaviour)
    val controller        = PlatformInitiativesController(mcc, mockPIConnector, mockTRConnector, mockPIView, authComponent)
  }
}
