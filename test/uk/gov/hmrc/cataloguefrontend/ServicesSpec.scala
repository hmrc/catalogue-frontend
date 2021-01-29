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

package uk.gov.hmrc.cataloguefrontend

import org.mockito.MockitoSugar
import play.api.Configuration
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.actions.{UmpAuthActionBuilder, VerifySignInStatus}
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.events.{EventService, ReadModelService}
import uk.gov.hmrc.cataloguefrontend.service.{ConfigService, LeakDetectionService, RouteRulesService}
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterService
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereService
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import views.html._

import scala.concurrent.ExecutionContext

class ServicesSpec extends UnitSpec with MockitoSugar {
  import ExecutionContext.Implicits.global

  "/services" should {
    "redirect to the repositories page with the appropriate filters" in {

      val result = catalogueController.allServices(FakeRequest())

      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/repositories?name=&type=Service")
    }
  }

  private val catalogueController = new CatalogueController(
    mock[UserManagementConnector],
    mock[TeamsAndRepositoriesConnector],
    mock[ConfigService],
    mock[RouteRulesService],
    mock[ServiceDependenciesConnector],
    mock[LeakDetectionService],
    mock[EventService],
    mock[ReadModelService],
    mock[ShutterService],
    mock[VerifySignInStatus],
    mock[UmpAuthActionBuilder],
    mock[UserManagementPortalConfig],
    Configuration.empty,
    stubMessagesControllerComponents(),
    mock[DigitalServiceInfoPage],
    mock[WhatsRunningWhereService],
    mock[IndexPage],
    mock[TeamInfoPage],
    mock[ServiceInfoPage],
    mock[ServiceConfigPage],
    mock[ServiceConfigRawPage],
    mock[LibraryInfoPage],
    mock[PrototypeInfoPage],
    mock[RepositoryInfoPage],
    mock[RepositoriesListPage],
    mock[OutOfDateTeamDependenciesPage]
  )
}
