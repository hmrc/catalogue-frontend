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
import play.api.Environment
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.actions.{UmpAuthenticated, VerifySignInStatus}
import uk.gov.hmrc.cataloguefrontend.connector.{IndicatorsConnector, ServiceDependenciesConnector, TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.events.{EventService, ReadModelService}
import uk.gov.hmrc.cataloguefrontend.service.{DeploymentsService, LeakDetectionService}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.test.UnitSpec
import views.html._

class LibrariesSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerTest {

  "/libraries" should {
    "redirect to the repositories page with the appropriate filters" in {

      val result = catalogueController.allLibraries(FakeRequest())

      status(result)           shouldBe 303
      redirectLocation(result) shouldBe Some("/repositories?name=&type=Library")
    }
  }

  private lazy val catalogueController = new CatalogueController(
    userManagementConnector       = mock[UserManagementConnector],
    teamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector],
    serviceDependencyConnector    = mock[ServiceDependenciesConnector],
    indicatorsConnector           = mock[IndicatorsConnector],
    leakDetectionService          = mock[LeakDetectionService],
    deploymentsService            = mock[DeploymentsService],
    eventService                  = mock[EventService],
    readModelService              = mock[ReadModelService],
    environment                   = mock[Environment],
    verifySignInStatus            = mock[VerifySignInStatus],
    umpAuthenticated              = mock[UmpAuthenticated],
    serviceConfig                 = mock[ServicesConfig],
    userManagementPortalConfig    = mock[UserManagementPortalConfig],
    viewMessages                  = app.injector.instanceOf[ViewMessages],
    mcc                           = app.injector.instanceOf[MessagesControllerComponents],
    digitalServiceInfoPage        = mock[DigitalServiceInfoPage],
    indexPage                     = mock[IndexPage],
    teamInfoPage                  = mock[TeamInfoPage],
    serviceInfoPage               = mock[ServiceInfoPage],
    libraryInfoPage               = mock[LibraryInfoPage],
    prototypeInfoPage             = mock[PrototypeInfoPage],
    repositoryInfoPage            = mock[RepositoryInfoPage]
  )
}
