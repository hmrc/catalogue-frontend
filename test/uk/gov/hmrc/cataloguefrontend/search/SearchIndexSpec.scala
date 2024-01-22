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

package uk.gov.hmrc.cataloguefrontend.search

import org.mockito.MockitoSugar.mock
import org.scalatest.concurrent.{ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.model.{UserLog, Log}
import uk.gov.hmrc.cataloguefrontend.connector.{TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.prcommenter.PrCommenterConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.cataloguefrontend.search.SearchIndex.searchURIs


import uk.gov.hmrc.cataloguefrontend.leakdetection.{routes => leakRoutes}
import uk.gov.hmrc.cataloguefrontend.repository.{routes => reposRoutes}
import uk.gov.hmrc.cataloguefrontend.prcommenter.{PrCommenterConnector, routes => prcommenterRoutes}
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{routes => commissioningRoutes}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.{routes => serviceConfigsRoutes}
import uk.gov.hmrc.cataloguefrontend.teams.{routes => teamRoutes}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{routes => wrwRoutes}
import uk.gov.hmrc.cataloguefrontend.deployments.{routes => depRoutes}
import uk.gov.hmrc.cataloguefrontend.{routes => catalogueRoutes}
import uk.gov.hmrc.cataloguefrontend.shuttering.{ShutterType, routes => shutterRoutes}
import uk.gov.hmrc.cataloguefrontend.users.{routes => userRoutes}
import uk.gov.hmrc.cataloguefrontend.createrepository.{routes => createRepoRoutes}

import scala.concurrent.ExecutionContext.Implicits.global


class SearchIndexSpec extends AnyWordSpec with Matchers{
  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
  "optimiseIndex" should {
    "Return the expected index structure" in {
      val testIndex = Seq(
        SearchTerm(linkType = "conf", name = "wrist", link = "/service/wristband/config", weight = 0.5f, Set("repo")),
      )

      SearchIndex.optimizeIndex(testIndex) shouldBe Map(
        "con" -> Seq(SearchTerm(linkType = "conf", name = "wrist", link = "/service/wristband/config", weight = 0.5f, Set("repo"))),
        "onf" -> Seq(SearchTerm(linkType = "conf", name = "wrist", link = "/service/wristband/config", weight = 0.5f, Set("repo"))),
        "wri" -> Seq(SearchTerm(linkType = "conf", name = "wrist", link = "/service/wristband/config", weight = 0.5f, Set("repo"))),
        "ris" -> Seq(SearchTerm(linkType = "conf", name = "wrist", link = "/service/wristband/config", weight = 0.5f, Set("repo"))),
        "ist" -> Seq(SearchTerm(linkType = "conf", name = "wrist", link = "/service/wristband/config", weight = 0.5f, Set("repo"))),
        "rep" -> Seq(SearchTerm(linkType = "conf", name = "wrist", link = "/service/wristband/config", weight = 0.5f, Set("repo"))),
        "epo" -> Seq(SearchTerm(linkType = "conf", name = "wrist", link = "/service/wristband/config", weight = 0.5f, Set("repo")))
      )
    }
  }

  private val index = Seq(
    SearchTerm(linkType = "timeline", name = "PODS File Upload",                           link = "/somethings/pods",                                               weight = 0.5f, Set()),
    SearchTerm(linkType = "timeline", name = "xi-eori-common-component-frontend",          link = "/deployment-timeline?service=xi-eori-common-component-frontend", weight = 0.5f,Set()),
    SearchTerm(linkType = "timeline", name = "verification-questions",                     link = "/deployment-timeline?service=verification-questions",            weight = 0.5f,Set()),
    SearchTerm(linkType = "config",   name = "vmv-frontend",                               link = "/service/wristband/config",                                      weight = 0.5f,Set()),
    SearchTerm(linkType = "config",   name = "verify-your-identity-for-a-trust-frontend",  link = "/service/verify-your-identity-for-a-trust-frontend/config",      weight = 0.5f,Set()),
    SearchTerm(linkType = "health",   name = "voa-api-proxy-performance-tests",            link = "/health-indicators/voa-api-proxy-performance-tests",             weight = 0.5f,Set()),
    SearchTerm(linkType = "health",   name = "vmv-frontend",                               link = "/health-indicators/vmv-frontend",                                weight = 0.5f,Set()),
    SearchTerm(linkType = "leak",     name = "vault-admin-policies",                       link = "/leak-detection/repositories/vault-admin-policies",              weight = 0.5f,Set()),
    SearchTerm(linkType = "leak",     name = "vatvc-scala-dashing",                        link = "/leak-detection/repositories/vatvc-scala-dashing",               weight = 0.5f,Set()),
    SearchTerm(linkType = "Other",    name = "vault-app-config-service-info-parser",       link = "/repositories/vault-app-config-service-info-parser",             weight = 0.5f,Set("repository")),
    SearchTerm(linkType = "Other",    name = "vat-deferral-new-payment-scheme-perf-tests", link = "/repositories/vat-deferral-new-payment-scheme-perf-tests",       weight = 0.5f,Set("repository")),
    SearchTerm(linkType = "Service",  name = "time-to-pay-taxpayer",                       link = "/repositories/time-to-pay-taxpayer",                             weight = 0.5f,Set("repository")),
    SearchTerm(linkType = "Service",  name = "time-based-one-time-password",               link = "/repositories/time-based-one-time-password",                     weight = 0.5f,Set("repository")),
    SearchTerm(linkType = "health",   name = "voa",                                        link = "/health-indicators/voa-api-proxy-performance-tests",             weight = 0.5f,Set())
  )
  
  private val uriIndex = List(
    SearchTerm(linkType = "explorer", name = "dependency",                   link = catalogueRoutes.DependencyExplorerController.landing.url,                           1.0f, Set("depex")),
    SearchTerm(linkType = "explorer", name = "bobby",                        link = catalogueRoutes.BobbyExplorerController.list().url,                                 1.0f),
    SearchTerm(linkType = "explorer", name = "jvm",                          link = catalogueRoutes.JDKVersionController.compareAllEnvironments().url,                  1.0f, Set("jdk", "jre")),
    SearchTerm(linkType = "explorer", name = "leaks",                        link = leakRoutes.LeakDetectionController.ruleSummaries.url,                               1.0f, Set("lds")),
    SearchTerm(linkType = "page",     name = "whatsrunningwhere",            link = wrwRoutes.WhatsRunningWhereController.releases().url,                               1.0f, Set("wrw")),
    SearchTerm(linkType = "page",     name = "deployment",                   link = depRoutes.DeploymentEventsController.deploymentEvents(Environment.Production).url,  1.0f),
    SearchTerm(linkType = "page",     name = "shutter-overview",             link = shutterRoutes.ShutterOverviewController.allStates(ShutterType.Frontend).url,        1.0f),
    SearchTerm(linkType = "page",     name = "shutter-api",                  link = shutterRoutes.ShutterOverviewController.allStates(ShutterType.Api).url,             1.0f),
    SearchTerm(linkType = "page",     name = "shutter-rate",                 link = shutterRoutes.ShutterOverviewController.allStates(ShutterType.Rate).url,            1.0f),
    SearchTerm(linkType = "page",     name = "shutter-events",               link = shutterRoutes.ShutterEventsController.shutterEvents.url,                            1.0f),
    SearchTerm(linkType = "page",     name = "teams",                        link = teamRoutes.TeamsController.allTeams().url,                                          1.0f),
    SearchTerm(linkType = "page",     name = "repositories",                 link = reposRoutes.RepositoriesController.allRepositories().url,                           1.0f),
    SearchTerm(linkType = "page",     name = "users",                        link = userRoutes.UsersController.allUsers().url,                                          1.0f),
    SearchTerm(linkType = "page",     name = "defaultbranch",                link = catalogueRoutes.CatalogueController.allDefaultBranches().url,                       1.0f),
    SearchTerm(linkType = "page",     name = "pr-commenter-recommendations", link = prcommenterRoutes.PrCommenterController.recommendations().url,                      1.0f),
    SearchTerm(linkType = "page",     name = "search config",                link = serviceConfigsRoutes.ServiceConfigsController.searchLanding().url,                  1.0f),
    SearchTerm(linkType = "page",     name = "config warnings",              link = serviceConfigsRoutes.ServiceConfigsController.configWarningLanding().url,           1.0f),
    SearchTerm(linkType = "page",     name = "create service repository",    link = createRepoRoutes.CreateRepositoryController.createServiceRepositoryLanding().url,   1.0f),
    SearchTerm(linkType = "page",     name = "create prototype repository",  link = createRepoRoutes.CreateRepositoryController.createPrototypeRepositoryLanding().url, 1.0f),
    SearchTerm(linkType = "page",     name = "create test repository",       link = createRepoRoutes.CreateRepositoryController.createTestRepository().url,             1.0f),
    SearchTerm(linkType = "page",     name = "deploy service",               link = depRoutes.DeployServiceController.step1(None).url,                                  1.0f),
    SearchTerm(linkType = "page",     name = "search commissioning state",   link = commissioningRoutes.ServiceCommissioningStatusController.searchLanding().url,       1.0f))
  
  private val testUserLog = new
    UserLog(userName = "bob.bobber",
      logs = Seq(
        new Log(page = "/sign-out"                                                     , visitCounter = 9),
        new Log(page = "/users?username=&team=Platops"                                 , visitCounter = 5),
        new Log(page = "/create-app-configs?serviceName=platops-test-frontend"         , visitCounter = 8),
        new Log(page = "/whats-running-where"                                          , visitCounter = 22),
        new Log(page = "/deploy-service/1"                                             , visitCounter = 12)
      )
    )
      
  val userLogTestRes = Seq(
    SearchTerm(linkType = "explorer",     name = "dependency", link = "/whats-running-where",           1.0f, Set("depex")),
    SearchTerm(linkType = "explorer",     name = "dependency", link = "/deploy-service/1",              1.0f, Set("depex")),
    SearchTerm(linkType = "explorer",     name = "dependency", link = "/users?username=&team=Platops",  1.0f, Set("depex"))
  )

  private val mockTeamsAndRepositoriesConnector = mock[TeamsAndRepositoriesConnector]
  private val mockPrCommenterConnector          = mock[PrCommenterConnector]
  private val mockUserManagementConnector       = mock[UserManagementConnector]
  private val testIndex = new SearchIndex(mockTeamsAndRepositoriesConnector, mockPrCommenterConnector, mockUserManagementConnector)

  //Populate index with mock data
  testIndex.cachedIndex.set(SearchIndex.optimizeIndex(index))

  "search" should {
    "return all SearchTerms containing a 3 letter query" in {
      val res = testIndex.search(query = Seq("vau"))
      res shouldBe  Seq(
        SearchTerm(linkType = "leak",  name = "vault-admin-policies",                 link = "/leak-detection/repositories/vault-admin-policies",  weight = 0.5f, hints = Set()),
        SearchTerm(linkType = "Other", name = "vault-app-config-service-info-parser", link = "/repositories/vault-app-config-service-info-parser", weight = 0.5f, hints = Set("repository"))
      )
    }

    "return all SearchTerms containing a 6 letter query" in {
      val res = testIndex.search(query = Seq("entity"))
      res shouldBe Seq(
        SearchTerm(linkType = "config", name = "verify-your-identity-for-a-trust-frontend", link = "/service/verify-your-identity-for-a-trust-frontend/config", weight = 0.5f, hints = Set())
      )
    }

    "return all SearchTerms filtered by a 2 term query, without duplicates" in {
      val res = testIndex.search(query = Seq("tim", "erv"))
      res shouldBe Seq(
        SearchTerm(linkType = "Service",  name = "time-based-one-time-password", link = "/repositories/time-based-one-time-password",                     weight = 0.5f,Set("repository")),
        SearchTerm(linkType = "Service",  name = "time-to-pay-taxpayer",         link = "/repositories/time-to-pay-taxpayer",                             weight = 0.5f,Set("repository"))
      )
    }

    "return all SearchTerms filtered by a 5 term query, without duplicates" in {
      val res = testIndex.search(query = Seq("ver", "you", "ide", "for", "tru"))
      res shouldBe Seq(
        SearchTerm(linkType = "config",   name = "verify-your-identity-for-a-trust-frontend",  link = "/service/verify-your-identity-for-a-trust-frontend/config",      weight = 0.5f,Set())
      )
    }

    "return all SearchTerms containing the query within the 'hints' field" in {
      val res = testIndex.search(query = Seq("rep"))
      res shouldBe Seq(
        SearchTerm(linkType = "Service",  name = "time-based-one-time-password",               link = "/repositories/time-based-one-time-password",                     weight = 0.5f,Set("repository")),
        SearchTerm(linkType = "Service",  name = "time-to-pay-taxpayer",                       link = "/repositories/time-to-pay-taxpayer",                             weight = 0.5f,Set("repository")),
        SearchTerm(linkType = "Other",    name = "vat-deferral-new-payment-scheme-perf-tests", link = "/repositories/vat-deferral-new-payment-scheme-perf-tests",       weight = 0.5f,Set("repository")),
        SearchTerm(linkType = "Other",    name = "vault-app-config-service-info-parser",       link = "/repositories/vault-app-config-service-info-parser",             weight = 0.5f,Set("repository"))
      )
    }

    "return an empty Sequence if no SearchTerms contain the query" in {
      val res = testIndex.search(query = Seq("zzz"))
      res shouldBe Seq.empty
    }

    "return SearchTerms belonging to the same service next to one another, and in alphabetical order" in {
      val res = testIndex.search(query = Seq("fro"))
      res shouldBe Seq(
        SearchTerm(linkType = "config",   name = "verify-your-identity-for-a-trust-frontend",  link = "/service/verify-your-identity-for-a-trust-frontend/config",      weight = 0.5f,Set()),
        SearchTerm(linkType = "config",   name = "vmv-frontend",                               link = "/service/wristband/config",                                      weight = 0.5f,Set()),
        SearchTerm(linkType = "health",   name = "vmv-frontend",                               link = "/health-indicators/vmv-frontend",                                weight = 0.5f,Set()),
        SearchTerm(linkType = "timeline", name = "xi-eori-common-component-frontend",          link = "/deployment-timeline?service=xi-eori-common-component-frontend", weight = 0.5f,Set())
      )
    }

    "return an exact match first (with increased weighting)" in {
      val res = testIndex.search(query = Seq("voa"))
      res shouldBe Seq(
        SearchTerm(linkType = "health",   name = "voa",                                        link = "/health-indicators/voa-api-proxy-performance-tests",             weight = 1.0f,Set()),
        SearchTerm(linkType = "health",   name = "voa-api-proxy-performance-tests",            link = "/health-indicators/voa-api-proxy-performance-tests",             weight = 0.5f,Set())
      )
    }

    "Be case insensitive" in {
      val res1 = testIndex.search(query = Seq("pods"))
      val res2 = testIndex.search(query = Seq("PODS"))
      val res3 = testIndex.search(query = Seq("oad"))
      val res4 = testIndex.search(query = Seq("OAD"))
      List(res1, res2, res3, res4).foreach(_ shouldBe Seq(
        SearchTerm(linkType = "timeline", name = "PODS File Upload", link = "/somethings/pods", weight = 0.5f, Set())
      ))
    }
  }
  
  "searchURIs" should {
    "return SearchTerms in order that are exact matches to the URIs" in {
      val res = searchURIs(testUserLog.logs, uriIndex)
      print(s"$res")
      res shouldBe userLogTestRes
    }
  }
}
