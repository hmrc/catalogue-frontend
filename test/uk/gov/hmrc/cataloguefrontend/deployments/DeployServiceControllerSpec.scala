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
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, Helpers}
import uk.gov.hmrc.cataloguefrontend.connector.{GitHubProxyConnector, GitRepository, Organisation, RepoType, ServiceDependenciesConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.connector.model.{BobbyRuleViolation, Dependency, DependencyScope, Kind, RepositoryModule, RepositoryModules, Vendor}
import uk.gov.hmrc.cataloguefrontend.deployments.view.html.{DeployServicePage, DeployServiceStep4Page}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, SlugInfoFlag, TeamName, Version, VersionRange}
import uk.gov.hmrc.cataloguefrontend.service.{ServiceDependencies, ServiceJdkVersion}
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{Check, ServiceCommissioningStatusConnector}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.{ConfigChange, ConfigChanges, ServiceConfigsService}
import uk.gov.hmrc.cataloguefrontend.util.TelemetryLinks
import uk.gov.hmrc.cataloguefrontend.vulnerabilities.{CurationStatus, DistinctVulnerability, VulnerabilitiesConnector, VulnerabilitySummary}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{ReleasesConnector, WhatsRunningWhere, WhatsRunningWhereVersion}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.internalauth.client.{Predicate, Resource, ResourceLocation, ResourceType, Retrieval}
import uk.gov.hmrc.internalauth.client.syntax.*
import uk.gov.hmrc.internalauth.client.test.{FrontendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl

import java.time.{Instant, LocalDate}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeployServiceControllerSpec
  extends AnyWordSpec
    with MockitoSugar
    with Matchers
    with GuiceOneAppPerSuite
    with DefaultAwaitTimeout
    with OptionValues {

  "Deploy Service Page step1" should {
    "allow a service to be specified" in new Setup {
      when(mockTeamsAndRepositoriesConnector.allRepositories(
        name               = any
      , team               = any
      , digitalService     = any
      , archived           = eqTo(Some(false))
      , repoType           = eqTo(Some(RepoType.Service))
      , serviceType        = any
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(allServices))
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
      when(mockTeamsAndRepositoriesConnector.allRepositories(
        name               = any
      , team               = any
      , digitalService     = any
      , archived           = eqTo(Some(false))
      , repoType           = eqTo(Some(RepoType.Service))
      , serviceType        = any
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(allServices))
      // This gets called twice
      // Matching on retrieval ANY since the type is erased and the mocks get confused
      when(mockAuthStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Any]]))
        .thenReturn(
          Future.successful(Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("services/some-service")))),
          Future.successful(true)
        )
      when(mockServiceDependenciesConnector.getSlugInfo(ServiceName(eqTo("some-service")), any)(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someSlugInfo)))
      when(mockReleasesConnector.releasesForService(ServiceName(eqTo("some-service")))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(someReleasesForService))
      when(mockServiceCommissioningConnector.commissioningStatus(ServiceName(eqTo("some-service")))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(someCommissioningStatus))

      val futResult = underTest.step1(Some(ServiceName("some-service")))(FakeRequest().withSession(SessionKeys.authToken -> "Token token"))

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
      when(mockTeamsAndRepositoriesConnector.allRepositories(
        name               = any
      , team               = any
      , digitalService     = any
      , archived           = eqTo(Some(false))
      , repoType           = eqTo(Some(RepoType.Service))
      , serviceType        = any
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(allServices))
      // This gets called twice
      // Matching on retrieval ANY since the type is erased and the mocks get confused
      when(mockAuthStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Any]]))
        .thenReturn(
          Future.successful(Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("services/some-service")))),
          Future.successful(true)
        )

      when(mockGitHubProxyConnector.compare(eqTo("some-service"), eqTo(Version("0.2.0")), eqTo(Version("0.3.0")))(using any[HeaderCarrier]))
        .thenReturn(Future.failed(RuntimeException("Some error calling github")): Future[GitHubProxyConnector.Compare] )
      when(mockServiceDependenciesConnector.getSlugInfo(ServiceName(eqTo("some-service")), any)(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someSlugInfo)))
      when(mockReleasesConnector.releasesForService(ServiceName(eqTo("some-service")))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(someReleasesForService))
      when(mockServiceCommissioningConnector.commissioningStatus(ServiceName(eqTo("some-service")))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(someCommissioningStatus))
      when(mockServiceDependenciesConnector.getSlugInfo(ServiceName(eqTo("some-service")), eqTo(Some(Version("0.2.0"))))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someSlugInfo.copy(version = Version("0.2.0")))))
      when(mockServiceDependenciesConnector.getSlugInfo(ServiceName(eqTo("some-service")), eqTo(Some(Version("0.3.0"))))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someSlugInfo)))
      when(mockServiceConfigsService.configChangesNextDeployment(ServiceName(eqTo("some-service")), eqTo(Environment.QA), eqTo(Version("0.3.0")))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(someConfigChanges))
      when(mockServiceConfigsService.configWarnings(
        ServiceName(eqTo("some-service")),
        eqTo(List(Environment.QA)),
        eqTo(Some(Version("0.3.0"))),
        eqTo(true)
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(someConfigWarning)))
      when(mockVulnerabilitiesConnector.vulnerabilitySummaries(
        flag           = any[Option[SlugInfoFlag]],
        serviceQuery   = eqTo(Some("some-service")),
        version        = eqTo(Some(Version("0.3.0"))),
        team           = any[Option[TeamName]],
        curationStatus = eqTo(Some(CurationStatus.ActionRequired))
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(Seq(someVulnerabilities))))
      when(mockServiceDependenciesConnector.getRepositoryModules(
        repositoryName = eqTo("some-service"),
        version = eqTo(Version("0.3.0"))
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(violations)))

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

      jsoupDocument.select("#config-updates-rows").first.children.size shouldBe 3
      jsoupDocument.select("#config-warnings-rows").first.children.size shouldBe 1
      jsoupDocument.select("#vulnerabilities-rows").first.children.size shouldBe 2 // has a collapse tr too
      jsoupDocument.select("#deployment-config-updates-rows").first.children.size shouldBe 1
      jsoupDocument.select("div.row.dependency-row").size shouldBe 1
      jsoupDocument.select("#deploy-btn").size shouldBe 1
    }
  }

  "Deploy Service Page step3" should {
    "deploy service" in new Setup {
      when(mockTeamsAndRepositoriesConnector.allRepositories(
        name               = any
      , team               = any
      , digitalService     = any
      , archived           = eqTo(Some(false))
      , repoType           = eqTo(Some(RepoType.Service))
      , serviceType        = any
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful(allServices))
      // This gets called twice
      // Matching on retrieval ANY since the type is erased and the mocks get confused
      when(mockAuthStubBehaviour.stubAuth(any[Option[Predicate.Permission]], any[Retrieval[Any]]))
        .thenReturn(
          Future.successful(
            Retrieval.Username("some-user") ~
              Set(Resource(ResourceType("catalogue-frontend"), ResourceLocation("services/some-service")))
          ),
          Future.successful(true)
        )
      when(mockServiceDependenciesConnector.getSlugInfo(ServiceName(eqTo("some-service")), eqTo(Some(Version("0.3.0"))))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someSlugInfo)))
      when(mockBuildJobsConnector.deployMicroservice(
        serviceName = ServiceName(eqTo("some-service")),
        version     = eqTo(Version("0.3.0")),
        environment = eqTo(Environment.QA),
        user        = Retrieval.Username(eqTo("some-user"))
      )(using any[HeaderCarrier]))
        .thenReturn(Future.successful("http://localhost:8461/some/queue/url"))

      val futResult = underTest.step3()(
        FakeRequest()
          .withMethod(Helpers.POST)
          .withSession(SessionKeys.authToken -> "Token token")
          .withFormUrlEncodedBody("serviceName" -> "some-service", "version" -> "0.3.0", "environment" -> "qa")
      )

      Helpers.status(futResult) shouldBe 303
      Helpers.redirectLocation(futResult) shouldBe Some(routes.DeployServiceController.step4(
        serviceName = ServiceName("some-service")
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
      when(mockServiceDependenciesConnector.getSlugInfo(ServiceName(eqTo("some-service")), eqTo(Some(Version("0.3.0"))))(using any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(someSlugInfo)))

      val futResult = underTest.step4(
        serviceName = ServiceName("") // taken from query params via form
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
  , organisation   = Some(Organisation.Mdtp)
  , description    = "some-description"
  , githubUrl      = "some-github-url"
  , createdDate    = Instant.now
  , lastActiveDate = Instant.now
  , language       = Some("some-language")
  , isArchived     = false
  , defaultBranch  = "some-default-branch"
  )

  private val allServices = Seq(
    someService,
    someService.copy(name = "some-service-2")
  )

  private val someSlugInfo = ServiceDependencies(
    uri           = "some-uri"
  , name          = "some-service"
  , version       = Version("0.3.0")
  , runnerVersion = "some-runner-version"
  , java          = ServiceJdkVersion("", Vendor.OpenJDK, Kind.JRE)
  , classpath     = "some-classpath"
  , dependencies  = Nil
  )

  private val someConfigChanges = ConfigChanges(
    app               = ConfigChanges.App(from = Some(Version("0.2.0")), to = Version("0.3.0"))
  , base              = ConfigChanges.BaseConfigChange(                                     from = Some(ConfigChanges.CommitId("abcdef")), to = Some(ConfigChanges.CommitId("ghijkl")), githubUrl = "some-url")
  , common            = ConfigChanges.CommonConfigChange(                                   from = Some(ConfigChanges.CommitId("abcdef")), to = Some(ConfigChanges.CommitId("ghijkl")), githubUrl = "some-url")
  , env               = ConfigChanges.EnvironmentConfigChange(environment = Environment.QA, from = Some(ConfigChanges.CommitId("abcdef")), to = Some(ConfigChanges.CommitId("ghijkl")), githubUrl = "some-url")
  , configChanges     = Map(
                          KeyName("key1") -> ConfigChange(from = None                                                        , to = Some(ConfigSourceValue("some-source", None, "some-value1")))
                        , KeyName("key2") -> ConfigChange(from = Some(ConfigSourceValue("some-source", None, "some-value2a")), to = Some(ConfigSourceValue("some-source", None, "some-value2b")))
                        , KeyName("key3") -> ConfigChange(from = Some(ConfigSourceValue("some-source", None, "some-value3")) , to = None)
                        )
  , deploymentChanges = Map(
                          KeyName("instances") -> ConfigChange(from = Some(ConfigSourceValue("some-source", None, "1")), to = Some(ConfigSourceValue("some-source", None, "2")))
                        )
  )

  private val someReleasesForService = WhatsRunningWhere(
    serviceName = ServiceName("some-service")
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
                            , summary                    = "summary"
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

  private val violations = RepositoryModules(
    "some-service",
    Some(Version("0.3.0")),
    Seq.empty,
    Seq(
      RepositoryModule(
        "some-service",
        "some-group",
        Seq(
          Dependency(
            "some-dependency",
            "some-group",
            Version("0.0.1"),
            None,
            Seq(
              BobbyRuleViolation(
                "some-reason",
                VersionRange(
                  None,
                  None,
                  None,
                  "0.0.1 <= 0.0.2"
                ),
                LocalDate.MIN
              )
            ),
            Seq.empty,
            None,
            DependencyScope.Compile
          )
        ),
        Seq.empty,
        Seq.empty,
        Seq.empty,
        Seq.empty,
        Seq.empty,
        Seq.empty
      )
    )
  )

  private trait Setup {
    given mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

    val mockAuthStubBehaviour             = mock[StubBehaviour]
    val mockBuildJobsConnector            = mock[BuildJobsConnector]
    val mockTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
    val mockServiceDependenciesConnector  = mock[ServiceDependenciesConnector]
    val mockServiceCommissioningConnector = mock[ServiceCommissioningStatusConnector]
    val mockReleasesConnector             = mock[ReleasesConnector]
    val mockVulnerabilitiesConnector      = mock[VulnerabilitiesConnector]
    val mockServiceConfigsService         = mock[ServiceConfigsService]
    val mockGitHubProxyConnector          = mock[GitHubProxyConnector]
    val underTest                         = DeployServiceController(
                                              auth                          = FrontendAuthComponentsStub(mockAuthStubBehaviour)
                                            , mcc                           = mcc
                                            , configuration                 = app.injector.instanceOf[Configuration]
                                            , buildJobsConnector            = mockBuildJobsConnector
                                            , teamsAndRepositoriesConnector = mockTeamsAndRepositoriesConnector
                                            , serviceDependenciesConnector  = mockServiceDependenciesConnector
                                            , serviceCommissioningConnector = mockServiceCommissioningConnector
                                            , releasesConnector             = mockReleasesConnector
                                            , vulnerabilitiesConnector      = mockVulnerabilitiesConnector
                                            , gitHubProxyConnector          = mockGitHubProxyConnector
                                            , serviceConfigsService         = mockServiceConfigsService
                                            , telemetryLinks                = TelemetryLinks(app.injector.instanceOf[Configuration])
                                            , deployServicePage             = app.injector.instanceOf[DeployServicePage]
                                            , deployServiceStep4Page        = app.injector.instanceOf[DeployServiceStep4Page]
                                            )
    extension (eventualResult: Future[Result])
      def toDocument: Document =
        Jsoup.parse(Helpers.contentAsString(eventualResult))
  }
}
