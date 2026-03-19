/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.createteam

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues.*
import uk.gov.hmrc.cataloguefrontend.teams.CreateTeamConstraints
import play.api.data.validation.*

class CreateTeamFormSpec 
  extends AnyWordSpec 
    with Matchers:

  "CreateTeamConstraints" should:
    "return true when the team name length is 30 characters" in:
      isValid(validLength)("a" * 30) shouldBe true

    "return true when the team name length is than 30 characters" in:
      isValid(validLength)("a" * 29) shouldBe true

    "return false when the team name length is greater than 30 characters" in:
      isValid(validLength)("a" * 31) shouldBe false

    "return false when the team name is empty" in:
      isValid(nonEmptyValidation)("") shouldBe false

    "return true when team name has valid chars" in:
      isValid(validChars)("Team Name - One") shouldBe true
      isValid(validChars)("Team Name-One"  ) shouldBe true
      isValid(validChars)("team_name_one"  ) shouldBe true
      isValid(validChars)("Team123"        ) shouldBe true
      isValid(validChars)("Team-Name_1"    ) shouldBe true
      isValid(validChars)("A B C"          ) shouldBe true
      isValid(validChars)("Team Name-One"  ) shouldBe true

    "return false when team name has invalid chars" in:
      isValid(validChars)("Team @ Name" ) shouldBe false
      isValid(validChars)("Team#Name"   ) shouldBe false
      isValid(validChars)("Team ! Name-") shouldBe false
      isValid(validChars)("Team %Name"  ) shouldBe false

    "return false when team name has valid hyphen placement" in:
      isValid(validHyphenPlacement)("Team - Name"      ) shouldBe true
      isValid(validHyphenPlacement)("Team - Name - One") shouldBe true
      isValid(validHyphenPlacement)("Team-Name"        ) shouldBe true
      isValid(validHyphenPlacement)("Team-Name-One"    ) shouldBe true

    "return false when team name has invalid hyphen placement" in:
      isValid(validHyphenPlacement)("Team--Name") shouldBe false
      isValid(validHyphenPlacement)("-TeamName" ) shouldBe false
      isValid(validHyphenPlacement)("TeamName-" ) shouldBe false

    "return true when team name has valid hyphen spacing" in:
      isValid(validHyphenSpacing)("Team - Name"      ) shouldBe true
      isValid(validHyphenSpacing)("Team - Name - One") shouldBe true
      isValid(validHyphenSpacing)("Team-Name"        ) shouldBe true
      isValid(validHyphenSpacing)("Team-Name-One"    ) shouldBe true

    "return false when team name has invalid hyphen spacing" in:
      isValid(validHyphenSpacing)("Team -Name") shouldBe false
      isValid(validHyphenSpacing)("Team- Name") shouldBe false

  private def isValid(constraintName: String)(value: String): Boolean =
    CreateTeamConstraints.teamNameConstraints
      .find(_.name.contains(constraintName))
      .value(value) == Valid

  private lazy val validLength          = "constraints.teamNameLengthCheck"
  private lazy val nonEmptyValidation   = "constraints.nonEmptyTeamNameCheck"
  private lazy val validChars           = "constraints.teamNameCharsCheck"
  private lazy val validHyphenPlacement = "constraints.teamNameHyphenPlacementCheck"
  private lazy val validHyphenSpacing   = "constraints.teamNameHyphenSpacingCheck"
