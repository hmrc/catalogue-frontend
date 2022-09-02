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

package uk.gov.hmrc.cataloguefrontend
package connector

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class UserManagementConnector @Inject() (
  httpClientV2              : HttpClientV2,
  userManagementPortalConfig: UserManagementPortalConfig
)(implicit val ec: ExecutionContext) {

  import UserManagementConnector._
  import userManagementPortalConfig._

  private val logger = Logger(getClass)

  private implicit val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
  }

  def getTeamMembersForTeams(teamNames: Seq[TeamName])(implicit hc: HeaderCarrier): Future[Map[TeamName, Either[UMPError, Seq[TeamMember]]]] =
    Future
      .traverse(teamNames)(teamName =>
          getTeamMembersFromUMP(teamName).map(umpErrorOrTeamMembers => (teamName, umpErrorOrTeamMembers))
      )
      .map(_.toMap)

  def getTeamMembersFromUMP(teamName: TeamName)(implicit hc: HeaderCarrier): Future[Either[UMPError, Seq[TeamMember]]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/teams/${teamName.asString}/members"
    httpClientV2
      .get(url)
      .setHeader(
        "requester" -> "None",
        "Token"     -> "None"
      )
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case 200 =>
            (response.json \ "members")
              .validate[List[TeamMember]]
              .fold(
                errors => Left(UMPError.ConnectionError(s"Could not parse response from $url: $errors")),
                Right.apply
              )
          case 404      => Left(UMPError.UnknownTeam)
          case httpCode => Left(UMPError.HTTPError(httpCode))
        }
      }
      .recover {
        case ex =>
          logger.error(s"An error occurred when connecting to $url", ex)
          Left(UMPError.ConnectionError(s"Could not connect to $url: ${ex.getMessage}"))
      }
  }

  def getAllUsersFromUMP(implicit hc: HeaderCarrier): Future[Either[UMPError, Seq[TeamMember]]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/users"
    httpClientV2
      .get(url)
      .setHeader(
        "requester" -> "None",
        "Token"     -> "None"
      )
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case 200 =>
            (response.json \\ "users").headOption
              .map(_.as[Seq[TeamMember]])
              .fold[Either[UMPError, Seq[TeamMember]]](ifEmpty = Left(UMPError.ConnectionError(s"Could not parse response from $url")))(Right.apply)
          case httpCode => Left(UMPError.HTTPError(httpCode))
        }
      }
      .recover {
        case ex =>
          logger.error(s"An error occurred when connecting to $url", ex)
          Left(UMPError.ConnectionError(s"Could not connect to $url: ${ex.getMessage}"))
      }
  }

  def getTeamDetails(teamName: TeamName)(implicit hc: HeaderCarrier): Future[Either[UMPError, TeamDetails]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/teams/${teamName.asString}"
    httpClientV2
      .get(url)
      .setHeader(
        "requester" -> "None",
        "Token"     -> "None"
      )
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case 200 =>
            response.json
              .validate[TeamDetails]
              .fold(
                errors => Left(UMPError.ConnectionError(s"Could not parse response from $url: $errors")),
                Right.apply
              )
          case 404      => Left(UMPError.UnknownTeam)
          case httpCode => Left(UMPError.HTTPError(httpCode))
        }
      }
      .recover {
        case ex =>
          logger.error(s"An error occurred when connecting to $url", ex)
          Left(UMPError.ConnectionError(s"Could not connect to $url: ${ex.getMessage}"))
      }
  }
}

object UserManagementConnector {
  sealed trait UMPError

  object UMPError {
    case object UnknownTeam extends UMPError
    case class HTTPError(code: Int) extends UMPError
    case class ConnectionError(error: String) extends UMPError
  }

  case class TeamMember(
    displayName    : Option[String],
    familyName     : Option[String],
    givenName      : Option[String],
    primaryEmail   : Option[String],
    serviceOwnerFor: Option[Seq[String]],
    username       : Option[String]
  ) {

    def getUmpLink(umpProfileBaseUrl: String): String =
      username.map(x => url"$umpProfileBaseUrl/$x".toString).getOrElse("USERNAME NOT PROVIDED")

    def getDisplayName: String =
      this.displayName.getOrElse("DISPLAY NAME NOT PROVIDED")
  }

  case class SlackInfo(url : String) {
    val name         = url.split("/").lastOption.getOrElse(url)
    val hasValidUrl  = url.startsWith("http://") || url.startsWith("https://")
    val hasValidName = "^[A-Z0-9]+$".r.findFirstIn(name).isEmpty
  }

  case class TeamDetails(
    description      : Option[String],
    location         : Option[String],
    organisation     : Option[String],
    documentation    : Option[String],
    slack            : Option[SlackInfo],
    slackNotification: Option[SlackInfo],
    team             : String
  )

  implicit val teamMemberFormat: OFormat[TeamMember] = Json.format[TeamMember]
  implicit val slackInfoReads: Reads[SlackInfo]      = implicitly[Reads[String]].map(SlackInfo(_))
  implicit val teamDetailsReads: Reads[TeamDetails]  = Json.reads[TeamDetails]

  final case class DisplayName(value: String) {
    require(value.nonEmpty)

    override def toString: String = value
  }
}
