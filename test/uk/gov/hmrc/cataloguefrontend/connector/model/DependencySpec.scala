/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.connector.model

import org.scalatest.{FreeSpec, Matchers}

class DependencySpec extends FreeSpec with Matchers {

  "getVersionState" - {

    "should return UpToDate if on the latest version" in {
      Dependency("library-abc", Version(1, 2, 3), Some(Version(1, 2, 3))).getVersionState shouldBe Some(UpToDate)
    }

    "should return MinorVersionOutOfDate if on patch version behind" in {
      Dependency("library-abc", Version(1, 2, 2), Some(Version(1, 2, 3))).getVersionState shouldBe Some(
        MinorVersionOutOfDate)
    }

    "should return MinorVersionOutOfDate if on minor version behind" in {
      Dependency("library-abc", Version(1, 1, 3), Some(Version(1, 2, 3))).getVersionState shouldBe Some(
        MinorVersionOutOfDate)
    }

    "should return MajorVersionOutOfDate if on minor version behind" in {
      Dependency("library-abc", Version(1, 2, 3), Some(Version(2, 2, 3))).getVersionState shouldBe Some(
        MajorVersionOutOfDate)
    }

    "should return InvalidVersionState if current version is greater than the latest version " in {

      Dependency("library-abc", Version(1, 0, 1), Some(Version(1, 0, 0))).getVersionState shouldBe Some(
        InvalidVersionState)
      Dependency("library-abc", Version(1, 1, 0), Some(Version(1, 0, 0))).getVersionState shouldBe Some(
        InvalidVersionState)
      Dependency("library-abc", Version(2, 0, 0), Some(Version(1, 0, 0))).getVersionState shouldBe Some(
        InvalidVersionState)
    }

  }

  "Version" - {

    "should include suffix when converted to a string" in {
      Version(1,2,3,Some("play-26")).toString should be ("1.2.3-play-26")
    }

    "should omit suffix the suffix completely when not present" in {
      Version(1,2,3).toString should be ("1.2.3")
    }

  }
}
