/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.service

import cats.data.{EitherT, NonEmptyList, OptionT}
import cats.implicits._
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.actions.UmpAuthenticatedRequest
import uk.gov.hmrc.cataloguefrontend.connector.model.Username
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, Team, TeamsAndRepositoriesConnector, UserManagementAuthConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.{UmpToken, UmpUnauthorized, UmpUserId}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.DisplayName
import uk.gov.hmrc.http.HeaderCarrier


import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthService @Inject()(
    userManagementAuthConnector  : UserManagementAuthConnector
  , userManagementConnector      : UserManagementConnector
  , teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
  )(implicit val ec: ExecutionContext) {

  import AuthService._

  def authenticate(username: String, password: String)(
    implicit hc: HeaderCarrier): Future[Either[UmpUnauthorized, TokenAndDisplayName]] =
    (for {
       umpAuthData    <- EitherT(userManagementAuthConnector.authenticate(username, password))
       optDisplayName <- EitherT.liftF[Future, UmpUnauthorized, Option[DisplayName]](userManagementConnector.getDisplayName(umpAuthData.userId))
       displayName    =  optDisplayName.getOrElse(DisplayName(umpAuthData.userId.value))
     } yield TokenAndDisplayName(umpAuthData.token, displayName)
    ).value

  /** Check username belongs to teams which own services */
  def authorizeServices[A](
      serviceNames: NonEmptyList[String]
    )(implicit request: UmpAuthenticatedRequest[A]
             , hc     : HeaderCarrier
             ): Future[Either[ServiceForbidden, Unit]] =
    for {
      teams <- teamsAndRepositoriesConnector.allTeams.map(_.toList)

      teamServicesMap: Map[String/*#TeamName*/, List[String/*#ServiceName*/]]
            = teams.map { team =>
                val teamServiceNames = team.repos.getOrElse(Map.empty).get(RepoType.Service.toString).getOrElse(Seq.empty).toList
                (team.name, teamServiceNames)
              }.toMap

      // filter the above map for only the applicable teams and services
      // we can then check that the user belongs to all the teams (and report which services required it)
      requiredTeamsForServices : Map[String/*#TeamName*/, NonEmptyList[String/*#ServiceName*/]]
            = serviceNames.map { serviceName =>
                 teamServicesMap.find { case (teamName, teamServiceNames) => teamServiceNames.contains(serviceName) } match {
                   case None                => sys.error(s"Couldn't find owning team for service `$serviceName`")
                   case Some((teamName, _)) => (serviceName, teamName)
                 }
               }
               .groupBy(_._2).mapValues(_.map(_._1))

      // check user belongs to each team, and if not, report forbidden services
      res   <- requiredTeamsForServices.toList.foldM[Future, Either[ServiceForbidden, Unit]](Either.right[ServiceForbidden, Unit](())) {
                 case (acc, (teamName, teamServiceNames)) =>
                   userManagementConnector.getTeamMembersFromUMP(teamName)
                      .map {
                        case Left(umpErr)       => sys.error(s"Failed to lookup team members from ump: $umpErr")
                        case Right(teamMembers) =>
                          val teamMemberNames = teamMembers.map(_.username).collect { case Some(username) => Username(username) }.toList
                          if (teamMemberNames.contains(request.username))
                            acc
                          else acc match {
                            case Right(())                  => Left(ServiceForbidden(teamServiceNames))
                            case Left(ServiceForbidden(s1)) => Left(ServiceForbidden(teamServiceNames ::: s1))
                          }
                       }
               }
      } yield res
}

object AuthService {

  final case class TokenAndDisplayName(
      token      : UmpToken
    , displayName: DisplayName
    )

  case class ServiceForbidden(serviceName: NonEmptyList[String])
}
