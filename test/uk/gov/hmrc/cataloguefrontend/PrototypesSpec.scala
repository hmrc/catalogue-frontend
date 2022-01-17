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

package uk.gov.hmrc.cataloguefrontend

import org.mockito.MockitoSugar
import play.api.Configuration
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.service.{ConfigService, CostEstimateConfig, CostEstimationService, DefaultBranchesService, LeakDetectionService, RouteRulesService}
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterService
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhereService
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents
import views.html._

import scala.concurrent.ExecutionContext

class PrototypesSpec extends UnitSpec with MockitoSugar {
  import ExecutionContext.Implicits.global

  "/prototypes" should {
    "redirect to the repositories page with a filter showing only prototypes" in {
      val result = catalogueController.allPrototypes(FakeRequest())

      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/repositories?repoType=Prototype")
    }
  }

  private lazy val catalogueController = new CatalogueController(
    userManagementConnector       = mock[UserManagementConnector],
    teamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector],
    configService                 = mock[ConfigService],
    costEstimationService         = mock[CostEstimationService],
    serviceCostEstimateConfig     = mock[CostEstimateConfig],
    routeRulesService             = mock[RouteRulesService],
    serviceDependenciesConnector  = mock[ServiceDependenciesConnector],
    leakDetectionService          = mock[LeakDetectionService],
    shutterService                = mock[ShutterService],
    defaultBranchesService        = mock[DefaultBranchesService],
    userManagementPortalConfig    = mock[UserManagementPortalConfig],
    configuration                 = Configuration.empty,
    mcc                           = stubMessagesControllerComponents(),
    digitalServiceInfoPage        = mock[DigitalServiceInfoPage],
    whatsRunningWhereService      = mock[WhatsRunningWhereService],
    indexPage                     = mock[IndexPage],
    teamInfoPage                  = mock[TeamInfoPage],
    serviceInfoPage               = mock[ServiceInfoPage],
    serviceConfigPage             = mock[ServiceConfigPage],
    serviceConfigRawPage          = mock[ServiceConfigRawPage],
    libraryInfoPage               = mock[LibraryInfoPage],
    prototypeInfoPage             = mock[PrototypeInfoPage],
    repositoryInfoPage            = mock[RepositoryInfoPage],
    repositoriesListPage          = mock[RepositoriesListPage],
    defaultBranchListPage         = mock[DefaultBranchListPage],
    outOfDateTeamDependenciesPage = mock[OutOfDateTeamDependenciesPage],
    costEstimationPage            = mock[CostEstimationPage]
  )
}
