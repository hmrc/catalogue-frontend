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

package uk.gov.hmrc.cataloguefrontend.createawebhook

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.{POST, contentAsString, defaultAwaitTimeout, redirectLocation, status}
import uk.gov.hmrc.cataloguefrontend.FakeApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.{routes => catalogueRoutes}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys, UpstreamErrorResponse}
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.internalauth.client._
import views.html.CreateAWebhookPage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.time.Instant

class CreateAWebhookControllerSpec
  extends AnyWordSpec
  with Matchers
  with MockitoSugar
  with ArgumentMatchersSugar
  with FakeApplicationBuilder
  with ScalaFutures {

  override def beforeEach(): Unit = {
    super.beforeEach()
    setupAuthEndpoint()
  }

  "CreateAWebhookController" should {
    "have the correct url setup" in {
      uk.gov.hmrc.cataloguefrontend.createawebhook.routes.CreateAWebhookController.createAWebhookLanding(Some("test-service"))
        .url shouldBe "/create-a-webhook?repositoryName=test-service"
    }

    "return permission with correct resource type and action" in new Setup {
      controller.createWebhookPermission(serviceName) shouldBe
        Permission(
          Resource(
            ResourceType("catalogue-frontend"),
            ResourceLocation("services/test-service")
          ),
          IAAction("CREATE_WEBHOOK")
        )
    }
  }

  "CreateAWebhookController.createAWebhookLanding" should {

    "return 200 when user is authenticated" in new Setup {

      when(authStubBehaviour.stubAuth(eqTo(None), any[Retrieval[Boolean]]))
        .thenReturn(Future.successful(true))
      
      when(mockTRConnector.allRepositories(eqTo(None), eqTo(None), eqTo(None), eqTo(None), eqTo(None))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(someService)))

      val result = controller
        .createAWebhookLanding(Some(serviceName))(
          FakeRequest()
            .withSession(SessionKeys.authToken -> "Token token")
        )

      status(result) shouldBe 200
    }
  }

  "CreateAWebhookController.createAWebhook" should {

    "redirect to the service page when the form is submitted successfully" in new Setup {

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Unit]]))
        .thenReturn(Future.successful(()))

      when(mockTRConnector.allRepositories(eqTo(None), eqTo(None), eqTo(None), eqTo(None), eqTo(None))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(someService)))

      when(mockBDConnector.createAWebhook(any[CreateWebhookForm]))
        .thenReturn(Future.successful(Right(BuildDeployApiConnector.AsyncRequestId("1234"))))

      val result = controller
        .createAWebhook()(
          FakeRequest(POST, "/create-a-webhook")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody(
              "repositoryName" -> serviceName,
              "events[0]"      -> "EVENT_ISSUES",
              "webhookUrl"     -> webhookUrl,
            )
        )

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(s"${catalogueRoutes.CatalogueController.repository(serviceName).url}?infoMessage=A+new+webhook+has+been+created.")
    }

    "return 400 when the form is submitted with errors" in new Setup {

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Unit]]))
        .thenReturn(Future.successful(()))

      when(mockTRConnector.allRepositories(eqTo(None), eqTo(None), eqTo(None), eqTo(None), eqTo(None))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(someService)))

      val result = controller
        .createAWebhook()(
          FakeRequest(POST, "/create-a-webhook")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody("webhookUrl" -> "ba:/d.url")
        )

      status(result) shouldBe 400
      val content = contentAsString(result) 
      
      content should include("Please, select a supported event type")
      content should include("Webhook url is not valid.")
    }

    "return 400 when form is submitted with no input values" in new Setup {

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Unit]]))
        .thenReturn(Future.successful(()))
      
      when(mockTRConnector.allRepositories(eqTo(None), eqTo(None), eqTo(None), eqTo(None), eqTo(None))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(someService)))

      val result = controller
        .createAWebhook()(
          FakeRequest(POST, "/create-a-webhook")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody("" -> "")
        )

      contentAsString(result) should include("This field is required")
      status(result) shouldBe 400
    }

    "return 403 with global form error - when user does not have permission to create a webhook for the service" in new Setup {

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Unit]]))
        .thenReturn(Future.successful(()), Future.failed(UpstreamErrorResponse("Unauthorised", 403)))
      
      when(mockTRConnector.allRepositories(eqTo(None), eqTo(None), eqTo(None), eqTo(None), eqTo(None))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(someService)))

      val result = controller
        .createAWebhook()(
          FakeRequest(POST, "/create-a-webhook")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody(
              "repositoryName" -> serviceName,
              "events[0]"      -> "EVENT_ISSUES",
              "webhookUrl"     -> webhookUrl,
            )
        )

      status(result) shouldBe 403
      contentAsString(result) should include("Unauthorised. Please, choose a repository on which you have permissions.")
    }
  }

  private trait Setup {

    implicit val hc       = HeaderCarrier()
    implicit val mcc      = app.injector.instanceOf[MessagesControllerComponents]
    val mockCAWView       = app.injector.instanceOf[CreateAWebhookPage]
    val mockTRConnector   = mock[TeamsAndRepositoriesConnector]
    val mockGHConnector   = mock[GitHubProxyConnector]
    val mockBDConnector   = mock[BuildDeployApiConnector]
    val authStubBehaviour = mock[StubBehaviour]
    val authComponent     = FrontendAuthComponentsStub(authStubBehaviour)
    val controller        =
      new CreateAWebhookController(
        auth                                = authComponent,
        mcc                                 = mcc,
        createAWebhookPage                  = mockCAWView,
        buildDeployApiConnector             = mockBDConnector,
        teamsAndRepositoriesConnector       = mockTRConnector,
      )

    val serviceName = "test-service"
    val events      = WebhookEventType.values.map(_.value())
    val webhookUrl  = "http://url.com"

    val someService = GitRepository(
      name           = serviceName
    , description    = "some-description"
    , githubUrl      = "some-github-url"
    , createdDate    = Instant.now
    , lastActiveDate = Instant.now
    , language       = Some("some-language")
    , isArchived     = false
    , defaultBranch  = "some-default-branch"
    )
  }
}
