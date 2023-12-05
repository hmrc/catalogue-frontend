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

import uk.gov.hmrc.cataloguefrontend.model.Environment.Development
import uk.gov.hmrc.cataloguefrontend.connector.model.Version
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec
import uk.gov.hmrc.cataloguefrontend.viewModels.whatsRunningWhere.EnvironmentWithVersion
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{VersionNumber, WhatsRunningWhereVersion}

class EnvironmentFieldSpec extends UnitSpec {

  "EnvironmentVersion" should {
    def version(number: String) = WhatsRunningWhereVersion(Development, VersionNumber(number), List.empty)

    "create tooltip content for major versions" in {

      val latestVersion: Version = Version("3.0.0")

      val environmentWithVersion1Behind = EnvironmentWithVersion(Development, version("2.0.0"))
      val environmentWithVersion2Behind = EnvironmentWithVersion(Development, version("1.0.0"))

      val expectedResultPlural    = "<div>1.0.0 is 2 <strong>major</strong> versions behind latest 3.0.0</div>"
      val expectedResultSingular  = "<div>2.0.0 is 1 <strong>major</strong> version behind latest 3.0.0</div>"

      environmentWithVersion1Behind.toolTipContent(latestVersion) shouldBe expectedResultSingular
      environmentWithVersion2Behind.toolTipContent(latestVersion) shouldBe expectedResultPlural
    }

    "create tooltip content for minor versions" in {

      val latestVersion: Version = Version("1.3.0")

      val environmentWithVersion1Behind = EnvironmentWithVersion(Development, version("1.2.0"))
      val environmentWithVersion2Behind = EnvironmentWithVersion(Development, version("1.1.0"))

      val expectedResultPlural = "<div>1.1.0 is 2 <strong>minor</strong> versions behind latest 1.3.0</div>"
      val expectedResultSingular = "<div>1.2.0 is 1 <strong>minor</strong> version behind latest 1.3.0</div>"

      environmentWithVersion1Behind.toolTipContent(latestVersion) shouldBe expectedResultSingular
      environmentWithVersion2Behind.toolTipContent(latestVersion) shouldBe expectedResultPlural
    }

    "create tooltip content for patch versions ahead" in {

      val latestVersion: Version = Version("1.1.0")

      val environmentWithVersion1Ahead = EnvironmentWithVersion(Development, version("1.1.1"))
      val environmentWithVersion2Ahead = EnvironmentWithVersion(Development, version("1.1.2"))

      val expectedResultPlural   = "<div>1.1.2 is 2 <strong>patch/hotfix</strong> versions ahead latest 1.1.0</div>"
      val expectedResultSingular = "<div>1.1.1 is 1 <strong>patch/hotfix</strong> version ahead latest 1.1.0</div>"

      environmentWithVersion1Ahead.toolTipContent(latestVersion) shouldBe expectedResultSingular
      environmentWithVersion2Ahead.toolTipContent(latestVersion) shouldBe expectedResultPlural
    }

    "create tooltip content for patch versions behind" in {

      val latestVersionSingular: Version = Version("1.1.1")
      val latestVersionPlural: Version = Version("1.1.2")

      val environmentWithVersion = EnvironmentWithVersion(Development, version("1.1.0"))

      val expectedResultPlural = "<div>1.1.0 is 2 versions behind latest <strong>patch/hotfix</strong> version 1.1.2</div>"
      val expectedResultSingular = "<div>1.1.0 is 1 version behind latest <strong>patch/hotfix</strong> version 1.1.1</div>"

      environmentWithVersion.toolTipContent(latestVersionSingular) shouldBe expectedResultSingular
      environmentWithVersion.toolTipContent(latestVersionPlural) shouldBe expectedResultPlural
    }
  }

}
