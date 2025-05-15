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

package uk.gov.hmrc.cataloguefrontend.createrepository

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContent, MessagesControllerComponents, Result}
import play.api.test.*
import play.api.test.Helpers.*
import uk.gov.hmrc.cataloguefrontend.connector.{BuildDeployApiConnector, GitHubTeam, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.createrepository.CreateRepositoryController.RepoTypeOut
import uk.gov.hmrc.cataloguefrontend.createrepository.RepoType.Test
import uk.gov.hmrc.cataloguefrontend.createrepository.view.html.*
import uk.gov.hmrc.cataloguefrontend.model.TeamName
import uk.gov.hmrc.cataloguefrontend.test.FakeApplicationBuilder
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.internalauth.client.*
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.mongo.cache.SessionCacheRepository
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CreateRepositoryControllerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with FakeApplicationBuilder:

  "CreateRepositoryController" should:
    "render the landing page on GET request" in new Setup:
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/TestTeam")))))

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      val fakeRequest: FakeRequest[AnyContent] =
        FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      val result: Future[Result] =
        controller.createRepoLandingGet()(fakeRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Create a Repository")

    "return a BadRequest when invalid form is submitted in POST" in new Setup:
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/TestTeam")))))

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      val fakeRequest: FakeRequest[AnyContent] = FakeRequest(POST, "/create-repo")
        .withFormUrlEncodedBody("repoType" -> "invalidData")
        .withSession(SessionKeys.authToken -> "Token token")

      val result: Future[Result] =
        controller.createRepoLandingPost()(fakeRequest)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Invalid value")

    "redirect to the repository creation page after successful form submission" in new Setup:
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/TestTeam")))))

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      val fakeRequest: FakeRequest[AnyContent] = FakeRequest(POST, "/create-repo")
        .withFormUrlEncodedBody("repoType" -> "Service")
        .withSession(SessionKeys.authToken -> "Token token")

      when(mockCacheRepo.putSession(any(), any())(any(), any())).thenReturn(Future.successful("session" -> "value"))

      val result: Future[Result] =
        controller.createRepoLandingPost()(fakeRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/create-repo/2")

    "display repository creation confirmation page on successful creation" in new Setup:
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/TestTeam")))))

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      val fakeRequest: FakeRequest[AnyContent] =
        FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      val result: Future[Result] =
        controller.createRepoConfirmation(RepoType.Service, "test-repo")(fakeRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Creating service repository: test-repo")

    "render the repository creation page with correctly filtered teams on GET request" in new Setup:
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/TestTeam")), Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/TestTeam2")))))

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      when(mockCacheRepo.getFromSession[RepoTypeOut](any())(any(), any()))
        .thenReturn(Future.successful(Some(RepoTypeOut(Test))))

      when(mockCacheRepo.deleteFromSession[RepoTypeOut](any())(any()))
        .thenReturn(Future.successful(()))

      when(mockTeamsAndReposConnector.allTeams(any())(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(GitHubTeam(TeamName("TestTeam"), None, Seq.empty))))

      val fakeRequest: FakeRequest[AnyContent] =
        FakeRequest().withSession(SessionKeys.authToken -> "Token token")

      val result: Future[Result] =
        controller.createRepoGet()(fakeRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("""option value="TestTeam"""")
      contentAsString(result) should not include("""option value="TestTeam2"""")

    "return a BadRequest when repository creation fails" in new Setup:
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("teams/TestTeam")))))

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      when(mockCacheRepo.getFromSession[RepoTypeOut](any())(any(), any()))
        .thenReturn(Future.successful(Some(RepoTypeOut(Test))))

      when(mockCacheRepo.deleteFromSession[RepoTypeOut](any())(any()))
        .thenReturn(Future.successful(()))

      when(mockTeamsAndReposConnector.allTeams(any())(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(GitHubTeam(TeamName("TestTeam"), None, Seq.empty))))

      when(mockBuildDeployApiConnector.createTestRepository(any())(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Left("Some error")))

      val fakeRequest: FakeRequest[AnyContent] = FakeRequest(POST, "/create-repo/2")
        .withFormUrlEncodedBody("teamName" -> "TestTeam", "repositoryName" -> "repo-test", "makePrivate" -> "false", "testType" -> "UI Journey Test")
        .withSession(SessionKeys.authToken -> "Token token")

      val result: Future[Result] =
        controller.createRepoPost()(fakeRequest)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("Repository creation failed!")

  private trait Setup:
    given mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

    val mockBuildDeployApiConnector: BuildDeployApiConnector =
      mock[BuildDeployApiConnector]

    val mockTeamsAndReposConnector: TeamsAndRepositoriesConnector =
      mock[TeamsAndRepositoriesConnector]

    val mockCacheRepo: SessionCacheRepository =
      mock[SessionCacheRepository]

    val authStubBehaviour: StubBehaviour =
      mock[StubBehaviour]

    val authComponent: FrontendAuthComponents =
      FrontendAuthComponentsStub(authStubBehaviour)

    def controller: CreateRepositoryController =
      new CreateRepositoryController(
        authComponent,
        mcc,
        app.injector.instanceOf[MongoComponent],
        app.injector.instanceOf[ServicesConfig],
        app.injector.instanceOf[TimestampSupport],
        app.injector.instanceOf[SelectRepoTypePage],
        app.injector.instanceOf[CreateServicePage],
        app.injector.instanceOf[CreatePrototypePage],
        app.injector.instanceOf[CreateTestPage],
        app.injector.instanceOf[CreateExternalPage],
        app.injector.instanceOf[CreateRepositoryConfirmationPage],
        mockBuildDeployApiConnector,
        mockTeamsAndReposConnector
      )(using scala.concurrent.ExecutionContext.global):
        override val cacheRepo: SessionCacheRepository = mockCacheRepo
