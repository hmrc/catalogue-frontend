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

import uk.gov.hmrc.cataloguefrontend.model.Environment.{Development, ExternalTest, Production, QA}
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.cataloguefrontend.viewModels.whatsRunningWhere.{EnvironmentWithVersion, EnvironmentWithoutVersion, VersionRow, WhatsRunningWhereViewModel}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{ServiceName, VersionNumber, WhatsRunningWhere, WhatsRunningWhereVersion}

class WhatsRunningWhereViewModelSpec extends UnitSpec {

  "WhatsRunningWhereViewModel" should {

    "convert WhatsRunningWhere and Environments into version rows" in {

      val developmentVersion = WhatsRunningWhereVersion(Development, VersionNumber("1.4.0"), List.empty)
      val qaVersion          = WhatsRunningWhereVersion(QA, VersionNumber("1.3.0"), List.empty)
      val productionVersion  = WhatsRunningWhereVersion(Production, VersionNumber("1.2.0"), List.empty)

      val environments = List(developmentVersion, qaVersion, productionVersion)

      val whatsRunningWhere = Seq(
        WhatsRunningWhere(ServiceName("foo"), environments),
        WhatsRunningWhere(ServiceName("bar"), environments),
        WhatsRunningWhere(ServiceName("baz"), environments)
      )

      val acceptedEnvironments = Seq(Development, QA, ExternalTest)

      val viewModel = WhatsRunningWhereViewModel(
        whatsRunning = whatsRunningWhere,
        environments = acceptedEnvironments
      )

      val expectedEnvironmentType = List(
        EnvironmentWithVersion(Development, developmentVersion),
        EnvironmentWithVersion(QA, qaVersion),
        EnvironmentWithoutVersion(ExternalTest)
      )

      val expectedResult = Seq(
        VersionRow("foo", 0, expectedEnvironmentType),
        VersionRow("bar", 1, expectedEnvironmentType),
        VersionRow("baz", 2, expectedEnvironmentType)
      )

      viewModel.toVersionRow shouldBe expectedResult
    }
  }

}
