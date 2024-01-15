/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.createuser

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.{POST, contentAsString, defaultAwaitTimeout, redirectLocation, status}
import uk.gov.hmrc.cataloguefrontend.FakeApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.connector.{TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.users.{CreateUserController, CreateUserRequest, Organisation, routes}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, Predicate, Resource, ResourceLocation, ResourceType, Retrieval}
import views.html.users.{CreateUserPage, CreateUserRequestSentPage}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CreateUserControllerSpec
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

  "CreateUserController.createUserLanding" should {
    "have the correct url setup for creating a human user" in {
      uk.gov.hmrc.cataloguefrontend.users.routes.CreateUserController.createUserLanding(isServiceAccount = false)
        .url shouldBe "/create-user"
    }
  }

  "CreateUserController.createServiceUserLanding" should {
    "have the correct url setup for creating a non human user" in {
      uk.gov.hmrc.cataloguefrontend.users.routes.CreateUserController.createUserLanding(isServiceAccount = true)
        .url shouldBe "/create-service-user"
    }
  }

  "CreateUserController.createUserLanding" should {

    "return 200 when user is authenticated when creating a human user" in new Setup {

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("test-service"), ResourceLocation("teams/TestTeam")))))

      val result = controller
        .createUserLanding(isServiceAccount = false)(
          FakeRequest()
            .withSession(SessionKeys.authToken -> "Token token")
        )

      status(result) shouldBe 200
    }

    "return 200 with form error - when user does not have permission to create a human user" in new Setup {

      when(authStubBehaviour.stubAuth(eqTo(None), any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set.empty[Resource]))

      val result = controller
        .createUserLanding(isServiceAccount = false)(
          FakeRequest()
            .withSession(SessionKeys.authToken -> "Token token")
        )

      status(result) shouldBe 200
      contentAsString(result) should include(s" There are currently no teams that you have permissions to create a user for.")

    }
  }

  "CreateUserController.createUserLanding" should {

    "return 200 when user is authenticated when creating a non human user" in new Setup {

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("test-service"), ResourceLocation("teams/TestTeam")))))

      val result = controller
        .createUserLanding(isServiceAccount = true)(
          FakeRequest()
            .withSession(SessionKeys.authToken -> "Token token")
        )

      status(result) shouldBe 200
    }

    "return 200 with form error - when user does not have permission to create non human a user" in new Setup {

      when(authStubBehaviour.stubAuth(eqTo(None), any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set.empty[Resource]))

      val result = controller
        .createUserLanding(isServiceAccount = true)(
          FakeRequest()
            .withSession(SessionKeys.authToken -> "Token token")
        )

      status(result) shouldBe 200
      contentAsString(result) should include(s" There are currently no teams that you have permissions to create a user for.")

    }
  }

  "CreateUserController.createUser" should {

    "redirect to human user creation request sent page when the form is submitted successfully" in new Setup {

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("test-service"), ResourceLocation("teams/TestTeam")))))

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      when(mockUMConnector.createUser(any[CreateUserRequest], isServiceAccount = eqTo(false))(any[HeaderCarrier]))
        .thenReturn(Future.unit)

      val result = controller
        .createUser(isServiceAccount = false)(
          FakeRequest(POST, "/create-user")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody(
              "givenName"    -> user.givenName,
              "familyName"   -> user.familyName,
              "organisation" -> user.organisation,
              "team"         -> user.team.asString,
              "contactEmail" -> user.contactEmail
            )
        )

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CreateUserController.requestSent(user.givenName, user.familyName, isServiceAccount = false).url)

    }
  }

  "CreateUserController.createNonHumanUser" should {

    "redirect to service user creation request sent page when the form is submitted successfully" in new Setup {

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("test-service"), ResourceLocation("teams/TestTeam")))))

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      when(mockUMConnector.createUser(any[CreateUserRequest], isServiceAccount = eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.unit)

      val result = controller
        .createUser(isServiceAccount = true)(
          FakeRequest(POST, "/create-service-user")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody(
              "givenName"    -> user.givenName,
              "familyName"   -> user.familyName,
              "organisation" -> user.organisation,
              "team"         -> user.team.asString,
              "contactEmail" -> user.contactEmail
            )
        )

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.CreateUserController.requestSent(user.givenName, user.familyName, isServiceAccount = true).url)

    }
  }

  private trait Setup {
    implicit val hc      : HeaderCarrier                 = HeaderCarrier()
    implicit val mcc     : MessagesControllerComponents  = app.injector.instanceOf[MessagesControllerComponents]
    val mockCUPView      : CreateUserPage                = app.injector.instanceOf[CreateUserPage]
    val mockCURSPView    : CreateUserRequestSentPage     = app.injector.instanceOf[CreateUserRequestSentPage]
    val mockUMConnector  : UserManagementConnector       = mock[UserManagementConnector]
    val authStubBehaviour: StubBehaviour                 = mock[StubBehaviour]
    val authComponent    : FrontendAuthComponents        = FrontendAuthComponentsStub(authStubBehaviour)

    val controller        =
      new CreateUserController(
        auth                      = authComponent,
        mcc                       = mcc,
        createUserPage            = mockCUPView,
        createUserRequestSentPage = mockCURSPView,
        userManagementConnector   = mockUMConnector
      )

    val user: CreateUserRequest =
      CreateUserRequest(
        givenName        = "test",
        familyName       = "user",
        organisation     = Organisation.Mdtp.asString,
        contactEmail     = "test.user@email.gov.uk",
        contactComments  = "test123",
        team             = TeamName("TestTeam"),
        isReturningUser  = false,
        isTransitoryUser = false,
        vpn              = true,
        jira             = true,
        confluence       = true,
        googleApps       = true,
        environments     = true
      )
  }
}
