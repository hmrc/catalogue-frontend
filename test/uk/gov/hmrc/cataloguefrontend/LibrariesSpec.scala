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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.cataloguefrontend.actions.{UmpAuthenticated, VerifySignInStatus}
import uk.gov.hmrc.cataloguefrontend.connector.{IndicatorsConnector, ServiceDependenciesConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.events.{EventService, ReadModelService}
import uk.gov.hmrc.cataloguefrontend.service.{DeploymentsService, LeakDetectionService}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.test.UnitSpec

class LibrariesSpec extends UnitSpec with ScalaFutures with MockitoSugar with GuiceOneAppPerTest {

  "/libraries" should {
    "redirect to the repositories page with the appropriate filters" in {

      val result: Result =
        new CatalogueController(
          userManagementConnector = mock[UserManagementConnector],
          teamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector],
          serviceDependencyConnector = mock[ServiceDependenciesConnector],
          indicatorsConnector = mock[IndicatorsConnector],
          leakDetectionService = mock[LeakDetectionService],
          deploymentsService = mock[DeploymentsService],
          eventService = mock[EventService],
          readModelService = mock[ReadModelService],
          environment = mock[play.api.Environment],
          verifySignInStatus = mock[VerifySignInStatus],
          umpAuthenticated = mock[UmpAuthenticated],
          serviceConfig = mock[ServicesConfig],
          viewMessages = app.injector.instanceOf[ViewMessages],
          mcc = app.injector.instanceOf[MessagesControllerComponents]
        ).allLibraries(FakeRequest()).futureValue

      result.header.status              shouldBe 303
      result.header.headers("Location") shouldBe "/repositories?name=&type=Library"
    }
  }
}
