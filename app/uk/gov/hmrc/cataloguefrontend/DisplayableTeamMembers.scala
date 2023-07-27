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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementPortalConnector.TeamMember
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName

object DisplayableTeamMembers {

  def apply(
    teamName         : TeamName,
    umpProfileBaseUrl: String,
    teamMembers      : Seq[TeamMember]
  ): Seq[DisplayableTeamMember] =
    teamMembers
      .map(tm =>
        DisplayableTeamMember(
          displayName    = tm.getDisplayName,
          umpLink        = tm.getUmpLink(umpProfileBaseUrl),
          role           = tm.role
        )
      ).sortBy((_.displayName))
}

case class DisplayableTeamMember(
  displayName   : String,
  umpLink       : String,
  role          : Option[String]
)

object DisplayableTeamMember {

  implicit val displayableTeamMemberFormat: OFormat[DisplayableTeamMember] = Json.format[DisplayableTeamMember]

  def apply(tm: TeamMember, umpProfileBaseUrl: String): DisplayableTeamMember =
    DisplayableTeamMember(
      displayName = tm.getDisplayName,
      umpLink     = tm.getUmpLink(umpProfileBaseUrl),
      role        = tm.role
    )
}
