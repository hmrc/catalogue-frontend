/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.{Instant, LocalDate}

import org.scalatest.{FreeSpec, Matchers}

class DependencySpec extends FreeSpec with Matchers {

  "versionState" - {

    "should return UpToDate if on the latest version" in {
      Dependency("library-abc", Version("1.2.3"), Some(Version("1.2.3"))).versionState shouldBe Some(VersionState.UpToDate)
    }

    "should return MinorVersionOutOfDate if on patch version behind" in {
      Dependency("library-abc", Version("1.2.2"), Some(Version("1.2.3"))).versionState shouldBe Some(
        VersionState.MinorVersionOutOfDate)
    }

    "should return MinorVersionOutOfDate if on minor version behind" in {
      Dependency("library-abc", Version("1.1.3"), Some(Version("1.2.3"))).versionState shouldBe Some(
        VersionState.MinorVersionOutOfDate)
    }

    "should return MajorVersionOutOfDate if on minor version behind" in {
      Dependency("library-abc", Version("1.2.3"), Some(Version("2.2.3"))).versionState shouldBe Some(
        VersionState.MajorVersionOutOfDate)
    }

    "should return BobbyRuleViolated if dependency has any broken bobby rules" in {
      Dependency("library-abc", Version("1.2.3"), Some(Version("2.2.3")),
        Seq(BobbyRuleViolation("banned library", BobbyVersionRange("[1.2.3]"), LocalDate.of(1,1,1)))).versionState shouldBe Some(VersionState.BobbyRuleViolated)
    }

    "should return BobbyRulePending if dependency will break future rules" in {
      new Dependency(
          "library-abc"
        , Version("1.2.3")
        , Some(Version("2.2.3"))
        , Seq(BobbyRuleViolation("banned library", BobbyVersionRange("[1.2.3]"), LocalDate.of(9999,1,1))(now = LocalDate.of(200,1,2)))
        )
        .versionState shouldBe Some(VersionState.BobbyRulePending)
    }

    "should return BobbyRuleViolation if dependency has both pending and active broken rules" in {
      new Dependency(
          "library-abc"
        , Version("1.2.3")
        , Some(Version("2.2.3"))
        , Seq( BobbyRuleViolation("banned library",  BobbyVersionRange("[1.2.3]"), LocalDate.of(9999,1,1))(now = LocalDate.of(2000,1,2))
             , BobbyRuleViolation("banned library",  BobbyVersionRange("[1.2.3]"), LocalDate.of(1,1,1))(now = LocalDate.of(2000,1,2))
             )
        ).versionState shouldBe Some(VersionState.BobbyRuleViolated)
    }

    "should return Invalid if current version is greater than the latest version " in {
      Dependency("library-abc", Version("1.0.1"), Some(Version("1.0.0"))).versionState shouldBe Some(
        VersionState.Invalid)
      Dependency("library-abc", Version("1.1.0"), Some(Version("1.0.0"))).versionState shouldBe Some(
        VersionState.Invalid)
      Dependency("library-abc", Version("2.0.0"), Some(Version("1.0.0"))).versionState shouldBe Some(
        VersionState.Invalid)
    }
  }

  "Dependencies" - {

    "provides a list of only dependencies with active bobby rules" in {
      val badDep = new Dependency(
          "library-abc"
        , Version("1.2.3")
        , Some(Version("2.2.3"))
        , Seq(BobbyRuleViolation("banned library",  BobbyVersionRange("[1.2.3]"), LocalDate.of(1,1,1))(now = LocalDate.of(2000,1,2)))
        )

      val pendingDep = new Dependency(
          "library-xyz"
        , Version("1.2.3")
        , Some(Version("2.2.3"))
        , Seq(BobbyRuleViolation("banned library", BobbyVersionRange("[1.2.3]"), LocalDate.of(9999,1,1))(now = LocalDate.of(2000,1,2)))
        )

      val goodDep = Dependency("library-lol", Version("1.2.3"), Some(Version("2.2.3")))

      val deps = Dependencies(
          repositoryName         = "repo"
        , libraryDependencies    = Seq(badDep, goodDep, pendingDep)
        , sbtPluginsDependencies = Seq()
        , otherDependencies      = Seq()
        , lastUpdated            = Instant.now
        )

      deps.toSeq.filter(_.activeBobbyRuleViolations.nonEmpty) shouldBe Seq(badDep)
    }

    "provides a list of only dependencies with pending bobby rules" in {
      val badDep = new Dependency(
          "library-abc"
        , Version("1.2.3")
        , Some(Version("2.2.3"))
        , Seq(BobbyRuleViolation("banned library", BobbyVersionRange("[1.2.3]"), LocalDate.of(1,1,1))(now = LocalDate.of(2000,1,2)))
        )

      val pendingDep = new Dependency(
          "library-xyz"
        , Version("1.2.3")
        , Some(Version("2.2.3"))
        , Seq(BobbyRuleViolation("banned library", BobbyVersionRange("[1.2.3]"), LocalDate.of(9999,1,1))(now = LocalDate.of(2000,1,2)))
        )

      val goodDep = Dependency("library-lol", Version("1.2.3"), Some(Version("2.2.3")))

      val deps = Dependencies(
          repositoryName         = "repo"
        , libraryDependencies    = Seq(badDep, goodDep, pendingDep)
        , sbtPluginsDependencies = Seq()
        , otherDependencies      = Seq()
        , lastUpdated            = Instant.now
        )

      deps.toSeq.filter(_.pendingBobbyRuleViolations.nonEmpty) shouldBe Seq(pendingDep)
    }
  }
}
