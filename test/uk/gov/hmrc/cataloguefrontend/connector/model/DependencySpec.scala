/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.LocalDate

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DependencySpec extends AnyWordSpec with Matchers {

  "versionState" should {
    "return None if on the latest version" in {
      Dependency(
        name           = "library-abc",
        group          = "uk.gov.hmrc",
        currentVersion = Version("1.2.3"),
        latestVersion  = Some(Version("1.2.3"))
      ).versionState shouldBe None
    }

    "return None if version is ahead" in {
      Dependency(
        name           = "library-abc",
        group          = "uk.gov.hmrc",
        currentVersion = Version("1.4.7"),
        latestVersion  = Some(Version("1.3.13"))
      ).versionState shouldBe None
    }

    "return NewVersionAvailable if on patch version behind" in {
      Dependency(
        name           = "library-abc",
        group          = "uk.gov.hmrc",
        currentVersion = Version("1.2.2"),
        latestVersion  = Some(Version("1.2.3"))
      ).versionState shouldBe Some(VersionState.NewVersionAvailable)
    }

    "return NewVersionAvailable if on minor version behind" in {
      Dependency(
        name           = "library-abc",
        group          = "uk.gov.hmrc",
        currentVersion = Version("1.1.3"),
        latestVersion  = Some(Version("1.2.3"))
      ).versionState shouldBe Some(VersionState.NewVersionAvailable)
    }

    "return NewVersionAvailable if on major version behind" in {
      Dependency(
        name           = "library-abc",
        group          = "uk.gov.hmrc",
        currentVersion = Version("1.2.3"),
        latestVersion  = Some(Version("2.2.3"))
      ).versionState shouldBe Some(VersionState.NewVersionAvailable)
    }

    val activeViolation = BobbyRuleViolation("banned library",  BobbyVersionRange("[1.2.3]"), LocalDate.of(1,1,1))(now = LocalDate.of(2000,1,2))
    val pendingViolation = BobbyRuleViolation("banned library",  BobbyVersionRange("[1.2.3]"), LocalDate.of(9999,1,1))(now = LocalDate.of(2000,1,2))

    "return BobbyRuleViolated if dependency has any broken bobby rules" in {
      Dependency(
        name                = "library-abc",
        group               = "uk.gov.hmrc",
        currentVersion      = Version("1.2.3"),
        latestVersion       = Some(Version("2.2.3")),
        bobbyRuleViolations = Seq(activeViolation)
      ).versionState shouldBe Some(VersionState.BobbyRuleViolated(activeViolation))
    }

    "return BobbyRulePending if dependency will break future rules" in {
      Dependency(
        name                = "library-abc",
        group               = "uk.gov.hmrc",
        currentVersion      = Version("1.2.3"),
        latestVersion       = Some(Version("2.2.3")),
        bobbyRuleViolations = Seq(pendingViolation)
      ).versionState shouldBe Some(VersionState.BobbyRulePending(pendingViolation))
    }

    "return BobbyRuleViolation if dependency has both pending and active broken rules" in {
      Dependency(
        name                = "library-abc",
        group               = "uk.gov.hmrc",
        currentVersion      = Version("1.2.3"),
        latestVersion       = Some(Version("2.2.3")),
        bobbyRuleViolations = Seq(pendingViolation, activeViolation)
      ).versionState shouldBe Some(VersionState.BobbyRuleViolated(activeViolation))
    }
  }

  "Dependencies" should {
    "provide a list of only dependencies with active bobby rules" in {
      val badDep = Dependency(
          "library-abc"
        , "uk.gov.hmrc"
        , Version("1.2.3")
        , Some(Version("2.2.3"))
        , Seq(BobbyRuleViolation("banned library",  BobbyVersionRange("[1.2.3]"), LocalDate.of(1,1,1))(now = LocalDate.of(2000,1,2)))
        )

      val pendingDep = Dependency(
        name                = "xyz",
        group               = "uk.gov.hmrc",
        currentVersion      = Version("1.2.3"),
        latestVersion       = Some(Version("2.2.3")),
        bobbyRuleViolations = Seq(BobbyRuleViolation("banned library", BobbyVersionRange("[1.2.3]"), LocalDate.of(9999,1,1))(now = LocalDate.of(2000,1,2)))
        )

      val goodDep = Dependency("library-lol", "uk.gov.hmrc", Version("1.2.3"), Some(Version("2.2.3")))

      val deps = Dependencies(
          repositoryName         = "repo",
          libraryDependencies    = Seq(badDep, goodDep, pendingDep),
          sbtPluginsDependencies = Seq(),
          otherDependencies      = Seq()
        )

      deps.toDependencySeq.filter(_.activeBobbyRuleViolations.nonEmpty) shouldBe Seq(badDep)
    }

    "provide a list of only dependencies with pending bobby rules" in {
      val badDep = Dependency(
          name                = "library-abc",
          group               = "uk.gov.hmrc",
          currentVersion      = Version("1.2.3"),
          latestVersion       = Some(Version("2.2.3")),
          bobbyRuleViolations = Seq(BobbyRuleViolation("banned library", BobbyVersionRange("[1.2.3]"), LocalDate.of(1,1,1))(now = LocalDate.of(2000,1,2)))
        )

      val pendingDep = Dependency(
          name                = "library-xyz",
          group               = "uk.gov.hmrc",
          currentVersion      = Version("1.2.3"),
          latestVersion       = Some(Version("2.2.3")),
          bobbyRuleViolations = Seq(BobbyRuleViolation("banned library", BobbyVersionRange("[1.2.3]"), LocalDate.of(9999,1,1))(now = LocalDate.of(2000,1,2)))
        )

      val goodDep = Dependency(
          name                = "library-lol",
          group               = "uk.gov.hmrc",
          currentVersion      = Version("1.2.3"),
          latestVersion       = Some(Version("2.2.3"))
        )

      val deps = Dependencies(
          repositoryName         = "repo",
          libraryDependencies    = Seq(badDep, goodDep, pendingDep),
          sbtPluginsDependencies = Seq(),
          otherDependencies      = Seq()
        )

      deps.toDependencySeq.filter(_.pendingBobbyRuleViolations.nonEmpty) shouldBe Seq(pendingDep)
    }
  }
}
