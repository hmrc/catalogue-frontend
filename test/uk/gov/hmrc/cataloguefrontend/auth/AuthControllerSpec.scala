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

package uk.gov.hmrc.cataloguefrontend.auth

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.mvc.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.cataloguefrontend.routes as appRoutes
import uk.gov.hmrc.internalauth.client.syntax.toProductOps
import uk.gov.hmrc.internalauth.client.{IAAction, Resource, ResourceLocation, ResourceType, Retrieval}
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthControllerSpec
  extends AnyWordSpec
     with Matchers
     with GuiceOneAppPerSuite
     with MockitoSugar
     with OptionValues
     with ScalaFutures {

  import Helpers._

  "Sanitize" should {
    "filter out redirecting urls" in new Setup {
      AuthController.sanitize(None) shouldBe None
      AuthController.sanitize(Some(RedirectUrl(routes.AuthController.signIn(Some(RedirectUrl("/my-url"))).url))) shouldBe None
      AuthController.sanitize(Some(RedirectUrl(routes.AuthController.postSignIn(Some(RedirectUrl("/my-url"))).url))) shouldBe None
      AuthController.sanitize(Some(RedirectUrl("/my-url"))) shouldBe Some(RedirectUrl("/my-url"))
    }
  }

  "Signing in" should {
    "redirect to internal-auth" in new Setup {
      val request = FakeRequest()

      val result = controller.signIn(targetUrl = None)(request)

      redirectLocation(result) shouldBe Some("http://localhost:8471/test-only/sign-in?test_only_base_url=http://localhost:9000&continue_url=%2Fpost-sign-in")
    }

    "forward target Url" in new Setup {
      val request = FakeRequest()

      val result = controller.signIn(targetUrl = Some(RedirectUrl("/my-url")))(request)

      redirectLocation(result) shouldBe Some("http://localhost:8471/test-only/sign-in?test_only_base_url=http://localhost:9000&continue_url=%2Fpost-sign-in%3FtargetUrl%3D%252Fmy-url")
    }

    "redirect to requested page if already logged in" in new Setup {
      val request = FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      when(
        authStubBehaviour.stubAuth(
          None,
          Retrieval.EmptyRetrieval
        )
      ).thenReturn(Future.unit)

      val result = controller.signIn(targetUrl = Some(RedirectUrl("/my-url")))(request)

      redirectLocation(result) shouldBe Some("/post-sign-in?targetUrl=%2Fmy-url")
    }
  }

  "Returning from signing-in" should {
    "put username into session" in new Setup {
      val request = FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      when(
        authStubBehaviour.stubAuth(
          None,
          Retrieval.username ~ createUserRetrieval ~ manageUserRetrieval
        )
      ).thenReturn(Future.successful(Retrieval.Username("user.name") ~ Set.empty[Resource] ~ Set.empty[Resource]))

      val result = controller.postSignIn(targetUrl = None)(request)

      redirectLocation(result) shouldBe Some(appRoutes.CatalogueController.index.url)

      Helpers.session(result).apply(AuthController.SESSION_USERNAME) shouldBe "user.name"
    }

    "put canCreateUsers into session as true if user is a team admin" in new Setup {
      val request = FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      when(
        authStubBehaviour.stubAuth(
          None,
          Retrieval.username ~ createUserRetrieval ~ manageUserRetrieval
        )
      ).thenReturn(Future.successful(Retrieval.Username("user.name") ~ Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/*"))) ~ Set.empty[Resource]))

      val result = controller.postSignIn(targetUrl = None)(request)

      redirectLocation(result) shouldBe Some(appRoutes.CatalogueController.index.url)

      Helpers.session(result).apply(AuthController.CAN_CREATE_USERS) shouldBe "true"
    }

    "put canCreateUsers into session as false if user is not a team admin" in new Setup {
      val request = FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      when(
        authStubBehaviour.stubAuth(
          None,
          Retrieval.username ~ createUserRetrieval ~ manageUserRetrieval
        )
      ).thenReturn(Future.successful(Retrieval.Username("user.name") ~ Set.empty[Resource] ~ Set.empty[Resource]))

      val result = controller.postSignIn(targetUrl = None)(request)

      redirectLocation(result) shouldBe Some(appRoutes.CatalogueController.index.url)

      Helpers.session(result).apply(AuthController.CAN_CREATE_USERS) shouldBe "false"
    }

    "redirect to requested page" in new Setup {
      val request = FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      when(
        authStubBehaviour.stubAuth(
          None,
          Retrieval.username ~ createUserRetrieval ~ manageUserRetrieval
        )
      ).thenReturn(Future.successful(Retrieval.Username("user.name") ~ Set.empty[Resource] ~ Set.empty[Resource]))

      val result = controller.postSignIn(targetUrl = Some(RedirectUrl("/my-url")))(request)

      redirectLocation(result) shouldBe Some("/my-url")

      Helpers.session(result).apply(AuthController.SESSION_USERNAME) shouldBe "user.name"
    }

    "reject non-relative urls" in new Setup {
      val request = FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      when(
        authStubBehaviour.stubAuth(
          None,
          Retrieval.username ~ createUserRetrieval ~ manageUserRetrieval
        )
      ).thenReturn(Future.successful(Retrieval.Username("user.name") ~ Set.empty[Resource] ~ Set.empty[Resource]))

      val result = controller.postSignIn(targetUrl = Some(RedirectUrl("http://other-site/my-url")))(request)

      redirectLocation(result) shouldBe Some(appRoutes.CatalogueController.index.url)
    }

    "put canManagerUsers into session as true if user is a member of the group, admins" in new Setup {
      val request = FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      when(
        authStubBehaviour.stubAuth(
          None,
          Retrieval.username ~ createUserRetrieval ~ manageUserRetrieval
        )
      ).thenReturn(Future.successful(Retrieval.Username("user.name") ~ Set.empty[Resource] ~ Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/*")))))

      val result = controller.postSignIn(targetUrl = None)(request)

      redirectLocation(result) shouldBe Some(appRoutes.CatalogueController.index.url)

      Helpers.session(result).apply(AuthController.CAN_MANAGE_USERS) shouldBe "true"
    }

    "put canManagerUsers into session as false if user is not a member of the group, admins" in new Setup {
      val request = FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      when(
        authStubBehaviour.stubAuth(
          None,
          Retrieval.username ~ createUserRetrieval ~ manageUserRetrieval
        )
      ).thenReturn(Future.successful(Retrieval.Username("user.name") ~ Set.empty[Resource] ~ Set.empty[Resource]))

      val result = controller.postSignIn(targetUrl = None)(request)

      redirectLocation(result) shouldBe Some(appRoutes.CatalogueController.index.url)

      Helpers.session(result).apply(AuthController.CAN_MANAGE_USERS) shouldBe "false"
    }

    "put multiple grants into the session when a user has multiple permissions" in new Setup {
      val request = FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      when(
        authStubBehaviour.stubAuth(
          None,
          Retrieval.username ~ createUserRetrieval ~ manageUserRetrieval
        )
      ).thenReturn(Future.successful(Retrieval.Username("user.name")
        ~ Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/*")))
        ~ Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/*"))))
      )

      val result = controller.postSignIn(targetUrl = None)(request)

      redirectLocation(result) shouldBe Some(appRoutes.CatalogueController.index.url)

      Helpers.session(result).apply(AuthController.CAN_CREATE_USERS)   shouldBe "true"
      Helpers.session(result).apply(AuthController.CAN_MANAGE_USERS) shouldBe "true"
    }
  }

  "Signing out" should {
    "redirect to landing page and clear session" in new Setup {
      val request = FakeRequest()

      val result = controller.signOut(request)

      redirectLocation(result)      shouldBe Some(appRoutes.CatalogueController.index.url)
      result.futureValue.newSession shouldBe Some(Session())
    }
  }

  private[this] trait Setup {
    val messagesApi       = app.injector.instanceOf[MessagesApi]
    val mcc               = app.injector.instanceOf[MessagesControllerComponents]
    val authStubBehaviour = mock[StubBehaviour]
    val authComponent     = { given ControllerComponents = Helpers.stubControllerComponents()
                              FrontendAuthComponentsStub(authStubBehaviour)
                            }
    val controller        = AuthController(authComponent, mcc)
    
    val createUserRetrieval = Retrieval.locations(
      resourceType = Some(ResourceType("catalogue-frontend")),
      action = Some(IAAction("CREATE_USER"))
    )

    val manageUserRetrieval = Retrieval.locations(
     resourceType = Some(ResourceType("catalogue-frontend")),
     action       = Some(IAAction("MANAGE_USER"))
    )
  }
}
