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
package connector

import play.api.Logger
import play.api.cache.AsyncCacheApi
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.config.{UserManagementAuthConfig, UserManagementPortalConfig}
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class UserManagementConnector @Inject() (
  httpClientV2              : HttpClientV2,
  userManagementPortalConfig: UserManagementPortalConfig,
  userManagementAuthConfig  : UserManagementAuthConfig,
  tokenCache                : AsyncCacheApi
)(implicit val ec: ExecutionContext) {

  import UserManagementConnector._
  import userManagementAuthConfig._
  import userManagementPortalConfig._
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  def login(): Future[UmpToken] = {
    implicit val lrf: OFormat[UmpLoginRequest] = UmpLoginRequest.format
    implicit val atf: OFormat[UmpAuthToken] = UmpAuthToken.format
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val url = url"$userManagementLoginUrl"
    val login = UmpLoginRequest(username, password)

    for {
      token <- httpClientV2.post(url).withBody(Json.toJson(login)).execute[UmpAuthToken]
      _ = logger.info("logged into UMP")
    } yield token
  }

  def retrieveToken(): Future[UmpToken] =
    if (authEnabled)
      tokenCache.getOrElseUpdate[UmpToken]("token", tokenTTL)(login())
    else
      Future.successful(NoTokenRequired)

  def getTeamMembersFromUMP(teamName: TeamName)(implicit hc: HeaderCarrier): Future[Either[UMPError, Seq[TeamMember]]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/teams/${teamName.asString}/members"
    for {
      token <- retrieveToken()
      resp <- httpClientV2
                .get(url)
                .setHeader(token.asHeaders():_*)
                .execute[HttpResponse]
                .map { response =>
                  response.status match {
                    case 200 =>
                      (response.json \ "members")
                        .validate[List[TeamMember]]
                        .fold(
                          errors  => Left(UMPError.ConnectionError(s"Could not parse response from $url: $errors")),
                          members => Right(members.filterNot(m => List("service", "platops").exists(m.getDisplayName.toLowerCase.contains(_))))
                        )
                    case 404 => Left(UMPError.UnknownTeam)
                    case httpCode => Left(UMPError.HTTPError(httpCode))
                  }
                }
                .recover {
                  case ex =>
                    logger.error(s"An error occurred when connecting to $url", ex)
                    Left(UMPError.ConnectionError(s"Could not connect to $url: ${ex.getMessage}"))
                }
    } yield resp
  }.recover {
    case ex =>
      logger.error(s"Failed to login to UMP", ex)
      Left(UMPError.ConnectionError(s"Failed to login to UMP: ${ex.getMessage}"))
  }

  def getAllUsersFromUMP(implicit hc: HeaderCarrier): Future[Either[UMPError, Seq[TeamMember]]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/users"
    for {
      token <- retrieveToken()
      resp  <- httpClientV2
                 .get(url)
                 .setHeader(token.asHeaders():_*)
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
    } yield resp
  }.recover {
    case ex =>
      logger.error(s"Failed to login to UMP", ex)
      Left(UMPError.ConnectionError(s"Failed to login to UMP: ${ex.getMessage}"))
  }

  def getTeamDetails(teamName: TeamName)(implicit hc: HeaderCarrier): Future[Either[UMPError, TeamDetails]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/teams/${teamName.asString}"
    for {
      token <- retrieveToken()
      resp <- httpClientV2.get(url)
                .setHeader(token.asHeaders():_*)
                .execute[HttpResponse]
                .map {response =>
                  response.status match {
                    case 200 =>
                      response.json
                        .validate[TeamDetails]
                        .fold (
                          errors => Left (UMPError.ConnectionError (s"Could not parse response from $url: $errors")),
                          Right.apply
                        )
                    case 404 => Left (UMPError.UnknownTeam)
                    case httpCode => Left (UMPError.HTTPError (httpCode))
                  }
                }
        .recover {
          case ex =>
            logger.error (s"An error occurred when connecting to $url", ex)
            Left (UMPError.ConnectionError (s"Could not connect to $url: ${ex.getMessage}"))
        }
    } yield resp
  }.recover {
    case ex =>
      logger.error(s"Failed to login to UMP", ex)
      Left(UMPError.ConnectionError(s"Failed to login to UMP: ${ex.getMessage}"))
  }

  def getTeamsForUser(ldapUsername: String)(implicit hc: HeaderCarrier): Future[Either[UMPError, List[TeamDetails]]] = {
    val url = url"$userManagementBaseUrl/v2/organisations/users/$ldapUsername/teams"
    for {
      token <- retrieveToken()
      resp <- httpClientV2.get(url)
        .setHeader(token.asHeaders(): _*)
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case 200 => response.json
              .validate[UmpTeams]
              .fold(
                errors => Left(UMPError.ConnectionError(s"Could not parse response from $url: $errors")),
                umpTeams => Right(umpTeams.teams)
              )
            case 404 => Right(List.empty[TeamDetails])
            case httpCode => Left(UMPError.HTTPError(httpCode))
          }
        }
        .recover {
          case ex =>
            logger.error(s"An error occurred when connecting to $url", ex)
            Left(UMPError.ConnectionError(s"Could not connect to $url: ${ex.getMessage}"))
        }
    } yield resp
  }.recover {
    case ex =>
      logger.error(s"Failed to login to UMP", ex)
      Left(UMPError.ConnectionError(s"Failed to login to UMP: ${ex.getMessage}"))
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
    username       : Option[String],
    role           : Option[String]
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

  case class UmpTeams(
     teams: List[TeamDetails]
   )

  object UmpTeams {
    implicit val reads: Reads[UmpTeams] = Json.reads[UmpTeams]
  }

  final case class DisplayName(value: String) {
    require(value.nonEmpty)

    override def toString: String = value
  }

  sealed trait UmpToken {
    def asHeaders(): Seq[(String, String)]
  }

  case class UmpAuthToken(token: String, uid: String) extends UmpToken {
    def asHeaders(): Seq[(String, String)] = {
      Seq( "Token" -> token, "requester" -> uid)
    }
  }

  case object NoTokenRequired extends UmpToken {
    override def asHeaders(): Seq[(String, String)] = Seq.empty
  }

  object UmpAuthToken {
    val format: OFormat[UmpAuthToken] = (
      (__ \ "Token").format[String]
        ~ (__ \ "uid").format[String]
      )(UmpAuthToken.apply, unlift(UmpAuthToken.unapply))
  }

  case class UmpLoginRequest(username: String, password:String)

  object UmpLoginRequest {
    val format: OFormat[UmpLoginRequest] = (
      (__ \ "username").format[String]
        ~ (__ \ "password").format[String]
      )(UmpLoginRequest.apply, unlift(UmpLoginRequest.unapply))
  }
}
