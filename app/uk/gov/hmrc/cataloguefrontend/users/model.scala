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

package uk.gov.hmrc.cataloguefrontend.users

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsObject, Json, OWrites, Reads, __}
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName

final case class Role(asString: String) {
  def displayName: String =
    asString.split("_").map(_.capitalize).mkString(" ")

  def isUser: Boolean =
    asString == "user"
}

object Role {
  val reads: Reads[Role] = Reads.StringReads.map(Role.apply)
}

final case class Member(
  username   : String
, displayName: Option[String]
, role       : Role
)

object Member {

  val reads: Reads[Member] = {
    implicit val rR : Reads[Role] = Role.reads
    ( (__ \ "username"   ).read[String]
    ~ (__ \ "displayName").readNullable[String]
    ~ (__ \ "role"       ).read[Role]
    )(Member.apply)
  }
}

final case class SlackInfo(url: String) {
  val name: String =
    url.split("/").lastOption.getOrElse(url)

  val hasValidUrl: Boolean =
    url.startsWith("http://") || url.startsWith("https://")

  val hasValidName: Boolean =
    "^[A-Z0-9]+$".r.findFirstIn(name).isEmpty
}

object SlackInfo {
  val reads: Reads[SlackInfo] = Reads.StringReads.map(SlackInfo.apply)
}

final case class UmpTeam(
  members          : Seq[Member]
, teamName         : TeamName
, description      : Option[String]
, documentation    : Option[String]
, slack            : Option[SlackInfo]
, slackNotification: Option[SlackInfo]
)

object UmpTeam {
  val reads: Reads[UmpTeam] = {
    implicit val mr : Reads[Member]    = Member.reads
    implicit val sir: Reads[SlackInfo] = SlackInfo.reads
    implicit val tnr: Reads[TeamName]  = TeamName.format
    ( (__ \ "members"          ).read[Seq[Member]]
    ~ (__ \ "teamName"         ).read[TeamName]
    ~ (__ \ "description"      ).readNullable[String]
    ~ (__ \ "documentation"    ).readNullable[String]
    ~ (__ \ "slack"            ).readNullable[SlackInfo]
    ~ (__ \ "slackNotification").readNullable[SlackInfo]
    )(UmpTeam.apply)
  }
}

final case class User(
  displayName   : Option[String],
  familyName    : String,
  givenName     : Option[String],
  organisation  : Option[String],
  primaryEmail  : String,
  username      : String,
  githubUsername: Option[String],
  phoneNumber   : Option[String],
  role          : Role,
  teamNames     : Seq[TeamName]
)

object User {
  val reads: Reads[User] = {
    ( ( __ \ "displayName"   ).readNullable[String]
    ~ ( __ \ "familyName"    ).read[String]
    ~ ( __ \ "givenName"     ).readNullable[String]
    ~ ( __ \ "organisation"  ).readNullable[String]
    ~ ( __ \ "primaryEmail"  ).read[String]
    ~ ( __ \ "username"      ).read[String]
    ~ ( __ \ "githubUsername").readNullable[String]
    ~ ( __ \ "phoneNumber"   ).readNullable[String]
    ~ ( __ \ "role"          ).read[Role](Role.reads)
    ~ ( __ \ "teamNames"     ).read[Seq[TeamName]](Reads.seq(TeamName.format))
      )(User.apply)
  }
}

enum Organisation(val asString: String):
  case Mdtp  extends Organisation("MDTP" )
  case Voa   extends Organisation("VOA"  )
  case Other extends Organisation("Other")

case class CreateUserRequest(
  givenName         : String,
  familyName        : String,
  organisation      : String,
  contactEmail      : String,
  contactComments   : String,
  team              : TeamName,
  isReturningUser   : Boolean,
  isTransitoryUser  : Boolean,
  isServiceAccount  : Boolean,
  vpn               : Boolean,
  jira              : Boolean,
  confluence        : Boolean,
  googleApps        : Boolean,
  environments      : Boolean
)


object CreateUserRequest {

  implicit val writes: OWrites[CreateUserRequest] =
    OWrites.transform[CreateUserRequest](
      ( (__ \ "givenName"               ).write[String]
      ~ (__ \ "familyName"              ).write[String]
      ~ (__ \ "organisation"            ).write[String]
      ~ (__ \ "contactEmail"            ).write[String]
      ~ (__ \ "contactComments"         ).write[String]
      ~ (__ \ "team"                    ).write[TeamName](TeamName.format)
      ~ (__ \ "isReturningUser"         ).write[Boolean]
      ~ (__ \ "isTransitoryUser"        ).write[Boolean]
      ~ (__ \ "isServiceAccount"        ).write[Boolean]
      ~ (__ \ "access" \ "vpn"          ).write[Boolean]
      ~ (__ \ "access" \ "jira"         ).write[Boolean]
      ~ (__ \ "access" \ "confluence"   ).write[Boolean]
      ~ (__ \ "access" \ "googleApps"   ).write[Boolean]
      ~ (__ \ "access" \ "environments" ).write[Boolean]
      )(r => Tuple.fromProductTyped(r))
    ) { (req, json) =>
      val givenName   = if (req.isServiceAccount) s"service_${req.givenName}" else req.givenName
      val displayName = if (req.isServiceAccount) s"$givenName ${req.familyName}" else s"${givenName.capitalize} ${req.familyName.capitalize}"
      json ++ Json.obj(
        "givenName"          -> givenName,
        "username"           -> s"$givenName.${req.familyName}",
        "displayName"        -> displayName,
        "isExistingLDAPUser" -> false,
        "access"             -> ((json \ "access").as[JsObject] ++ Json.obj("ldap" -> true))
      )
    }
}
