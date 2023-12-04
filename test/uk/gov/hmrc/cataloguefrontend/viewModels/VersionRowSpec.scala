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

package uk.gov.hmrc.cataloguefrontend.viewModels

import uk.gov.hmrc.cataloguefrontend.connector.model.Version
import uk.gov.hmrc.cataloguefrontend.model.Environment.{Development, Production, QA, Staging}
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.cataloguefrontend.viewModels.whatsRunningWhere.{EnvironmentWithVersion, EnvironmentWithoutVersion, VersionRow}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{VersionNumber, WhatsRunningWhereVersion}

class VersionRowSpec extends UnitSpec {

  "VersionRow" should {

    "define version opacity for major versions" in {

      val developmentVersion = WhatsRunningWhereVersion(Development, VersionNumber("4.0.0"), List.empty)
      val qaVersion          = WhatsRunningWhereVersion(QA, VersionNumber("3.0.0"), List.empty)
      val stagingVersion     = WhatsRunningWhereVersion(Staging, VersionNumber("2.0.0"), List.empty)
      val productionVersion  = WhatsRunningWhereVersion(Production, VersionNumber("1.0.0"), List.empty)

      val environments = Seq(
        EnvironmentWithVersion(Development, developmentVersion),
        EnvironmentWithVersion(QA, qaVersion),
        EnvironmentWithVersion(Staging, stagingVersion),
        EnvironmentWithVersion(Production, productionVersion)
      )

      val row = VersionRow("foo", 1, environments)

      row.versionOpacity(developmentVersion.versionNumber.asVersion) shouldBe 0.125
      row.versionOpacity(qaVersion.versionNumber.asVersion)          shouldBe 0.5
      row.versionOpacity(stagingVersion.versionNumber.asVersion)     shouldBe 0.75
      row.versionOpacity(productionVersion.versionNumber.asVersion)  shouldBe 1.0
    }

    "define version opacity for minor versions" in {

      val developmentVersion = WhatsRunningWhereVersion(Development, VersionNumber("1.4.0"), List.empty)
      val qaVersion          = WhatsRunningWhereVersion(QA, VersionNumber("1.3.0"), List.empty)
      val stagingVersion     = WhatsRunningWhereVersion(Staging, VersionNumber("1.2.0"), List.empty)
      val productionVersion  = WhatsRunningWhereVersion(Production, VersionNumber("1.1.0"), List.empty)

      val environments = Seq(
        EnvironmentWithVersion(Development, developmentVersion),
        EnvironmentWithVersion(QA, qaVersion),
        EnvironmentWithVersion(Staging, stagingVersion),
        EnvironmentWithVersion(Production, productionVersion)
      )

      val row = VersionRow("foo", 1, environments)

      row.versionOpacity(developmentVersion.versionNumber.asVersion) shouldBe 0.125
      row.versionOpacity(qaVersion.versionNumber.asVersion)          shouldBe 0.25
      row.versionOpacity(stagingVersion.versionNumber.asVersion)     shouldBe 0.375
      row.versionOpacity(productionVersion.versionNumber.asVersion)  shouldBe 0.5
    }

    "sort versions for environments with versions" in {

      val developmentVersion = WhatsRunningWhereVersion(Development, VersionNumber("1.4.0"), List.empty)
      val qaVersion          = WhatsRunningWhereVersion(QA, VersionNumber("1.3.0"), List.empty)
      val productionVersion  = WhatsRunningWhereVersion(Production, VersionNumber("1.2.0"), List.empty)

      val environments = Seq(
        EnvironmentWithVersion(Development, developmentVersion),
        EnvironmentWithVersion(QA, qaVersion),
        EnvironmentWithoutVersion(Staging),
        EnvironmentWithVersion(Production, productionVersion)
      )

      val row = VersionRow("foo", 1, environments)

      val expectedResult: Seq[Version] = Seq(
        productionVersion.versionNumber.asVersion,
        qaVersion.versionNumber.asVersion,
        developmentVersion.versionNumber.asVersion
      )

      row.sortedVersions shouldBe expectedResult
    }
  }
}
