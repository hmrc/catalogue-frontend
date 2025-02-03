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

import play.api.libs.json.*
import play.api.libs.functional.syntax.*
import uk.gov.hmrc.cataloguefrontend.model.{TeamName, UserName}
import uk.gov.hmrc.cataloguefrontend.util.FromString
import uk.gov.hmrc.crypto.Sensitive.SensitiveString

case class Role(asString: String):
  def displayName: String =
    asString.split("_").map(_.capitalize).mkString(" ")

  def isUser: Boolean =
    asString == "user"

object Role:
  val reads: Reads[Role] =
    summon[Reads[String]].map(Role.apply)

case class Member(
  username   : UserName
, displayName: Option[String]
, role       : Role
, isNonHuman : Boolean
)

object Member:
  val reads: Reads[Member] =
    ( (__ \ "username"   ).read[UserName]
    ~ (__ \ "displayName").readNullable[String]
    ~ (__ \ "role"       ).read[Role](Role.reads)
    ~ (__ \ "isNonHuman" ).read[Boolean]
    )(Member.apply)

case class SlackInfo(url: String):
  val name: String =
    url.split("/").lastOption.getOrElse(url)

  val hasValidUrl: Boolean =
    url.startsWith("http://") || url.startsWith("https://")

  val hasValidName: Boolean =
    "^[A-Z0-9]+$".r.findFirstIn(name).isEmpty
end SlackInfo

object SlackInfo {
  val reads: Reads[SlackInfo] =
    summon[Reads[String]].map(SlackInfo.apply)
}

case class UmpTeam(
  members          : Seq[Member]
, teamName         : TeamName
, description      : Option[String]
, documentation    : Option[String]
, slack            : Option[SlackInfo]
, slackNotification: Option[SlackInfo]
)

object UmpTeam:
  val reads: Reads[UmpTeam] =
    given Reads[Member]    = Member.reads
    given Reads[SlackInfo] = SlackInfo.reads
    ( (__ \ "members"          ).read[Seq[Member]]
    ~ (__ \ "teamName"         ).read[TeamName]
    ~ (__ \ "description"      ).readNullable[String]
    ~ (__ \ "documentation"    ).readNullable[String]
    ~ (__ \ "slack"            ).readNullable[SlackInfo]
    ~ (__ \ "slackNotification").readNullable[SlackInfo]
    )(UmpTeam.apply)

case class User(
  displayName   : Option[String],
  familyName    : String,
  givenName     : Option[String],
  organisation  : Option[String],
  primaryEmail  : String,
  username      : UserName,
  githubUsername: Option[String],
  phoneNumber   : Option[String],
  role          : Role,
  teamNames     : Seq[TeamName],
  isDeleted     : Boolean,
  isNonHuman    : Boolean
)

object User:
  val reads: Reads[User] =
    ( ( __ \ "displayName"   ).readNullable[String]
    ~ ( __ \ "familyName"    ).read[String]
    ~ ( __ \ "givenName"     ).readNullable[String]
    ~ ( __ \ "organisation"  ).readNullable[String]
    ~ ( __ \ "primaryEmail"  ).read[String]
    ~ ( __ \ "username"      ).read[UserName]
    ~ ( __ \ "githubUsername").readNullable[String]
    ~ ( __ \ "phoneNumber"   ).readNullable[String]
    ~ ( __ \ "role"          ).read[Role](Role.reads)
    ~ ( __ \ "teamNames"     ).read[Seq[TeamName]]
    ~ ( __ \ "isDeleted"     ).read[Boolean]
    ~ ( __ \ "isNonHuman"    ).read[Boolean]
    )(User.apply)

case class UserAccess(
  vpn: Boolean,
  jira: Boolean,
  confluence: Boolean,
  devTools: Boolean,
  googleApps: Boolean
)

object UserAccess:
  def empty: UserAccess =
    UserAccess(vpn = false, jira = false, confluence = false, devTools = false, googleApps = false)
  
  val reads: Reads[UserAccess] =
    ( (__ \ "vpn"       ).read[Boolean]
    ~ (__ \ "jira"      ).read[Boolean]
    ~ (__ \ "confluence").read[Boolean]
    ~ (__ \ "devTools"  ).read[Boolean]
    ~ (__ \ "googleApps").read[Boolean]
    )(UserAccess.apply _)

  val writes: Writes[UserAccess] =
    (userAccess: UserAccess) => Json.obj(
      "vpn"        -> userAccess.vpn,
      "jira"       -> userAccess.jira,
      "confluence" -> userAccess.confluence,
      "devTools"   -> userAccess.devTools,
      "googleApps" -> userAccess.googleApps
    )

  val format: Format[UserAccess] =
    Format(reads, writes)

enum Organisation(val asString: String) extends FromString:
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
  environments      : Boolean,
  bitwarden         : Boolean
)


object CreateUserRequest:
  val writes: Writes[CreateUserRequest] =
    OWrites.transform[CreateUserRequest](
      ( (__ \ "givenName"               ).write[String]
      ~ (__ \ "familyName"              ).write[String]
      ~ (__ \ "organisation"            ).write[String]
      ~ (__ \ "contactEmail"            ).write[String]
      ~ (__ \ "contactComments"         ).write[String]
      ~ (__ \ "team"                    ).write[TeamName]
      ~ (__ \ "isReturningUser"         ).write[Boolean]
      ~ (__ \ "isTransitoryUser"        ).write[Boolean]
      ~ (__ \ "isServiceAccount"        ).write[Boolean]
      ~ (__ \ "access" \ "vpn"          ).write[Boolean]
      ~ (__ \ "access" \ "jira"         ).write[Boolean]
      ~ (__ \ "access" \ "confluence"   ).write[Boolean]
      ~ (__ \ "access" \ "googleApps"   ).write[Boolean]
      ~ (__ \ "access" \ "environments" ).write[Boolean]
      ~ (__ \ "access" \ "bitwarden"    ).write[Boolean]
      )(r => Tuple.fromProductTyped(r))
    ): (req, json) =>
      val givenName   = if req.isServiceAccount then s"service_${req.givenName}"     else req.givenName
      val displayName = if req.isServiceAccount then s"$givenName ${req.familyName}" else s"${givenName.capitalize} ${req.familyName.capitalize}"
      json ++ Json.obj(
        "givenName"          -> givenName,
        "userDisplayName"    -> displayName,
        "isExistingLDAPUser" -> false,
        "access"             -> ((json \ "access").as[JsObject] ++ Json.obj("ldap" -> true))
      )

case class EditUserAccessRequest(
  username          : String,
  organisation      : String,
  vpn               : Boolean,
  jira              : Boolean,
  confluence        : Boolean,
  googleApps        : Boolean,
  environments      : Boolean,
  bitwarden         : Boolean
)

object EditUserAccessRequest:
  val writes: Writes[EditUserAccessRequest] =
    OWrites.transform[EditUserAccessRequest](
      ( (__ \ "username"                ).write[String]
      ~ (__ \ "organisation"            ).write[String]
      ~ (__ \ "access" \ "vpn"          ).write[Boolean]
      ~ (__ \ "access" \ "jira"         ).write[Boolean]
      ~ (__ \ "access" \ "confluence"   ).write[Boolean]
      ~ (__ \ "access" \ "googleApps"   ).write[Boolean]
      ~ (__ \ "access" \ "environments" ).write[Boolean]
      ~ (__ \ "access" \ "bitwarden"    ).write[Boolean]
      )(r => Tuple.fromProductTyped(r))
    ): (req, json) =>
      json ++ Json.obj(
        "isExistingLDAPUser" -> true
      )

case class ResetLdapPassword(
  username: String,
  email: String
)

object ResetLdapPassword:
  val writes: Writes[ResetLdapPassword] =
    ( (__ \ "username").write[String]
    ~ (__ \ "email_address").write[String]
    )(r => Tuple.fromProductTyped(r))

case class AddToGithubTeamRequest(
  username: String,
  team    : String
)

object AddToGithubTeamRequest:
  val writes: Writes[AddToGithubTeamRequest] =
    ( (__ \ "username").write[String]
    ~ (__ \ "team"    ).write[String]
    )(a => Tuple.fromProductTyped(a))

enum UserAttribute(val name: String, val description: String):
  case DisplayName  extends UserAttribute("displayName" , "Name"           )
  case Github       extends UserAttribute("github"      , "GitHub username")
  case Organisation extends UserAttribute("organisation", "Organisation"   )
  case PhoneNumber  extends UserAttribute("phoneNumber" , "Phone number"   )

object UserAttribute:
  def fromString(value: String): Option[UserAttribute] =
    UserAttribute.values.find(_.name == value)
  
  val writes: Writes[UserAttribute] =
    Writes { attr => JsString(attr.name) }

case class EditUserDetailsRequest(username: String, attribute: UserAttribute, value: String)

object EditUserDetailsRequest:
  val writes: Writes[EditUserDetailsRequest] =
    ( (__ \ "username" ).write[String]
    ~ (__ \ "attribute").write[UserAttribute](UserAttribute.writes)
    ~ (__ \ "value"    ).write[String]
    )(e => Tuple.fromProductTyped(e))

case class ResetGooglePassword(
  username: String,
  password: SensitiveString
)

object ResetGooglePassword:
  val writes: Writes[ResetGooglePassword] =
    given Writes[SensitiveString] = summon[Writes[String]].contramap(_.decryptedValue)
    ( (__ \ "username").write[String]
    ~ (__ \ "password").write[SensitiveString]
    )(r => Tuple.fromProductTyped(r))
