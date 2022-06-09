package uk.gov.hmrc.cataloguefrontend.search

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.Seq

class IndexBuilderSpec extends AnyWordSpec with Matchers{

  "optimiseIndex" should {
    "Return the expected index structure" in {
      val testIndex = Seq(
        SearchTerm(linkType = "conf", name = "wrist", link = "/service/wristband/config", weight = 0.5f, Set("repo")),
      )

      IndexBuilder.optimizeIndex(testIndex) shouldBe Map(
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
    SearchTerm(linkType = "timeline", name = "xi-eori-common-component-frontend",          link = "/deployment-timeline?service=xi-eori-common-component-frontend", weight = 0.5f,Set()),
    SearchTerm(linkType = "timeline", name = "verification-questions",                     link = "/deployment-timeline?service=verification-questions",            weight = 0.5f,Set()),
    SearchTerm(linkType = "config",   name = "wristband",                                  link = "/service/wristband/config",                                      weight = 0.5f,Set()),
    SearchTerm(linkType = "config",   name = "verify-your-identity-for-a-trust-frontend",  link = "/service/verify-your-identity-for-a-trust-frontend/config",      weight = 0.5f,Set()),
    SearchTerm(linkType = "health",   name = "voa-api-proxy-performance-tests",            link = "/health-indicators/voa-api-proxy-performance-tests",             weight = 0.5f,Set()),
    SearchTerm(linkType = "health",   name = "vmv-frontend",                               link = "/health-indicators/vmv-frontend",                                weight = 0.5f,Set()),
    SearchTerm(linkType = "leak",     name = "vault-admin-policies",                       link = "/leak-detection/repositories/vault-admin-policies",              weight = 0.5f,Set()),
    SearchTerm(linkType = "leak",     name = "vatvc-scala-dashing",                        link = "/leak-detection/repositories/vatvc-scala-dashing",               weight = 0.5f,Set()),
    SearchTerm(linkType = "Other",    name = "vault-app-config-service-info-parser",       link = "/repositories/vault-app-config-service-info-parser",             weight = 0.5f,Set("repository")),
    SearchTerm(linkType = "Other",    name = "vat-deferral-new-payment-scheme-perf-tests", link = "/repositories/vat-deferral-new-payment-scheme-perf-tests",       weight = 0.5f,Set("repository")),
    SearchTerm(linkType = "Service",  name = "time-to-pay-taxpayer",                       link = "/repositories/time-to-pay-taxpayer",                             weight = 0.5f,Set("repository")),
    SearchTerm(linkType = "Service",  name = "time-based-one-time-password",               link = "/repositories/time-based-one-time-password",                     weight = 0.5f,Set("repository"))
  )

  private val cachedIndex = IndexBuilder.optimizeIndex(index)

  "search" should {
    "return all SearchTerms containing a 3 letter query" in {
      val res = IndexBuilder.search(query = Seq("vau"), index = cachedIndex)
      res shouldBe  Seq(
        SearchTerm(linkType = "leak",  name = "vault-admin-policies",                 link = "/leak-detection/repositories/vault-admin-policies",  weight = 0.5f, hints = Set()),
        SearchTerm(linkType = "Other", name = "vault-app-config-service-info-parser", link = "/repositories/vault-app-config-service-info-parser", weight = 0.5f, hints = Set("repository"))
      )
    }

    "return all SearchTerms containing a 6 letter query" in {
      val res = IndexBuilder.search(query = Seq("entity"), index = cachedIndex)
      res shouldBe Seq(
        SearchTerm(linkType = "config", name = "verify-your-identity-for-a-trust-frontend", link = "/service/verify-your-identity-for-a-trust-frontend/config", weight = 0.5f, hints = Set())
      )
    }

    "return all SearchTerms filtered by a 2 term query, without duplicates" in {
      val res = IndexBuilder.search(query = Seq("tim", "erv"), index = cachedIndex)
      res shouldBe Seq(
        SearchTerm(linkType = "Service",  name = "time-to-pay-taxpayer",         link = "/repositories/time-to-pay-taxpayer",                             weight = 0.5f,Set("repository")),
        SearchTerm(linkType = "Service",  name = "time-based-one-time-password", link = "/repositories/time-based-one-time-password",                     weight = 0.5f,Set("repository"))
      )
    }

    "return all SearchTerms filtered by a 5 term query, without duplicates" in {
      val res = IndexBuilder.search(query = Seq("ver", "you", "ide", "for", "tru"), index = cachedIndex)
      res shouldBe Seq(
        SearchTerm(linkType = "config",   name = "verify-your-identity-for-a-trust-frontend",  link = "/service/verify-your-identity-for-a-trust-frontend/config",      weight = 0.5f,Set())
      )
    }

    "return all SearchTerms containing the query within the 'hints' field" in {
      val res = IndexBuilder.search(query = Seq("rep"), index = cachedIndex)
      res shouldBe Seq(
        SearchTerm(linkType = "Other",    name = "vault-app-config-service-info-parser",       link = "/repositories/vault-app-config-service-info-parser",             weight = 0.5f,Set("repository")),
        SearchTerm(linkType = "Other",    name = "vat-deferral-new-payment-scheme-perf-tests", link = "/repositories/vat-deferral-new-payment-scheme-perf-tests",       weight = 0.5f,Set("repository")),
        SearchTerm(linkType = "Service",  name = "time-to-pay-taxpayer",                       link = "/repositories/time-to-pay-taxpayer",                             weight = 0.5f,Set("repository")),
        SearchTerm(linkType = "Service",  name = "time-based-one-time-password",               link = "/repositories/time-based-one-time-password",                     weight = 0.5f,Set("repository"))
      )
    }

    "return an empty Sequence if no SearchTerms contain the query" in {
      val res = IndexBuilder.search(query = Seq("zzz"), index = cachedIndex)
      res shouldBe Seq.empty
    }
  }
}
