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

package uk.gov.hmrc.cataloguefrontend

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementPortalConnector.TeamMember

class DisplayableTeamMembersSpec extends AnyFunSpec with Matchers with TypeCheckedTripleEquals {

  describe("DisplayTeamMembers") {

    val teamName = TeamName("teamA")

    val teamMembers = Seq(
      TeamMember(
        displayName     = Some("E Federer"),
        familyName      = Some("Federer"),
        givenName       = Some("E"),
        primaryEmail    = Some("e.federer@digital.hmrc.gov.uk"),
        username        = Some("e.federer"),
        role            = Some("user")
      ),
      TeamMember(
        displayName     = Some("B Olapade"),
        familyName      = Some("Olapade"),
        givenName       = Some("B"),
        primaryEmail    = Some("b.olapade@digital.hmrc.gov.uk"),
        username        = Some("b.olapade"),
        role            = Some("user")
      ),
      TeamMember(
        displayName     = Some("D Doe"),
        familyName      = Some("Doe"),
        givenName       = Some("D"),
        primaryEmail    = Some("d.doe@digital.hmrc.gov.uk"),
        username        = Some("d.doe"),
        role            = Some("user")
      ),
      TeamMember(
        displayName     = Some("A Mouse"),
        familyName      = Some("Mouse"),
        givenName       = Some("A"),
        primaryEmail    = Some("a.mouse@digital.hmrc.gov.uk"),
        username        = Some("a.mouse"),
        role            = Some("user")
      ),
      TeamMember(
        displayName     = Some("C Bourne"),
        familyName      = Some("Bourne"),
        givenName       = Some("C"),
        primaryEmail    = Some("c.bourne@digital.hmrc.gov.uk"),
        username        = Some("c.bourne"),
        role            = Some("user")
      )
    )

    val expectedDisplayTeamMembers = Seq(
      DisplayableTeamMember(
        displayName    = "A Mouse",
        umpLink        = "http://example.com/profile/a.mouse",
        role           = Some("user")
      ),
      DisplayableTeamMember(
        displayName    = "B Olapade",
        umpLink        = "http://example.com/profile/b.olapade",
        role           = Some("user")
      ),
      DisplayableTeamMember(
        displayName    = "C Bourne",
        umpLink        = "http://example.com/profile/c.bourne",
        role           = Some("user")
      ),
      DisplayableTeamMember(
        displayName    = "D Doe",
        umpLink        = "http://example.com/profile/d.doe",
        role           = Some("user")
      ),
      DisplayableTeamMember(
        displayName    = "E Federer",
        umpLink        = "http://example.com/profile/e.federer",
        role           = Some("user")
      )
    )

    it("transforms TeamMembers into DisplayableTeamMembers correctly") {
      val displayableTeamMembers: Seq[DisplayableTeamMember] =
        DisplayableTeamMembers(teamName, "http://example.com/profile", teamMembers)

      for (i <- 0 until 4) {
        displayableTeamMembers should contain(expectedDisplayTeamMembers(i))
      }

    }
  }
}
