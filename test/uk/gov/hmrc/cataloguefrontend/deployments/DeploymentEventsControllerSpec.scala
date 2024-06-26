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

package uk.gov.hmrc.cataloguefrontend.deployments

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.FakeApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.deployments.view.html.DeploymentEventsPage
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, Version}
import uk.gov.hmrc.cataloguefrontend.test.UnitSpec
import uk.gov.hmrc.cataloguefrontend.util.DateHelper
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere._
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.internalauth.client.Retrieval
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class DeploymentEventsControllerSpec
  extends UnitSpec
     with MockitoSugar
     with FakeApplicationBuilder {
  import ExecutionContext.Implicits.global

  private trait Setup {
    given mcc: MessagesControllerComponents = stubMessagesControllerComponents()

    lazy val mockedTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    lazy val mockedReleasesConnector             = mock[ReleasesConnector]
    lazy val authStubBehaviour                   = mock[StubBehaviour]
    lazy val authComponent                       = FrontendAuthComponentsStub(authStubBehaviour)
    lazy val page                                = DeploymentEventsPage()

    lazy val controller =
      DeploymentEventsController(
        mockedReleasesConnector,
        mockedTeamsAndRepositoriesConnector,
        page,
        Configuration.empty,
        mcc,
        authComponent
      )
  }

  "DeploymentEvents" should {
    "return 400 when given a bad date" in new Setup {
      when(authStubBehaviour.stubAuth(None, Retrieval.EmptyRetrieval))
        .thenReturn(Future.unit)
      val response = controller.deploymentEvents()(FakeRequest(GET, "/deployments/production?from=baddate").withSession(SessionKeys.authToken -> "Token token"))
      status(response) shouldBe 400
    }

    "return 200 when given no filters" in new Setup {
      when(authStubBehaviour.stubAuth(None, Retrieval.EmptyRetrieval))
        .thenReturn(Future.unit)
      when(mockedReleasesConnector.deploymentHistory(environment = any, from = any, to = any, team = any, service = any, skip = any, limit = any)(using any[HeaderCarrier]))
        .thenReturn(Future.successful(PaginatedDeploymentHistory(history = Seq.empty, 0)))
      when(mockedTeamsAndRepositoriesConnector.allTeams()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty))
      val response = controller.deploymentEvents()(FakeRequest(GET, "/deployments/production").withSession(SessionKeys.authToken -> "Token token"))
      status(response) shouldBe 200
    }

    "request the api filter out audit records by date" in new Setup {
      import DateHelper._

      val d1 = LocalDate.parse("2020-01-01").atStartOfDayInstant

      private val deps = Seq(
        DeploymentHistory(
          ServiceName("s1"),
          Environment.Production,
          Version("1.1.1"),
          Seq.empty,
          TimeSeen(d1),
          Username("user_a")
        )
      )

      when(authStubBehaviour.stubAuth(None, Retrieval.EmptyRetrieval))
        .thenReturn(Future.unit)
      when(mockedReleasesConnector.deploymentHistory(environment = any, from = any, to = any, team = any, service = any, skip = any, limit = any)(using any[HeaderCarrier]))
        .thenReturn(Future.successful(PaginatedDeploymentHistory(deps, deps.length)))
      when(mockedTeamsAndRepositoriesConnector.allTeams()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty))

      val response = controller.deploymentEvents()(FakeRequest(GET, "/deployments/production?from=2020-01-01&to=2020-02-01").withSession(SessionKeys.authToken -> "Token token"))
      status(response) shouldBe 200

      val responseString = contentAsString(response)
      responseString.contains("user_a") shouldBe true
    }

    "request api filter by service name" in new Setup {
      import DateHelper._

      val d1 = LocalDate.parse("2020-01-01").atStartOfDayInstant

      val deps = Seq(
        DeploymentHistory(
          ServiceName("s1"),
          Environment.Production,
          Version("1.1.1"),
          Seq.empty,
          TimeSeen(d1),
          Username("user_a")
        )
      )

      when(authStubBehaviour.stubAuth(None, Retrieval.EmptyRetrieval))
        .thenReturn(Future.unit)
      when(
        mockedReleasesConnector
          .deploymentHistory(environment = eqTo(Environment.Production), from = any, to = any, team = eqTo(None), service = eqTo(Some("s1")), skip = eqTo(None), limit = any)(
            using any[HeaderCarrier]))
        .thenReturn(Future.successful(PaginatedDeploymentHistory(deps, deps.length)))

      when(mockedTeamsAndRepositoriesConnector.allTeams()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty))

      val response = controller.deploymentEvents()(FakeRequest(GET, "/deployments/production?service=s1").withSession(SessionKeys.authToken -> "Token token"))
      status(response) shouldBe 200

      val responseString = contentAsString(response)
      responseString.contains("user_a") shouldBe true
    }

    "request paginated data based on the page number" in new Setup {
      when(
        mockedReleasesConnector
          .deploymentHistory(
            eqTo(Environment.Production),
            from    = any,
            to      = any,
            team    = eqTo(None),
            service = eqTo(None),
            skip    = eqTo(Some(2 * DeploymentEventsController.pageSize)),
            limit   = eqTo(Some(DeploymentEventsController.pageSize))
          )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(PaginatedDeploymentHistory(Seq.empty, 0)))

      when(authStubBehaviour.stubAuth(None, Retrieval.EmptyRetrieval))
        .thenReturn(Future.unit)
      when(mockedTeamsAndRepositoriesConnector.allTeams()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty))

      val response = controller.deploymentEvents()(FakeRequest(GET, "/deployments/production?page=2").withSession(SessionKeys.authToken -> "Token token"))
      status(response) shouldBe 200
    }
  }
}
