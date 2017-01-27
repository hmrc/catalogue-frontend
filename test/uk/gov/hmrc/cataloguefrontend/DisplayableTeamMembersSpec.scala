/*
 * Copyright 2017 HM Revenue & Customs
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
import org.scalatest.{FunSpec, Matchers}
import uk.gov.hmrc.cataloguefrontend.DisplayableTeamMembers.DisplayableTeamMember
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.TeamMember

class DisplayableTeamMembersSpec extends FunSpec with Matchers with TypeCheckedTripleEquals {

  describe("DisplayTeamMembers") {

    val teamName = "teamA"


    val teamMembers = Seq(
      TeamMember(
        displayName =  Some("E Federer"),
        familyName =  Some("Federer"),
        givenName =  Some("E"),
        primaryEmail =  Some("e.federer@digital.hmrc.gov.uk"),
        username =  Some("e.federer"),
        serviceOwnerFor = Some(Seq("teamC"))
      ),
      TeamMember(
        displayName =  Some("B Olapade"),
        familyName =  Some("Olapade"),
        givenName =  Some("B"),
        primaryEmail =  Some("b.olapade@digital.hmrc.gov.uk"),
        username =  Some("b.olapade"),
        serviceOwnerFor = Some(Seq("teamA", "teamB"))
      ),
      TeamMember(
        displayName =  Some("D Doe"),
        familyName =  Some("Doe"),
        givenName =  Some("D"),
        primaryEmail =  Some("d.doe@digital.hmrc.gov.uk"),
        username =  Some("d.doe"),
        serviceOwnerFor = None
      ),
      TeamMember(
        displayName =  Some("A Mouse"),
        familyName =  Some("Mouse"),
        givenName =  Some("A"),
        primaryEmail =  Some("a.mouse@digital.hmrc.gov.uk"),
        username =  Some("a.mouse"),
        serviceOwnerFor = Some(Seq("teamA"))
      ),
      TeamMember(
        displayName =  Some("C Bourne"),
        familyName =  Some("Bourne"),
        givenName =  Some("C"),
        primaryEmail =  Some("c.bourne@digital.hmrc.gov.uk"),
        username =  Some("c.bourne"),
        serviceOwnerFor = None
      )
      
    )

    val expectedDisplayTeamMembers = Seq(
      DisplayableTeamMember(
        displayName =  "A Mouse",
        isServiceOwner = true,
        umpLink = "http://example.com/profile/a.mouse"
      ),
      DisplayableTeamMember(
        displayName =  "B Olapade",
        isServiceOwner = true,
        umpLink = "http://example.com/profile/b.olapade"
      ),
      DisplayableTeamMember(
        displayName =  "C Bourne",
        isServiceOwner = false,
        umpLink = "http://example.com/profile/c.bourne"
      ),
      DisplayableTeamMember(
        displayName =  "D Doe",
        isServiceOwner = false,
        umpLink = "http://example.com/profile/d.doe"
      ),
      DisplayableTeamMember(
        displayName =  "E Federer",
        isServiceOwner = false,
        umpLink = "http://example.com/profile/e.federer"
      )
    )


    val (expectedServiceOwners, expectedOthers)  = expectedDisplayTeamMembers.partition(_.isServiceOwner)

    it("transforms TeamMembers into DisplayableTeamMembers correctly") {
      val displayableTeamMembers: Seq[DisplayableTeamMember] = DisplayableTeamMembers(teamName, "http://example.com/profile", teamMembers)

      for (i <- 0 until 4) {
        displayableTeamMembers should contain (expectedDisplayTeamMembers(i))
      }

    }


    it("finds service owners based on service name (non-casesensetive)") {
      val differentCasedOwnerTeamMembers = Seq(
        TeamMember(
          displayName =  Some("E Federer"),
          familyName =  Some("Federer"),
          givenName =  Some("E"),
          primaryEmail =  Some("e.federer@digital.hmrc.gov.uk"),
          username =  Some("e.federer"),
          serviceOwnerFor = Some(Seq("TEAMa"))
        )
      )
      val displatableServiceOwners = DisplayableTeamMembers(teamName, "http://example.com/profile", differentCasedOwnerTeamMembers)

      displatableServiceOwners.size shouldBe 1
      displatableServiceOwners(0).isServiceOwner shouldBe true

    }

    it("displays service owners and then non service owners") {
      val (serviceOwners, others) = DisplayableTeamMembers(teamName, "http://example.com/profile", teamMembers).splitAt(2)

      serviceOwners should contain only (expectedServiceOwners: _*)
      others should contain only (expectedOthers: _*)
    }

    it("sorts service owners and non-service owners by display name") {
      val (serviceOwners, others) = DisplayableTeamMembers(teamName, "http://example.com/profile", teamMembers).splitAt(2)

      serviceOwners should===(expectedServiceOwners)
      others should===(expectedOthers)
    }

  }

}
