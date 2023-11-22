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

package uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, MessagesApi}
import play.api.mvc._
import play.api.test.Helpers
import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, TeamsAndRepositoriesConnector, ServiceType}
import uk.gov.hmrc.cataloguefrontend.connector.RepoType
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus._
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}
import views.html.servicecommissioningstatus.{ServiceCommissioningStatusPage, SearchServiceCommissioningStatusPage}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class ServiceCommissioningStatusControllerSpec
  extends AnyWordSpec
     with Matchers
     with GuiceOneAppPerSuite
     with MockitoSugar
     with ArgumentMatchersSugar
     with OptionValues
     with ScalaFutures {

  implicit lazy val defaultLang: Lang = Lang(java.util.Locale.getDefault)

  "filterChecks" should {
    "remove the Upscan checks when not configured" in new Setup {
      val repository = GitRepository(
        name                = "Name",
        description         = "Description",
        githubUrl           = "http://www.github.com/hmrc/foo",
        createdDate         = Instant.now,
        lastActiveDate      = Instant.now,
        repoType            = RepoType.Service,
        serviceType         = Some(ServiceType.Frontend),
        language            = None,
        isArchived          = false,
        defaultBranch       = "main",
      )
      val checks: List[Check] = List(Check.EnvCheck(
        title        = "Upscan Config",
        checkResults = Map[Environment, Check.Result](
          Environment.QA -> Left(Check.Missing("missing-link"))
        ),
        helpText     = "helpText",
        linkToDocs   = None
      ))

      val result = controller.filterChecks(Some(repository), Some(checks))

      result.map(_ shouldBe empty)
    }
    "leave the Upscan checks when configured" in new Setup {
      val repository = GitRepository(
        name                = "Name",
        description         = "Description",
        githubUrl           = "http://www.github.com/hmrc/foo",
        createdDate         = Instant.now,
        lastActiveDate      = Instant.now,
        repoType            = RepoType.Service,
        serviceType         = Some(ServiceType.Frontend),
        language            = None,
        isArchived          = false,
        defaultBranch       = "main",
      )
      val checks: List[Check] = List(Check.EnvCheck(
        title        = "Upscan Config",
        checkResults = Map[Environment, Check.Result](
          Environment.QA -> Right(Check.Present("link"))
        ),
        helpText     = "helpText",
        linkToDocs   = None
      ))

      val result = controller.filterChecks(Some(repository), Some(checks))

      result.map(_ should not be empty)
    }
  }


  private[this] trait Setup {
    val serviceCommissioningStatusConnector  = app.injector.instanceOf[ServiceCommissioningStatusConnector]
    val teamsAndRepositoriesConnector        = app.injector.instanceOf[TeamsAndRepositoriesConnector]
    val serviceCommissioningStatusPage       = app.injector.instanceOf[ServiceCommissioningStatusPage]
    val searchServiceCommissioningStatusPage = app.injector.instanceOf[SearchServiceCommissioningStatusPage]
    val messagesApi                          = app.injector.instanceOf[MessagesApi]
    val mcc                                  = app.injector.instanceOf[MessagesControllerComponents]
    val authStubBehaviour                    = mock[StubBehaviour]
    val authComponent                        = { implicit val cc: ControllerComponents = Helpers.stubControllerComponents()
                                                 FrontendAuthComponentsStub(authStubBehaviour)
                                               }
    val controller                           = new ServiceCommissioningStatusController(
      serviceCommissioningStatusConnector,
      teamsAndRepositoriesConnector,
      serviceCommissioningStatusPage,
      searchServiceCommissioningStatusPage,
      mcc,
      authComponent,
    )
  }
}
