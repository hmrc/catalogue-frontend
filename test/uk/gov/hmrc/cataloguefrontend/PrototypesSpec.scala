/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.actions.{UmpAuthenticated, VerifySignInStatus}
import uk.gov.hmrc.cataloguefrontend.connector.{IndicatorsConnector, ServiceDependenciesConnector, TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.events.{EventService, ReadModelService}
import uk.gov.hmrc.cataloguefrontend.service.{DeploymentsService, LeakDetectionService}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.test.UnitSpec
import views.html.{DigitalServiceInfoPage, IndexPage, ServiceInfoPage, TeamInfoPage}

class PrototypesSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerTest {

  "/prototypes" should {
    "redirect to the repositories page with a filter showing only prototypes" in {

      val result = catalogueController.allPrototypes(FakeRequest())

      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/repositories?name=&type=Prototype")
    }
  }

  private lazy val catalogueController = new CatalogueController(
    mock[UserManagementConnector],
    mock[TeamsAndRepositoriesConnector],
    mock[ServiceDependenciesConnector],
    mock[IndicatorsConnector],
    mock[LeakDetectionService],
    mock[DeploymentsService],
    mock[EventService],
    mock[ReadModelService],
    app.environment,
    mock[VerifySignInStatus],
    mock[UmpAuthenticated],
    app.injector.instanceOf[ServicesConfig],
    mock[UserManagementPortalConfig],
    app.injector.instanceOf[ViewMessages],
    app.injector.instanceOf[MessagesControllerComponents],
    mock[DigitalServiceInfoPage],
    mock[IndexPage],
    mock[TeamInfoPage],
    mock[ServiceInfoPage]
  )
}
