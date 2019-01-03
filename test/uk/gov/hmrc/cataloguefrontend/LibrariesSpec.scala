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
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.actions.{UmpAuthenticated, VerifySignInStatus}
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.events.{EventService, ReadModelService}
import uk.gov.hmrc.cataloguefrontend.service.{ConfigService, DeploymentsService, LeakDetectionService, RouteRulesService}
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import uk.gov.hmrc.play.test.UnitSpec
import views.html._

class LibrariesSpec extends UnitSpec with MockitoSugar {

  "/libraries" should {
    "redirect to the repositories page with the appropriate filters" in {

      val result = catalogueController.allLibraries(FakeRequest())

      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/repositories?name=&type=Library")
    }
  }

  private lazy val catalogueController = new CatalogueController(
    mock[UserManagementConnector],
    mock[TeamsAndRepositoriesConnector],
    mock[ConfigService],
    mock[RouteRulesService],
    mock[ServiceDependenciesConnector],
    mock[IndicatorsConnector],
    mock[LeakDetectionService],
    mock[DeploymentsService],
    mock[EventService],
    mock[ReadModelService],
    mock[VerifySignInStatus],
    mock[UmpAuthenticated],
    mock[UserManagementPortalConfig],
    stubMessagesControllerComponents(),
    mock[DigitalServiceInfoPage],
    mock[IndexPage],
    mock[TeamInfoPage],
    mock[ServiceInfoPage],
    mock[ServiceConfigPage],
    mock[ServiceConfigRawPage],
    mock[LibraryInfoPage],
    mock[PrototypeInfoPage],
    mock[RepositoryInfoPage],
    mock[RepositoriesListPage]
  )
}
