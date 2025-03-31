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

package uk.gov.hmrc.cataloguefrontend.createappconfigs

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.json._
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.{POST, contentAsString, defaultAwaitTimeout, redirectLocation, status}
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.AsyncRequestId
import uk.gov.hmrc.cataloguefrontend.connector._
import uk.gov.hmrc.cataloguefrontend.connector.model.{Kind, Vendor}
import uk.gov.hmrc.cataloguefrontend.createappconfigs.view.html.CreateAppConfigsPage
import uk.gov.hmrc.cataloguefrontend.model.{ServiceName, Version}
import uk.gov.hmrc.cataloguefrontend.service.{ServiceDependencies, ServiceJdkVersion}
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{Check, ServiceCommissioningStatusConnector, routes}
import uk.gov.hmrc.cataloguefrontend.test.FakeApplicationBuilder
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.internalauth.client._

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CreateAppConfigsControllerSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with FakeApplicationBuilder
     with ScalaFutures {

  override def beforeEach(): Unit =
    super.beforeEach()
    setupAuthEndpoint()

  "CreateAppConfigsController" should {
    "have the correct url setup" in new Setup {
      uk.gov.hmrc.cataloguefrontend.createappconfigs.routes.CreateAppConfigsController
        .createAppConfigsLanding(serviceName).url shouldBe "/create-app-configs?serviceName=test-service"
    }

    "return permission with correct resource type and action" in new Setup {
      controller.createAppConfigsPermission(serviceName) shouldBe
        Permission(
          Resource(
            ResourceType("catalogue-frontend"),
            ResourceLocation("services/test-service")
          ),
          IAAction("CREATE_APP_CONFIGS")
        )
    }
  }

  "CreateAppConfigsController.createAppConfigsLanding" should {
    "return 200 when user is authenticated" in new Setup {
      when(authStubBehaviour.stubAuth(eqTo(None), any[Retrieval[Boolean]]))
        .thenReturn(Future.successful(true))

      when(mockTRConnector.repositoryDetails(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(gitRepository)))

      when(mockSCSConnector.commissioningStatus(any[ServiceName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(List.empty[Check]))

      val result = controller
        .createAppConfigsLanding(serviceName)(
          FakeRequest()
            .withSession(SessionKeys.authToken -> "Token token")
        )

      status(result) shouldBe 200
    }

    "return 200 with global form error - when user does not have permission to create app configs for the service" in new Setup {
      when(authStubBehaviour.stubAuth(eqTo(None), any[Retrieval[Boolean]]))
        .thenReturn(Future.successful(false))

      when(mockTRConnector.repositoryDetails(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(gitRepository)))

      when(mockSCSConnector.commissioningStatus(any[ServiceName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(List.empty[Check]))

      val result = controller
        .createAppConfigsLanding(serviceName)(
          FakeRequest()
            .withSession(SessionKeys.authToken -> "Token token")
        )

      status(result) shouldBe 200
      contentAsString(result) should include(s"You do not have permission to create App Configs")
    }

    "return 404 when service name is not found" in new Setup {
      when(authStubBehaviour.stubAuth(eqTo(None), any[Retrieval[Boolean]]))
        .thenReturn(Future.successful(true))

      when(mockTRConnector.repositoryDetails(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      val result = controller
        .createAppConfigsLanding(serviceName)(
          FakeRequest()
            .withSession(SessionKeys.authToken -> "Token token")
        )

      status(result) shouldBe 404
    }

    "return 404 when service has no service type" in new Setup {
      when(authStubBehaviour.stubAuth(eqTo(None), any[Retrieval[Boolean]]))
        .thenReturn(Future.successful(true))

      when(mockTRConnector.repositoryDetails(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(gitRepository.copy(serviceType = None))))

      val result = controller
        .createAppConfigsLanding(serviceName)(
          FakeRequest()
            .withSession(SessionKeys.authToken -> "Token token")
        )

      status(result) shouldBe 404
    }
  }

  "CreateAppConfigsController.createAppConfigs" should {
    "redirect to the service commissioning page when the form is submitted successfully" in new Setup {
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      when(mockTRConnector.repositoryDetails(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(gitRepository)))

      when(mockSCSConnector.commissioningStatus(any[ServiceName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(List.empty[Check]))

      when(mockSDConnector.getSlugInfo(any[ServiceName], any[Option[Version]])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(serviceDependenciesContainsMongo)))

      when(mockBDConnector.createAppConfigs(
        eqTo(form.copy(appConfigBase = true)),
        ServiceName(eqTo(serviceName.asString)),
        eqTo(ServiceType.Backend),
        requiresMongo = eqTo(true),
        isApi         = eqTo(false)
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(asyncRequestIdResponse)))

      val result = controller
        .createAppConfigs(serviceName)(
          FakeRequest(POST, "/create-app-configs")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody("appConfigBase" -> "true")
        )

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.ServiceCommissioningStatusController.getCommissioningState(serviceName).url)
    }

    "redirect when service requires mongo but slug info is not found" in new Setup {
      val appDependenciesIncludesMongo = Some("mongo")

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      when(mockTRConnector.repositoryDetails(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(gitRepository)))

      when(mockSCSConnector.commissioningStatus(any[ServiceName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(List.empty[Check]))

      when(mockSDConnector.getSlugInfo(any[ServiceName], any[Option[Version]])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      when(mockGHConnector.getGitHubProxyRaw(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(appDependenciesIncludesMongo))

      when(mockBDConnector.createAppConfigs(
        eqTo(form.copy(appConfigBase = true)),
        ServiceName(eqTo(serviceName.asString)),
        eqTo(ServiceType.Backend),
        requiresMongo = eqTo(true),
        isApi         = eqTo(false)
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(asyncRequestIdResponse)))

      val result = controller
        .createAppConfigs(serviceName)(
          FakeRequest(POST, "/create-app-configs")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody("appConfigBase" -> "true")
        )

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.ServiceCommissioningStatusController.getCommissioningState(serviceName).url)
    }

    "redirect when service does not require mongo" in new Setup {
      val noMongoDependencies = ""

      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      when(mockTRConnector.repositoryDetails(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(gitRepository)))

      when(mockSCSConnector.commissioningStatus(any[ServiceName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(List.empty[Check]))

      when(mockSDConnector.getSlugInfo(any[ServiceName], any[Option[Version]])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      when(mockGHConnector.getGitHubProxyRaw(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(noMongoDependencies)))

      when(mockBDConnector.createAppConfigs(
        eqTo(form.copy(appConfigBase = true)),
        ServiceName(eqTo(serviceName.asString)),
        eqTo(ServiceType.Backend),
        requiresMongo = eqTo(false),
        isApi         = eqTo(false)
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(asyncRequestIdResponse)))

      val result = controller
        .createAppConfigs(serviceName)(
          FakeRequest(POST, "/create-app-configs")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody("appConfigBase" -> "true")
        )

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some(routes.ServiceCommissioningStatusController.getCommissioningState(serviceName).url)
    }

    "return 400 when the form is submitted with errors" in new Setup {
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      when(mockTRConnector.repositoryDetails(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(gitRepository)))

      when(mockSCSConnector.commissioningStatus(any[ServiceName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(List.empty[Check]))

      when(mockBDConnector.createAppConfigs(any[CreateAppConfigsForm], any[ServiceName], any[ServiceType], any[Boolean], any[Boolean])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(asyncRequestIdResponse)))

      val result = controller
        .createAppConfigs(serviceName)(
          FakeRequest(POST, "/create-app-configs")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody("appConfigBase" -> "notBoolean")
        )

      contentAsString(result) should include("error.boolean")
      status(result) shouldBe 400
    }

    "return 400 when form is submitted with no configs selected" in new Setup {
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      when(mockTRConnector.repositoryDetails(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(gitRepository)))

      when(mockSCSConnector.commissioningStatus(any[ServiceName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(List.empty[Check]))

      when(mockBDConnector.createAppConfigs(any[CreateAppConfigsForm], any[ServiceName], any[ServiceType], any[Boolean], any[Boolean])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(asyncRequestIdResponse)))

      val result = controller
        .createAppConfigs(serviceName)(
          FakeRequest(POST, "/create-app-configs")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody("" -> "")
        )

      contentAsString(result) should include("No update requested")
      status(result) shouldBe 400
    }

    "return 404 when service name is not found" in new Setup {
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[String]]))
        .thenReturn(Future.successful("test-service"))

      when(mockTRConnector.repositoryDetails(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      val result = controller
        .createAppConfigs(serviceName)(
          FakeRequest()
            .withSession(SessionKeys.authToken -> "Token token")
        )

      status(result) shouldBe 404
    }

    "return 500 when service has no service type" in new Setup {
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[String]]))
        .thenReturn(Future.successful("test-service"))

      when(mockTRConnector.repositoryDetails(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(gitRepository.copy(serviceType = None))))

      val result = controller
        .createAppConfigs(serviceName)(
          FakeRequest()
            .withSession(SessionKeys.authToken -> "Token token")
        )

      status(result) shouldBe 500
    }

    "return 500 when POST to build and deploy api fails" in new Setup {
      when(authStubBehaviour.stubAuth(any[Option[Predicate.Permission]], eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.unit)

      when(mockTRConnector.repositoryDetails(any[String])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(gitRepository)))

      when(mockSCSConnector.commissioningStatus(any[ServiceName])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(List.empty[Check]))

      when(mockSDConnector.getSlugInfo(any[ServiceName], any[Option[Version]])(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(serviceDependenciesContainsMongo)))

      when(mockBDConnector.createAppConfigs(
        eqTo(form.copy(appConfigBase = true)),
        ServiceName(eqTo(serviceName.asString)),
        eqTo(ServiceType.Backend),
        requiresMongo = eqTo(true),
        isApi         = eqTo(false)
       )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Left("Failed to connect with request id: 123")))

      val result = controller
        .createAppConfigs(serviceName)(
          FakeRequest(POST, "/create-app-configs")
            .withSession(SessionKeys.authToken -> "Token token")
            .withFormUrlEncodedBody("appConfigBase" -> "true")
        )

      status(result) shouldBe 500
      contentAsString(result) should include(s"Failed to connect with request id: 123")
    }
  }

  private trait Setup {
    private val config = Configuration(
      "environmentsToHideByDefault" -> List("integration", "development")
    )

    given HeaderCarrier = HeaderCarrier()

    given mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

    val mockCACView       = app.injector.instanceOf[CreateAppConfigsPage]
    val mockTRConnector   = mock[TeamsAndRepositoriesConnector]
    val mockGHConnector   = mock[GitHubProxyConnector]
    val mockBDConnector   = mock[BuildDeployApiConnector]
    val mockSCSConnector  = mock[ServiceCommissioningStatusConnector]
    val mockSDConnector   = mock[ServiceDependenciesConnector]
    val authStubBehaviour = mock[StubBehaviour]
    val authComponent     = FrontendAuthComponentsStub(authStubBehaviour)
    val controller        = CreateAppConfigsController(
                              auth                                = authComponent,
                              mcc                                 = mcc,
                              createAppConfigsPage                = mockCACView,
                              buildDeployApiConnector             = mockBDConnector,
                              teamsAndRepositoriesConnector       = mockTRConnector,
                              gitHubProxyConnector                = mockGHConnector,
                              serviceCommissioningStatusConnector = mockSCSConnector,
                              serviceDependenciesConnector        = mockSDConnector,
                              configuration                       = config
                            )

    val serviceName = ServiceName("test-service")

    val gitRepository =
      GitRepository(
        name           = serviceName.asString,
        organisation   = Some(Organisation.Mdtp),
        description    = "test",
        githubUrl      = "test",
        createdDate    = Instant.now(),
        lastActiveDate = Instant.now(),
        language       = Some("en"),
        isArchived     = false,
        defaultBranch  = "test",
        serviceType    = Some(ServiceType.Backend)
      )

    val serviceDependenciesContainsMongo =
      ServiceDependencies(
        uri                  = "test",
        name                 = serviceName.asString,
        version              = Version(1, 1, 1, ""),
        runnerVersion        = "",
        java                 = ServiceJdkVersion("", Vendor.OpenJDK, Kind.JRE),
        classpath            = "",
        dependencies         = Seq.empty,
        dependencyDotCompile = Some(""" "test" "hmrc-mongo" """)
      )

    val form: CreateAppConfigsForm =
      CreateAppConfigsForm(
        appConfigBase        = false,
        appConfigDevelopment = false,
        appConfigQA          = false,
        appConfigStaging     = false,
        appConfigProduction  = false
    )

    val asyncRequestIdResponse = AsyncRequestId(JsObject(Seq(
      "start_timestamp_milliseconds" -> JsNumber(1234L),
      "bnd_api_request_id"           -> JsString("1234")
    )))
  }
}
