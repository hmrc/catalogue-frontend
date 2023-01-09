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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

class WhatsRunningWhereControllerSpec extends UnitSpec{
  "matchesProduction" should {
    val versionNumberProd    = VersionNumber("0.0.0")
    val versionNumberNotProd = VersionNumber("1.1.1")

    val wrwVersionProd                  = WhatsRunningWhereVersion(Environment.Production, versionNumberProd   )
    val wrwVersionStagingMatchesProd    = WhatsRunningWhereVersion(Environment.Staging   , versionNumberProd   )
    val wrwVersionStagingNotMatchesProd = WhatsRunningWhereVersion(Environment.Staging   , versionNumberNotProd)

    val wrwMatchesProd    = WhatsRunningWhere(ServiceName("foo"), List(wrwVersionProd, wrwVersionStagingMatchesProd))
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
