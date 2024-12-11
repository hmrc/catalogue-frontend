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

package uk.gov.hmrc.cataloguefrontend.editUser

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.{POST, defaultAwaitTimeout, redirectLocation, status}
import uk.gov.hmrc.cataloguefrontend.config.CatalogueConfig
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector
import uk.gov.hmrc.cataloguefrontend.model.{TeamName, UserName}
import uk.gov.hmrc.cataloguefrontend.test.FakeApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.users.view.html.{EditUserAccessPage, EditUserRequestSentPage}
import uk.gov.hmrc.cataloguefrontend.users.{EditUserAccessRequest, EditUserController, Organisation, Role, User, UserAccess, routes}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.internalauth.client.*
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EditUserControllerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with FakeApplicationBuilder
    with ScalaFutures:

  override def beforeEach(): Unit =
    super.beforeEach()
    setupAuthEndpoint()

  "EditUserController.editUserLanding" should:
    "have the correct url setup" in:
      uk.gov.hmrc.cataloguefrontend.users.routes.EditUserController.editUserLanding(UserName("joe.bloggs"), None)
        .url shouldBe "/users/edit-user/access?username=joe.bloggs"

  "EditUserController.editUserLanding" should:

    "return 200 when user is authenticated when editing a user" in new Setup:

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("test-service"), ResourceLocation("teams/TestTeam")))))

      when(mockUMConnector.getUserAccess(any[UserName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(UserAccess(vpn = true, jira = true, confluence = true, devTools = true, googleApps = true)))

      val result = controller
        .editUserLanding(UserName("joe.bloggs"), Some("MDTP"))(
          FakeRequest()
            .withSession(SessionKeys.authToken -> "Token token")
        )

      status(result) shouldBe 200

  "EditUserController.editUserAccess" should:

    "redirect to edit user request sent page when the form is submitted successfully" in new Setup:

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("test-service"), ResourceLocation("teams/TestTeam")))))

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      when(mockUMConnector.getUserAccess(any[UserName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(UserAccess(vpn = true, jira = true, confluence = true, devTools = true, googleApps = true)))

      when(mockUMConnector.getUser(any[UserName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(user)))

      when(mockUMConnector.editUserAccess(any[EditUserAccessRequest])(using any[HeaderCarrier]))
        .thenReturn(Future.unit)

      val result = controller
        .editUserAccess(UserName("joe.bloggs"), Some("MDTP"))(
          FakeRequest(POST, "/edit-service-user")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody(
              "username"     -> editUserAccess.username,
              "organisation" -> editUserAccess.organisation,
              "vpn"          -> editUserAccess.vpn.toString,
              "jira"         -> editUserAccess.jira.toString,
              "confluence"   -> editUserAccess.confluence.toString,
              "environments" -> editUserAccess.environments.toString,
              "googleApps"   -> editUserAccess.googleApps.toString,
              "bitwarden"    -> editUserAccess.bitwarden.toString,
            )
        )

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.EditUserController.requestSent(UserName(editUserAccess.username)).url)

  private trait Setup:
    given HeaderCarrier = HeaderCarrier()
    given mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

    val mockCUPView      : EditUserAccessPage            = app.injector.instanceOf[EditUserAccessPage]
    val mockCURSPView    : EditUserRequestSentPage       = app.injector.instanceOf[EditUserRequestSentPage]
    val mockConfig       : CatalogueConfig               = mock[CatalogueConfig]
    val mockUMConnector  : UserManagementConnector       = mock[UserManagementConnector]
    val authStubBehaviour: StubBehaviour                 = mock[StubBehaviour]
    val authComponent    : FrontendAuthComponents        = FrontendAuthComponentsStub(authStubBehaviour)

    val controller =
      EditUserController(
        auth                      = authComponent,
        mcc                       = mcc,
        editUserAccessPage        = mockCUPView,
        editUserRequestSentPage   = mockCURSPView,
        userManagementConnector   = mockUMConnector
      )

    val editUserAccess: EditUserAccessRequest =
      EditUserAccessRequest(
        username         = "joe.bloggs",
        organisation     = Organisation.Mdtp.asString,
        vpn              = true,
        jira             = true,
        confluence       = true,
        googleApps       = true,
        environments     = true,
        bitwarden        = true
      )

    val user: User =
      User(
        givenName = Some("joe"),
        familyName = "bloggs",
        organisation = Some(Organisation.Mdtp.asString),
        displayName = Some("test123"),
        primaryEmail = "test.user@email.gov.uk",
        username = UserName("joe.bloggs"),
        githubUsername = Some("joe.bloggs"),
        phoneNumber = None,
        role = Role("user"),
        teamNames = Seq(TeamName("TestTeam"))
      )
