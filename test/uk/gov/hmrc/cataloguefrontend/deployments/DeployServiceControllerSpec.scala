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

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, Helpers}
import uk.gov.hmrc.cataloguefrontend.connector.model.Version
import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, ServiceDependenciesConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.{ServiceDependencies, ServiceJDKVersion}
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{Check, ServiceCommissioningStatusConnector}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsService
import uk.gov.hmrc.cataloguefrontend.util.TelemetryLinks
import uk.gov.hmrc.cataloguefrontend.vulnerabilities.{CurationStatus, DistinctVulnerability, VulnerabilitiesConnector, VulnerabilitySummary}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{ReleasesConnector, WhatsRunningWhere, WhatsRunningWhereVersion}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.internalauth.client.{Predicate, Resource, ResourceLocation, ResourceType, Retrieval}
import uk.gov.hmrc.internalauth.client.syntax._
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import views.html.deployments.{DeployServicePage, DeployServiceStep4Page}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeployServiceControllerSpec
  extends AnyWordSpec
  with MockitoSugar
  with ArgumentMatchersSugar
  with Matchers
  with GuiceOneAppPerSuite
  with DefaultAwaitTimeout
  with OptionValues {

  "Deploy Service Page step1" should {
    "allow a service to be specified" in new Setup {
      when(mockAuthStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Set[Resource]]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("services/some-service")))))

      val futResult = underTest.step1(None)(FakeRequest().withSession(SessionKeys.authToken -> "Token token"))

      Helpers.status(futResult) shouldBe Helpers.OK
      val jsoupDocument = futResult.toDocument
      jsoupDocument.select("h1").text() shouldBe "Deploy Service"
      jsoupDocument.select("#service-name-form").select("#service-name").attr("value") shouldBe ""
      jsoupDocument.select("#create-app-configs-submit-button").size shouldBe 0
    }

    "allow a service to be provided" in new Setup {

      // This gets called twice
      // Matching on retrieval ANY since the type is erased and the mocks get confused
      when(mockAuthStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Any]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("services/some-service")))))
        .andThen(Future.successful(true))
      when(mockServiceDependenciesConnector.getSlugInfo(eqTo("some-service"), eqTo(None))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someSlugInfo)))
      when(mockReleasesConnector.releasesForService(eqTo("some-service"))(any[HeaderCarrier]))
        .thenReturn(Future.successful(someReleasesForService))
      when(mockServiceCommissioningConnector.commissioningStatus(eqTo("some-service"))(any[HeaderCarrier]))
        .thenReturn(Future.successful(someCommissioningStatus))

      val futResult = underTest.step1(Some("some-service"))(FakeRequest().withSession(SessionKeys.authToken -> "Token token"))

      Helpers.status(futResult) shouldBe Helpers.OK
      val jsoupDocument = futResult.toDocument
      jsoupDocument.select("h1").text() shouldBe "Deploy Service"
      jsoupDocument.select("#service-name-form").select("#service-name").attr("value") shouldBe "some-service"
      jsoupDocument.select("#version-environment-form").select("#helpful-versions").first.child(0).attr("data-content") shouldBe "0.3.0" // Latest
      jsoupDocument.select("#version-environment-form").select("#helpful-versions").first.child(1).attr("data-content") shouldBe "0.2.0" // QA
      jsoupDocument.select("#version-environment-form").select("#helpful-versions").first.child(2).attr("data-content") shouldBe "0.1.0" // Production
      jsoupDocument.select("#version-environment-form").select("#service-name").attr("version") shouldBe ""
      jsoupDocument.select("#version-environment-form").select("#service-name").attr("environment") shouldBe ""
      jsoupDocument.select("#create-app-configs-submit-button").size shouldBe 0
    }
  }

  import ServiceConfigsService._
  "Deploy Service Page step2" should {
    "help evaluate deployment" in new Setup {
      // This gets called twice
      // Matching on retrieval ANY since the type is erased and the mocks get confused
      when(mockAuthStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Any]]))
        .thenReturn(Future.successful(Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("services/some-service")))))
        .andThen(Future.successful(true))
      when(mockServiceDependenciesConnector.getSlugInfo(eqTo("some-service"), eqTo(None))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someSlugInfo)))
      when(mockReleasesConnector.releasesForService(eqTo("some-service"))(any[HeaderCarrier]))
        .thenReturn(Future.successful(someReleasesForService))
      when(mockServiceCommissioningConnector.commissioningStatus(eqTo("some-service"))(any[HeaderCarrier]))
        .thenReturn(Future.successful(someCommissioningStatus))
      when(mockServiceDependenciesConnector.getSlugInfo(eqTo("some-service"), eqTo(Some(Version("0.3.0"))))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someSlugInfo)))
      when(mockServiceConfigsService.configByKeyWithNextDeployment(eqTo("some-service"), eqTo(Seq(Environment.QA)), eqTo(Some(Version("0.3.0"))))(any[HeaderCarrier]))
        .thenReturn(Future.successful(someConfigByKeyWithNextDeployment))
      when(mockServiceConfigsService.removedConfig(eqTo("some-service"), eqTo(Seq(Environment.QA)), eqTo(Some(Version("0.3.0"))))(any[HeaderCarrier]))
        .thenReturn(Future.successful(someRemoveConfig))
      when(mockServiceConfigsService.configWarnings(eqTo(ServiceName("some-service")), eqTo(List(Environment.QA)), eqTo(Some(Version("0.3.0"))), eqTo(true))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(someConfigWarning)))
      when(mockVulnerabilitiesConnector.vulnerabilitySummaries(eqTo(None), eqTo(Some(CurationStatus.ActionRequired)), eqTo(Some("some-service")), eqTo(Some(Version("0.3.0"))), eqTo(None), eqTo(None))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(someVulnerabilities)))

      val futResult = underTest.step2()(
        FakeRequest()
          .withMethod(Helpers.POST)
          .withSession(SessionKeys.authToken -> "Token token")
          .withFormUrlEncodedBody("serviceName" -> "some-service", "version" -> "0.3.0", "environment" -> "qa")
      )

      Helpers.status(futResult) shouldBe Helpers.OK
      val jsoupDocument = futResult.toDocument
      jsoupDocument.select("h1").text() shouldBe "Deploy Service"
      jsoupDocument.select("#service-name-form").select("#service-name").attr("value") shouldBe "some-service"
      jsoupDocument.select("#version-environment-form").select("#helpful-versions").first.child(0).attr("data-content") shouldBe "0.3.0" // Latest
      jsoupDocument.select("#version-environment-form").select("#helpful-versions").first.child(1).attr("data-content") shouldBe "0.2.0" // QA
      jsoupDocument.select("#version-environment-form").select("#helpful-versions").first.child(2).attr("data-content") shouldBe "0.1.0" // Production
      jsoupDocument.select("#version-environment-form").select("#service-name").attr("version") shouldBe ""
      jsoupDocument.select("#version-environment-form").select("#service-name").attr("environment") shouldBe ""

      jsoupDocument.select("#config-updates-rows").first.children.size shouldBe 2
      jsoupDocument.select("#config-warnings-rows").first.children.size shouldBe 1
      jsoupDocument.select("#vulnerabilities-rows").first.children.size shouldBe 2 // has a collapse tr too
      jsoupDocument.select("#deploy-btn").size shouldBe 1
    }
  }

  "Deploy Service Page step3" should {
    "deploy service" in new Setup {

      // This gets called twice
      // Matching on retrieval ANY since the type is erased and the mocks get confused
      when(mockAuthStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Any]]))
        .thenReturn(Future.successful(
            Retrieval.Username("some-user") ~
              Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("services/some-service")))
        ))
        .andThen(Future.successful(true))

      when(mockTeamsAndRepositoriesConnector.allServices()(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(someService)))
      when(mockServiceDependenciesConnector.getSlugInfo(eqTo("some-service"), eqTo(Some(Version("0.3.0"))))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someSlugInfo)))
      when(mockBuildJobsConnector.deployMicroservice(eqTo("some-service"), eqTo(Version("0.3.0")), eqTo(Environment.QA), eqTo("some-user") )(any[HeaderCarrier]))
        .thenReturn(Future.successful("http://localhost:8461/some/queue/url"))

      val futResult = underTest.step3()(
        FakeRequest()
          .withMethod(Helpers.POST)
          .withSession(SessionKeys.authToken -> "Token token")
          .withFormUrlEncodedBody("serviceName" -> "some-service", "version" -> "0.3.0", "environment" -> "qa")
      )

      Helpers.status(futResult) shouldBe 303
      Helpers.redirectLocation(futResult) shouldBe Some(routes.DeployServiceController.step4(
        serviceName = "some-service"
      , version     = "0.3.0"
      , environment = "qa"
      , queueUrl    = RedirectUrl("http://localhost:8461/some/queue/url")
      , buildUrl    = None).url
      )
    }
  }

  "Deploy Service Page step4" should {
    "watch deployment progress" in new Setup {
      when(mockAuthStubBehaviour.stubAuth(eqTo(None), any[Retrieval[Boolean]]))
        .thenReturn(Future.successful(true))
      when(mockServiceDependenciesConnector.getSlugInfo(eqTo("some-service"), eqTo(Some(Version("0.3.0"))))(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someSlugInfo)))

      val futResult = underTest.step4(
        serviceName = "" // taken from query params via form
      , version     = "" // same as above
      , environment = "" // same as above
      , queueUrl    = RedirectUrl("http://localhost:8461/some/queue/url")
      , buildUrl    = None
      )(FakeRequest(Helpers.GET, "/deploy-service/4?serviceName=some-service&version=0.3.0&environment=qa")
          .withSession(SessionKeys.authToken -> "Token token")
      )

      Helpers.status(futResult) shouldBe Helpers.OK
      val jsoupDocument = futResult.toDocument
      jsoupDocument.select("h1").text() shouldBe "Deploying Service"
    }
  }

  private val someService = GitRepository(
    name           = "some-service"
  , description    = "some-description"
  , githubUrl      = "some-github-url"
  , createdDate    = Instant.now
  , lastActiveDate = Instant.now
  , language       = Some("some-language")
  , isArchived     = false
  , defaultBranch  = "some-default-branch"
  )

  private val someSlugInfo = ServiceDependencies(
     uri           = "some-uri"
  , name          = "some-service"
  , version       = Version("0.3.0")
  , runnerVersion = "some-runner-version"
  , java          = ServiceJDKVersion("", "", "")
  , classpath     = "some-classpath"
  , dependencies  = Nil
  )

  private val someConfigByKeyWithNextDeployment: Map[KeyName, Map[ConfigEnvironment, Seq[(ConfigSourceValue, Boolean)]]] = Map(
    KeyName("key1") -> Map(ConfigEnvironment.ForEnvironment(Environment.QA) -> Seq((ConfigSourceValue("some-source", Some("some-url"), "some-value1") -> true)))
  , KeyName("key2") -> Map(ConfigEnvironment.ForEnvironment(Environment.QA) -> Seq((ConfigSourceValue("some-source", Some("some-url"), "some-value2") -> false)))
  )

  private val someRemoveConfig: Map[KeyName, Map[ConfigEnvironment, Seq[(ConfigSourceValue, Boolean)]]] = Map(
    KeyName("key3") -> Map(ConfigEnvironment.ForEnvironment(Environment.QA) -> Seq((ConfigSourceValue("some-source", Some("some-url"), "some-value3") -> true)))
  )

  private val someReleasesForService = WhatsRunningWhere(
    serviceName = uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.ServiceName("some-service")
  , versions    = WhatsRunningWhereVersion(Environment.QA        , Version("0.2.0"), Nil) ::
                  WhatsRunningWhereVersion(Environment.Production, Version("0.1.0"), Nil) ::
                  Nil
  )

  private val someCommissioningStatus = List(Check.EnvCheck(
    title        = "App Config Environment"
  , checkResults = Map(
                    Environment.QA         -> Right(Check.Present("some-evidence-link"))
                  , Environment.Production -> Right(Check.Present("some-evidence-link"))
                  )
  , helpText     = "some-help-text"
  , linkToDocs   = Some("some-link-to-docs")
  ))

  private val someConfigWarning = ConfigWarning(
    serviceName = ServiceName("some-service")
  , environment = Environment.QA
  , key         = KeyName("key1")
  , value       = ConfigSourceValue("some-source", Some("some-url"), "some-value1" )
  , warning     = "some-warning"
  )

  private val someVulnerabilities = VulnerabilitySummary(
    distinctVulnerability = DistinctVulnerability(
                              vulnerableComponentName    = "some-service"
                            , vulnerableComponentVersion = "0.3.0"
                            , vulnerableComponents       = Nil
                            , id                         = "some-id"
                            , score                      = None
                            , description                = "some-description"
                            , fixedVersions              = None
                            , references                 = Seq("some-reference")
                            , publishedDate              = Instant.now
                            , firstDetected              = Some(Instant.now)
                            , assessment                 = None
                            , curationStatus             = None
                            , ticket                     = None
                            )
  , occurrences           = Nil
  , teams                 = Seq("some-team")
  )

  private trait Setup {
    implicit val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

    val mockAuthStubBehaviour             = mock[StubBehaviour]
    val mockBuildJobsConnector            = mock[BuildJobsConnector]
    val mockTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    val mockServiceDependenciesConnector  = mock[ServiceDependenciesConnector]
    val mockServiceCommissioningConnector = mock[ServiceCommissioningStatusConnector]
    val mockReleasesConnector             = mock[ReleasesConnector]
    val mockVulnerabilitiesConnector      = mock[VulnerabilitiesConnector]
    val mockServiceConfigsService         = mock[ServiceConfigsService]
    val underTest                         = new DeployServiceController(
                                              auth                          = FrontendAuthComponentsStub(mockAuthStubBehaviour)
                                            , mcc                           = mcc
                                            , configuration                 = app.injector.instanceOf[Configuration]
                                            , buildJobsConnector            = mockBuildJobsConnector
                                            , serviceDependenciesConnector  = mockServiceDependenciesConnector
                                            , serviceCommissioningConnector = mockServiceCommissioningConnector
                                            , releasesConnector             = mockReleasesConnector
                                            , vulnerabilitiesConnector      = mockVulnerabilitiesConnector
                                            , serviceConfigsService         = mockServiceConfigsService
                                            , telemetryLinks                = new TelemetryLinks(app.injector.instanceOf[Configuration])
                                            , deployServicePage             = app.injector.instanceOf[DeployServicePage]
                                            , deployServiceStep4Page        = app.injector.instanceOf[DeployServiceStep4Page]
                                            )
    implicit class ResultOps(eventualResult: Future[Result]) {
      lazy val toDocument: Document = Jsoup.parse(Helpers.contentAsString(eventualResult))
    }
  }
}
