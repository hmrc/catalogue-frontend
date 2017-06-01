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

import play.api.libs.json.Json
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.TeamMember

object DisplayableTeamMembers {


  def apply(teamName: String, umpProfileBaseUrl: String, teamMembers: Seq[TeamMember]): Seq[DisplayableTeamMember] = {

    val displayableTeamMembers = teamMembers.map(tm =>
      DisplayableTeamMember(
        displayName = tm.getDisplayName,
        isServiceOwner = tm.serviceOwnerFor.map(_.map(_.toLowerCase)).exists(_.contains(teamName.toLowerCase)),
        umpLink = tm.getUmpLink(umpProfileBaseUrl))
    )

    val (serviceOwners, others) = displayableTeamMembers.partition(_.isServiceOwner)
    serviceOwners.sortBy(_.displayName) ++ others.sortBy(_.displayName)
  }

}

case class DisplayableTeamMember(displayName: String,
                                 isServiceOwner: Boolean = false,
                                 umpLink: String)

object DisplayableTeamMember {

  implicit val displayableTeamMemberFormat = Json.format[DisplayableTeamMember]

  def apply(tm: TeamMember, umpProfileBaseUrl: String): DisplayableTeamMember = DisplayableTeamMember(
    displayName = tm.getDisplayName,
    umpLink = tm.getUmpLink(umpProfileBaseUrl)
  )
}
