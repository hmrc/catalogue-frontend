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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.cataloguefrontend.connector.model.{UserLog, Log}
import uk.gov.hmrc.cataloguefrontend.connector.{TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.prcommenter.PrCommenterConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.cataloguefrontend.search.SearchIndex.searchURIs

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
    val uriSearchTestIndex = List(
      SearchTerm(linkType = "page", name = "page four",  link = "/page/4/sub-page", weight = 0),
      SearchTerm(linkType = "page", name = "page one",   link = "/page/1"         , weight = 0),
      SearchTerm(linkType = "page", name = "page two",   link = "/page/2"         , weight = 0),
      SearchTerm(linkType = "page", name = "page three", link = "/page/3"         , weight = 0),
      SearchTerm(linkType = "page", name = "page four",  link = "/page/4"         , weight = 0)
    )
    
    "filter out paths that are not pages in the index" in {
      val testUserLog = UserLog(userName = "bob.bobber",
        logs = Seq(
          Log(uri = "/page/1",    count = 4),
          Log(uri = "/page/2",    count = 3),
          Log(uri = "/page/5",    count = 2), //non-existent uri
          Log(uri = "/page/4",    count = 1)
        )
      )
      
      val expectedRes = Seq(
        SearchTerm(linkType = "page", name = "page one",   link = "/page/1", weight = 4),
        SearchTerm(linkType = "page", name = "page two",   link = "/page/2", weight = 3),
        SearchTerm(linkType = "page", name = "page four",  link = "/page/4", weight = 1)
      )
      
      val res = searchURIs(testUserLog.logs, uriSearchTestIndex)
      res shouldBe expectedRes
    }
    
    "return SearchTerms in desc order based on visits" in {
       val testUserLog = UserLog(userName = "bob.bobber",
         logs = Seq(
           Log(uri = "/page/1", count = 1),
           Log(uri = "/page/2", count = 23),
           Log(uri = "/page/3", count = 34),
           Log(uri = "/page/4", count = 12),
         )
       )
      
      val expectedRes = Seq(
        SearchTerm(linkType = "page", name = "page three", link = "/page/3", weight = 34),
        SearchTerm(linkType = "page", name = "page two",   link = "/page/2", weight = 23),
        SearchTerm(linkType = "page", name = "page four",  link = "/page/4", weight = 12),
        SearchTerm(linkType = "page", name = "page one",   link = "/page/1", weight = 1)
      )
      
      val res = searchURIs(testUserLog.logs, uriSearchTestIndex)
      res shouldBe expectedRes
    }
    
    "only ignore params when grouping pages, dont group root pages with sub pages" in {
      val testUserLog = UserLog(userName = "bob.bobber",
        logs = Seq(
          Log(uri = "/page/4",              count = 5),
          Log(uri = "/page/4/sub-page",     count = 2),
          Log(uri = "/page/4/sub-page?x=1", count = 1),
        )
      )
      
      val expectedRes = Seq(
        SearchTerm(linkType = "page", name = "page four", link = "/page/4",          weight = 5),
        SearchTerm(linkType = "page", name = "page four", link = "/page/4/sub-page", weight = 3)
      )
      
      val res = searchURIs(testUserLog.logs, uriSearchTestIndex)
      res shouldBe expectedRes
    }
    
    "return correctly ordered SearchTerms based on visits, correctly group and sum visits for those with base uri in common" in {
      val testUserLog = UserLog(userName = "bob.bobber",
        logs = Seq(
          Log(uri = "/page/1",         count = 3),
          Log(uri = "/page/1?x=1&y=0", count = 1),
          Log(uri = "/page/1?x=&y=4",  count = 2),
          Log(uri = "/page/2",         count = 5),
          Log(uri = "/page/2",         count = 5),
          Log(uri = "/page/3",         count = 1),
          Log(uri = "/page/4",         count = 16)
        )
      )
      
      val expectedRes = Seq(
        SearchTerm(linkType = "page", name = "page four",  link = "/page/4", weight = 16),
        SearchTerm(linkType = "page", name = "page two",   link = "/page/2", weight = 10),
        SearchTerm(linkType = "page", name = "page one",   link = "/page/1", weight = 6 ),
        SearchTerm(linkType = "page", name = "page three", link = "/page/3", weight = 1 )
      )
      
      val res = searchURIs(testUserLog.logs, uriSearchTestIndex)
      res shouldBe expectedRes
    }
  }
}
