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
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cataloguefrontend.FakeApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.deployments.view.html.DeploymentTimelinePage
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, Version}
import uk.gov.hmrc.cataloguefrontend.test.UnitSpec
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.DeploymentTimelineEvent
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.internalauth.client.Retrieval
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents

import java.time.{Instant, LocalDate}
import scala.concurrent.{ExecutionContext, Future}

class DeploymentTimelineControllerSpec
  extends UnitSpec
     with MockitoSugar
     with FakeApplicationBuilder {
  import ExecutionContext.Implicits.global

  private trait Setup {
    given HeaderCarrier = HeaderCarrier()

    given mcc: MessagesControllerComponents = stubMessagesControllerComponents()

    lazy val mockedTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    lazy val mockedServiceDependenciesConnector  = mock[ServiceDependenciesConnector]
    lazy val authStubBehaviour                   = mock[StubBehaviour]
    lazy val mockedDeploymentGraphService        = mock[DeploymentGraphService]
    lazy val authComponent                       = FrontendAuthComponentsStub(authStubBehaviour)
    lazy val page                                = DeploymentTimelinePage()

    lazy val controller =
      DeploymentTimelineController(
        mockedTeamsAndRepositoriesConnector,
        mockedServiceDependenciesConnector,
        mockedDeploymentGraphService,
        page,
        mcc,
        authComponent
      )
  }

  "DeploymentTimeline" should {
    "return 200" in new Setup {
      val start = LocalDate.now().minusDays(1)
      val end   = LocalDate.now()

      when(authStubBehaviour.stubAuth(None, Retrieval.EmptyRetrieval))
        .thenReturn(Future.unit)
      when(mockedTeamsAndRepositoriesConnector.allRepositories(
        name        = any,
        team        = any,
        archived    = any,
        repoType    = eqTo(Some(RepoType.Service)),
        serviceType = any
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty))
      when(mockedDeploymentGraphService.findEvents(service = any, start = any, end = any)(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(DeploymentTimelineEvent(Environment.Integration, Version(1, 0, 0, ""), "deploymentId", "ua", Instant.now(), Instant.now()))))
      when(mockedServiceDependenciesConnector.getSlugInfo(any, any)(using any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      val response = controller.graph(Some(ServiceName("foo")), start, end)(FakeRequest(GET, "/deployment-timeline").withSession(SessionKeys.authToken -> "Token token"))
      status(response) shouldBe 200
    }
  }
}
