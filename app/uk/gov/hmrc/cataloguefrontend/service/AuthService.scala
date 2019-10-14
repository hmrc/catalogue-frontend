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
import play.api.Logger
import uk.gov.hmrc.cataloguefrontend.actions.UmpAuthenticatedRequest
import uk.gov.hmrc.cataloguefrontend.connector.model.Username
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, Team, TeamsAndRepositoriesConnector, UserManagementAuthConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.{UmpToken, UmpUnauthorized, UmpUserId}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.{DisplayName, UMPError}
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
      requiredServiceNames: NonEmptyList[String]
    )(implicit request: UmpAuthenticatedRequest[A]
             , hc     : HeaderCarrier
             ): Future[Either[ServiceForbidden, Unit]] =
    for {
      teams <- teamsAndRepositoriesConnector.teamsWithRepositories.map(_.toList)

      // services owned by user's teams
      // services that are not required have been filtered out, so should be a subset of requiredServiceNames
      ownedServiceNames: List[String] <-
          teams.traverse { team =>
            val teamServiceNames = team.repos.getOrElse(Map.empty).get(RepoType.Service.toString).getOrElse(Seq.empty).toList
            val providedServices = requiredServiceNames.toList.intersect(teamServiceNames)
            if (providedServices.isEmpty)
              Future(List.empty)
            else
              userManagementConnector.getTeamMembersFromUMP(team.name)
                .map {
                  case Left(UMPError.HTTPError(404))
                                          => // Not all teams returned from TeamsAndRepositories (github) exist in UMP
                                             Logger.debug(s"Team `${team.name}` not found in UMP")
                                             List.empty
                  case Left(umpErr)       => sys.error(s"Failed to lookup team members for team `${team.name}` from ump: $umpErr")
                  case Right(teamMembers) => if (teamMembers.flatMap(_.username).contains(request.username.value)) providedServices else List.empty
                }
          }.map(_.flatten)

      missingServices = requiredServiceNames.toList.diff(ownedServiceNames)

    } yield NonEmptyList.fromList(missingServices)
              .map(s => Left(ServiceForbidden(s))).getOrElse(Right(()))
}

object AuthService {

  final case class TokenAndDisplayName(
      token      : UmpToken
    , displayName: DisplayName
    )

  case class ServiceForbidden(serviceName: NonEmptyList[String])
}
