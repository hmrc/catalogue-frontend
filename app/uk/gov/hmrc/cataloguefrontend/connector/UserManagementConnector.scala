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

/*
 * Copyright 2016 HM Revenue & Customs
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


import javax.inject.{Inject, Singleton}

import play.api.libs.json._
import play.api.{Configuration, Logger, Environment => PlayEnvironment}
import uk.gov.hmrc.cataloguefrontend.FutureHelpers.withTimerAndCounter
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



@Singleton
case class UserManagementConnector @Inject()(http : HttpClient, override val runModeConfiguration:Configuration, environment : PlayEnvironment) extends UserManagementPortalLink {

  import UserManagementConnector._

  implicit val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse) = response
  }


  def getTeamMembersForTeams(teamNames: Seq[String])(implicit hc: HeaderCarrier): Future[Map[String, Either[UMPError, Seq[TeamMember]]]] = {

    def getTeamMembers(teamName: String) = getTeamMembersFromUMP(teamName).map(umpErrorOrTeamMembers => (teamName, umpErrorOrTeamMembers))

    Future.sequence(teamNames.map(getTeamMembers)).map(_.toMap)
  }


  def getTeamMembersFromUMP(team: String)(implicit hc: HeaderCarrier): Future[Either[UMPError, Seq[TeamMember]]] = {
    val newHeaderCarrier = hc.withExtraHeaders("requester" -> "None", "Token" -> "None")

    val url = s"$userManagementBaseUrl/v2/organisations/teams/$team/members"

    def isHttpCodeFailure: Option[(Either[UMPError, _]) => Boolean] =
      Some { errorOrMembers: Either[UMPError, _] =>
        errorOrMembers match {
          case Left(HTTPError(code)) if code != 200 => true
          case _ => false
        }
      }

    val eventualConnectorErrorOrTeamMembers = http.GET[HttpResponse](url)(httpReads, newHeaderCarrier, fromLoggingDetails(newHeaderCarrier)).map { response =>
      response.status match {
        case 200 => extractMembers(team, response)
        case httpCode => Left(HTTPError(httpCode))
      }
    }

    withTimerAndCounter("ump")(eventualConnectorErrorOrTeamMembers, isHttpCodeFailure)
      .recover {
        case ex =>
          Logger.error(s"An error occurred when connecting to $userManagementBaseUrl: ${ex.getMessage}", ex)
          Left(ConnectionError(ex))
      }
  }


  def getAllUsersFromUMP(): Future[Either[UMPError, Seq[TeamMember]]] = {

    val newHeaderCarrier = HeaderCarrier().withExtraHeaders("requester" -> "None", "Token" -> "None")

    val url = s"$userManagementBaseUrl/v2/organisations/users"

    def isHttpCodeFailure: Option[(Either[UMPError, _]) => Boolean] =
      Some { errorOrMembers: Either[UMPError, _] =>
        errorOrMembers match {
          case Left(HTTPError(code)) if code != 200 => true
          case _ => false
        }
      }

    val eventualErrorOrTeamMembers = http.GET[HttpResponse](url)(httpReads, newHeaderCarrier, fromLoggingDetails(newHeaderCarrier)).map { response =>
      response.status match {
        case 200 => Right(extractUsers(response))
        case httpCode => Left(HTTPError(httpCode))
      }
    }

    withTimerAndCounter("ump")(eventualErrorOrTeamMembers, isHttpCodeFailure)
      .recover {
        case ex =>
          Logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Left(ConnectionError(ex))
      }
  }


  def getTeamDetails(team: String)(implicit hc: HeaderCarrier): Future[Either[UMPError, TeamDetails]] = {
    import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.teamDetailsReads
    val newHeaderCarrier = hc.withExtraHeaders("requester" -> "None", "Token" -> "None")
    //    val url = s"$userManagementBaseUrl/v1/organisations/mdtp/teams/$team"
    val url = s"$userManagementBaseUrl/v2/organisations/teams/$team"
    withTimerAndCounter("ump-teamdetails") {
      http.GET[HttpResponse](url)(httpReads, newHeaderCarrier, fromLoggingDetails(newHeaderCarrier)).map { response =>
        response.status match {
          case 200 => extractData[TeamDetails](team, response)
          case httpCode => Left(HTTPError(httpCode))
        }
      }.recover {
        case ex =>
          Logger.error(s"An error occurred when connecting to $userManagementBaseUrl: ${ex.getMessage}", ex)
          Left(ConnectionError(ex))
      }
    }

  }


  def extractData[T](team: String, response: HttpResponse)(implicit rd: Reads[T]): Either[UMPError, T] = {

    Option(response.json).flatMap { js => js.asOpt[T] } match {
      case Some(x) => Right(x)
      case _ => Left(NoData(umpMyTeamsPageUrl(team)))
    }
  }

  private def extractMembers(team: String, response: HttpResponse): Either[UMPError, Seq[TeamMember]] = {

    val left = Left(NoData(umpMyTeamsPageUrl(team)))

    val errorOrTeamMembers = (response.json \\ "members")
      .headOption
      .map(js => Right(js.as[Seq[TeamMember]]))
      .getOrElse(left)

    if (errorOrTeamMembers.isRight && errorOrTeamMembers.right.get.isEmpty) left else errorOrTeamMembers
  }

  def extractUsers(response: HttpResponse): Seq[TeamMember] =
    (response.json \\ "users")
      .headOption
      .map(js => js.as[Seq[TeamMember]])
      .getOrElse(throw new RuntimeException(s"Unable to parse or extract UMP users: ${response.json}"))

  override protected def mode = environment.mode

}

object UserManagementConnector {
  sealed trait UMPError

  case class NoData(linkToRectify: String) extends UMPError

  case class HTTPError(code: Int) extends UMPError

  case class ConnectionError(exception: Throwable) extends UMPError {
    override def toString: String = exception.getMessage
  }




  case class TeamMember(displayName: Option[String],
                        familyName: Option[String],
                        givenName: Option[String],
                        primaryEmail: Option[String],
                        serviceOwnerFor: Option[Seq[String]],
                        username: Option[String]) {

    def getUmpLink(umpProfileBaseUrl: String) =
      this.username.map(x => s"${umpProfileBaseUrl.appendSlash}$x").getOrElse("USERNAME NOT PROVIDED")

    def getDisplayName = this.displayName.getOrElse("DISPLAY NAME NOT PROVIDED")
  }

  

  case class TeamDetails(description: Option[String],
                         location: Option[String],
                         organisation: Option[String],
                         documentation: Option[String],
                         slack: Option[String],
                         team: String)


  implicit val teamMemberFormat = Json.format[TeamMember]
  implicit val teamDetailsReads = Json.reads[TeamDetails]


}


