package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

import java.time.Instant


class WhatsRunningWhereControllerSpec extends UnitSpec{
  "matchesProduction" should {
    val timeSeen = TimeSeen(Instant.now)

    val versionNumberProd= VersionNumber("0.0.0")
    val versionNumberNotProd = VersionNumber("1.1.1")

    val wrwVersionProd = WhatsRunningWhereVersion(Environment.Production, Platform.ECS, versionNumberProd, timeSeen)
    val wrwVersionStagingMatchesProd = WhatsRunningWhereVersion(Environment.Staging, Platform.ECS, versionNumberProd, timeSeen)
    val wrwVersionStagingNotMatchesProd = WhatsRunningWhereVersion(Environment.Staging, Platform.ECS, versionNumberNotProd, timeSeen)

    val wrwMatchesProd = WhatsRunningWhere(ServiceName("foo"), List(wrwVersionProd, wrwVersionStagingMatchesProd))
    val wrwNotMatchesProd = WhatsRunningWhere(ServiceName("foo"), List(wrwVersionProd, wrwVersionStagingNotMatchesProd))

    "return true when compared env version matches production version" in {
      WhatsRunningWhereController.matchesProduction(wrwMatchesProd, wrwVersionProd, Environment.Staging) shouldBe true
    }

    "return false when compared env version does not match production version" in {
      WhatsRunningWhereController.matchesProduction(wrwNotMatchesProd, wrwVersionProd, Environment.Staging) shouldBe false
    }

    "return false when compared env version is missing" in {
      WhatsRunningWhereController.matchesProduction(wrwMatchesProd, wrwVersionProd, Environment.QA) shouldBe false
    }
  }
}
