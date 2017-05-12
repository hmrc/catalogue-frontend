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

import cats.Applicative
import cats.data.EitherT
import play.api.Logger
import play.api.libs.json.{JsValue, Json, Reads}
import uk.gov.hmrc.cataloguefrontend.FutureHelpers.withTimerAndCounter
import uk.gov.hmrc.cataloguefrontend.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future
import cats.instances.future._


trait UserManagementConnector extends UserManagementPortalLink {
  val http: HttpGet

  import UserManagementConnector._

  implicit val teamMemberReads = Json.reads[TeamMember]
  implicit val teamDetailsReads = Json.reads[TeamDetails]

  implicit val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
    override def read(method: String, url: String, response: HttpResponse) = response
  }


//  def getTeamMembersArmin(teams: Seq[String])(implicit hc: HeaderCarrier): Future[Either[UMPError, Map[String, Seq[TeamMember]]]] = {
//
//    val xs: Future[Seq[(String, Either[UMPError, Seq[TeamMember]])]] = Future.sequence(teams.distinct.map((teamName: String) => getTeamMembersFromUMP(teamName).map(x => (teamName, x))))
//
//
//    val yy: Future[Either[ConnectorErrors, Map[String, Seq[TeamMember]]]] = xs.map{ (a: Seq[(String, Either[UMPError, Seq[TeamMember]])]) =>
//      val eitherErrorOrMapOfTeamNameAndMembers = a.map { case (teamName, e) => e.right.map(teamMembers => (teamName, teamMembers)).right.map(Map(_)) }
//
//      val partition = eitherErrorOrMapOfTeamNameAndMembers.partition(_.isLeft)
//
//      partition match {
//        case (Nil, teamMaps) =>
//          val stringToMemberses = for {Right(i) <- teamMaps} yield i
//          val left = stringToMemberses.foldLeft(Map.empty[String, Seq[TeamMember]])((acc, b) => b ++ acc)
//          Right(left)
//        case (errors, _) => Left((for {Left(e) <- errors} yield e).foldLeft(ConnectorErrors(Nil))((acc, b) => ConnectorErrors(acc.errors :+ b)))
//      }
//    }
//
//    yy.map(println)
//    yy
//  }


  def getTeamMembersForTeams(teamNames: Seq[String])(implicit hc: HeaderCarrier): Future[Map[String, Either[UMPError, Seq[TeamMember]]]] =
      Future.sequence(
        teamNames.map(getTeamMembers)
      ).map(_.toMap)


  private def getTeamMembers(teamName: String)(implicit hc: HeaderCarrier) = {
    getTeamMembersFromUMP(teamName).map(umpErrorOrteamMembers => (teamName, umpErrorOrteamMembers))
  }

//  def getTeamMembersNew(teams: Seq[String])(implicit hc: HeaderCarrier): Future[Either[UMPError, Map[String, Seq[TeamMember]]]] = {
//
//
//    val xs: Future[Seq[(String, Either[UMPError, Seq[TeamMember]])]] = Future.sequence(teams
//      .map(teamName => getTeamMembersFromUMP(teamName).map((x: Either[UMPError, Seq[TeamMember]]) => (teamName, x))))
//
//
//    val y: Future[Seq[Either[UMPError, Map[String, Seq[TeamMember]]]]] = xs.map{ s =>
//      s.map {
//        case (k, v) => v.right.map(s => (k,s)).right.map(Map(_))
//      }
//    }
//
//    val blah: Future[Either[Seq[UMPError], Seq[Map[String, Seq[TeamMember]]]]] = y.map { (s: Seq[Either[UMPError, Map[String, Seq[TeamMember]]]]) =>
//      s.partition(_.isLeft) match {
//        case (Nil,  teamMaps) => Right(for(Right(i) <- teamMaps.view) yield i)
//        case (connectionErrors, _) => Left(for(Left(s) <- connectionErrors.view) yield s)
//      }
//    }
//
//    import cats.syntax.applicative._
//
//    import cats.Monoid
//
////    val yy: EitherT[Future, ConnectorError, Map[String, Seq[TeamMember]]] =
////      y.fold(Seq.empty[(String, Seq[TeamMember])].pure[EitherTMap])(Monoid[EitherT[Future, ConnectorError, Seq[(String, Seq[TeamMember])]]].combine)
////        .map(_.toMap)
////
////    yy.value
//
////    y.foldLeft(Map[String, Seq[TeamMember]].pure[EitherTMap])((acc, next: EitherT[Future, ConnectorError, Seq[TeamMember]]) => acc.combine())
//    ???
//  }

  def getTeamMembersFromUMP(team: String)(implicit hc: HeaderCarrier): Future[Either[UMPError, Seq[TeamMember]]] = {
    val newHeaderCarrier = hc.withExtraHeaders("requester" -> "None", "Token" -> "None")

    val url = s"$userManagementBaseUrl/v1/organisations/mdtp/teams/$team/members"

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


  def getTeamDetails(team: String)(implicit hc: HeaderCarrier): Future[Either[UMPError, TeamDetails]] = {
    val newHeaderCarrier = hc.withExtraHeaders("requester" -> "None", "Token" -> "None")
    val url = s"$userManagementBaseUrl/v1/organisations/mdtp/teams/$team"
    withTimerAndCounter("ump-teamdetails") {
      http.GET[HttpResponse](url)(httpReads, newHeaderCarrier).map { response =>
        response.status match {
          case 200 => extractData[TeamDetails](team, response) {
            json =>
              (json \\ "organisations").lastOption.flatMap(x => (x \ "data").toOption)
          }
          case httpCode => Left(HTTPError(httpCode))
        }
      }.recover {
        case ex =>
          Logger.error(s"An error occurred when connecting to $userManagementBaseUrl: ${ex.getMessage}", ex)
          Left(ConnectionError(ex))
      }
    }

  }


  def extractData[T](team: String, response: HttpResponse)(extractor: JsValue => Option[JsValue])(implicit rd: Reads[T]): Either[UMPError, T] = {

   extractor(response.json).flatMap{ js => js.asOpt[T] } match {
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
