/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.users

import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.cataloguefrontend.config.UserManagementPortalConfig
import uk.gov.hmrc.cataloguefrontend.connector.{GitHubProxyConnector, TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.model.{TeamName, UserName}
import uk.gov.hmrc.cataloguefrontend.test.FakeApplicationBuilder
import uk.gov.hmrc.cataloguefrontend.users.view.html.{LdapResetRequestSentPage, OffBoardUsersPage, UserInfoPage, UserListPage, UserSearchResults, VpnRequestSentPage}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.internalauth.client.*
import uk.gov.hmrc.internalauth.client.syntax.*
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.mockito.ArgumentMatchers

class UsersControllerSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with FakeApplicationBuilder
     with ScalaFutures:

  override def beforeEach(): Unit =
    super.beforeEach()
    setupAuthEndpoint()

  "UsersController.requestLdapReset" should:
    "show a validation error and not request an LDAP password reset when the secondary email matches the primary email" in new Setup:
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Any]]))
        .thenReturn(
          Future.successful(Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/TestTeam")))),
          Future.successful(Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/TestTeam"))) ~ Set.empty[Resource])
        )

      when(mockUMConnector.getUser(any[UserName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(user)))
      when(mockUMConnector.getUserAccess(any[UserName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(UserAccess.empty))
      when(mockTeamsAndRepos.allTeams(any)(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty))
      when(mockUMConnector.resetLdapPassword(any[ResetLdapPassword])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some("TSR-123")))

      val result =
        controller.requestLdapReset(user.username)(
          FakeRequest(POST, "/users/reset-ldap-password")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody(
              "username" -> user.username.asString,
              "email"    -> user.primaryEmail
            )
        )

      status(result) shouldBe BAD_REQUEST
      val document = Jsoup.parse(contentAsString(result))
      document.select("#emailInput").attr("type") shouldBe "text"
      document.select("#emailInput").hasClass("is-invalid") shouldBe true
      document.select("#ldapModal .invalid-feedback").text() shouldBe LdapResetForm.secondaryEmailMustNotMatchPrimaryEmail
      verify(mockUMConnector, never()).resetLdapPassword(any[ResetLdapPassword])(using any[HeaderCarrier])

  "UsersController.user" should:
    "lowercase the username before calling User Management" in new Setup:
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Any]]))
        .thenReturn(
          Future.successful(
            Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/TestTeam"))) ~
            Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/*")))
          )
        )

      when(mockUMConnector.getUser(any[UserName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(user)))

      when(mockUMConnector.getUserRoles(any[UserName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(UserRoles(Seq.empty)))

      when(mockUMConnector.getUserAccess(any[UserName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(UserAccess.empty))

      when(mockTeamsAndRepos.allTeams(any)(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty))

      val mixedCaseUsername = UserName("John.Smith")

      val result =
        controller.user(mixedCaseUsername)(
          FakeRequest(GET, s"/users/${mixedCaseUsername.asString}")
            .withSession(SessionKeys.authToken -> "Token token")
        )

      status(result) shouldBe OK
      verify(mockUMConnector).getUser(ArgumentMatchers.eq("john.smith").asInstanceOf[UserName])(using any[HeaderCarrier])

  private trait Setup:
    given HeaderCarrier = HeaderCarrier()
    given mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

    val mockUMConnector       : UserManagementConnector       = mock[UserManagementConnector]
    val mockTeamsAndRepos     : TeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    val mockGitHubProxy       : GitHubProxyConnector          = mock[GitHubProxyConnector]
    val authStubBehaviour     : StubBehaviour                 = mock[StubBehaviour]
    val authComponent         : FrontendAuthComponents        = FrontendAuthComponentsStub(authStubBehaviour)

    val controller: UsersController =
      UsersController(
        userManagementConnector  = mockUMConnector,
        userInfoPage             = app.injector.instanceOf[UserInfoPage],
        userListPage             = app.injector.instanceOf[UserListPage],
        userSearchResults        = app.injector.instanceOf[UserSearchResults],
        vpnRequestSentPage       = app.injector.instanceOf[VpnRequestSentPage],
        ldapResetRequestSentPage = app.injector.instanceOf[LdapResetRequestSentPage],
        offBoardUsersPage        = app.injector.instanceOf[OffBoardUsersPage],
        umpConfig                = app.injector.instanceOf[UserManagementPortalConfig],
        teamsAndReposConnector   = mockTeamsAndRepos,
        gitHubProxyConnector     = mockGitHubProxy,
        mcc                      = mcc,
        auth                     = authComponent
      )

    val user: User =
      User(
        displayName    = Some("Joe Bloggs"),
        familyName     = "Bloggs",
        givenName      = Some("Joe"),
        organisation   = Some(Organisation.Mdtp.asString),
        primaryEmail   = "joe.bloggs@digital.hmrc.gov.uk",
        username       = UserName("joe.bloggs"),
        githubUsername = Some("joe.bloggs"),
        phoneNumber    = None,
        role           = Role("user"),
        teamNames      = Seq(TeamName("TestTeam")),
        isDeleted      = false,
        isNonHuman     = false
      )
