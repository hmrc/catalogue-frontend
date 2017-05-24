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

import java.io.{File, InputStream}

import cats.Applicative
import cats.data.EitherT
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.FutureHelpers.withTimerAndCounter
import uk.gov.hmrc.cataloguefrontend.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.{Await, Future}
import cats.instances.future._
import play.api.data.validation.ValidationError
import uk.gov.hmrc.cataloguefrontend.UserManagementConnector.{ConnectionError, getAllUsersFromUMP}

import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global


trait UserManagementConnector extends UserManagementPortalLink {
  val http: HttpGet

  import UserManagementConnector._

  implicit val teamMemberFormat = Json.format[TeamMember]
  implicit val teamDetailsReads = Json.reads[TeamDetails]

  implicit val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse) = response
  }


  def getTeamMembersForTeams(teamNames: Seq[String])(implicit hc: HeaderCarrier): Future[Map[String, Either[UMPError, Seq[TeamMember]]]] = {

    def getTeamMembers(teamName: String) = getTeamMembersFromUMP(teamName).map(umpErrorOrTeamMembers => (teamName, umpErrorOrTeamMembers))

    Future.sequence(teamNames.map(getTeamMembers)).map(_.toMap)
  }


  def getTeamMembersFromUMP(team: String)(implicit hc: HeaderCarrier): Future[Either[UMPError, Seq[TeamMember]]] = {
    val newHeaderCarrier = hc.withExtraHeaders("requester" -> "None", "Token" -> "None")

//    val url = s"$userManagementBaseUrl/v1/organisations/mdtp/teams/$team/members"
    val url = s"$userManagementBaseUrl/v2/organisations/teams/$team/members"

    def isHttpCodeFailure: Option[(Either[UMPError, _]) => Boolean] =
      Some { errorOrMembers: Either[UMPError, _] =>
      errorOrMembers match {
        case Left(HTTPError(code)) if code != 200 => true
        case _ => false
      }
    }

    val eventualConnectorErrorOrTeamMembers = http.GET[HttpResponse](url)(httpReads, newHeaderCarrier).map { response =>
      response.status match {
        case 200 => extractMembers(team, response)
        case httpCode => Left(HTTPError(httpCode))
      }
    }

    withTimerAndCounter("ump") (eventualConnectorErrorOrTeamMembers, isHttpCodeFailure)
      .recover {
      case ex =>
        Logger.error(s"An error occurred when connecting to $userManagementBaseUrl: ${ex.getMessage}", ex)
        Left(ConnectionError(ex))
    }
  }


//  def getAllUsersFromUMP_fromfile(): Future[Either[UMPError, Seq[TeamMember]]] = {
//
//    val stream: InputStream = getClass.getResourceAsStream("/ump/all-users.json")
//
//    val fileContents = Source.fromInputStream(stream).getLines().mkString("\n")
////    println(fileContents)
//
//    val teamMembers = (Json.parse(fileContents) \ "users").validate[Seq[TeamMember]]
//
//    teamMembers match {
//      case JsSuccess(tms, _) =>
//        println(tms.size)
//        Future.successful(Right(tms))
//      case JsError(errors) => Future.successful(Left(ConnectionError(new RuntimeException(errors.toString()))))
//    }
//  }


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

    val eventualErrorOrTeamMembers = http.GET[HttpResponse](url)(httpReads, newHeaderCarrier).map { response =>
      response.status match {
        case 200 => Right(extractUsers(response))
        case httpCode => Left(HTTPError(httpCode))
      }
    }

    withTimerAndCounter("ump") (eventualErrorOrTeamMembers, isHttpCodeFailure)
      .recover {
        case ex =>
          Logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Left(ConnectionError(ex))
      }
  }




  def getTeamDetails(team: String)(implicit hc: HeaderCarrier): Future[Either[UMPError, TeamDetails]] = {
    val newHeaderCarrier = hc.withExtraHeaders("requester" -> "None", "Token" -> "None")
//    val url = s"$userManagementBaseUrl/v1/organisations/mdtp/teams/$team"
    val url = s"$userManagementBaseUrl/v2/organisations/teams/$team"
    withTimerAndCounter("ump-teamdetails") {
      http.GET[HttpResponse](url)(httpReads, newHeaderCarrier).map { response =>
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

   Option(response.json).flatMap{ js => js.asOpt[T] } match {
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



}

object UserManagementConnector extends UserManagementConnector {
  override val http = WSHttp

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
                        username: Option[String])

  case class TeamDetails(description: Option[String],
                         location: Option[String],
                         organisation: Option[String],
                         documentation: Option[String],
                         slack: Option[String],
                         team: String)




}

import scala.concurrent.duration._

//object Runner {
//  def main(args: Array[String]): Unit = {
//    val allUsers: Future[Either[UserManagementConnector.UMPError, Seq[UserManagementConnector.TeamMember]]] = UserManagementConnector.getAllUsersFromUMP()
//    Await.result(allUsers, 4 seconds) match {
//      case Right(users) => println(users)
//      case Left(e) => println(e)
//      case x => println(x)
//    }
//
////    Thread.sleep(10000)
//  }
//}
