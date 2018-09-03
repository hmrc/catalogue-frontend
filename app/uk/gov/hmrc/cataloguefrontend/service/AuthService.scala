/*
 * Copyright 2018 HM Revenue & Customs
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

import cats.data.EitherT
import cats.implicits._
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.{UmpToken, UmpUnauthorized, UmpUserId}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.DisplayName
import uk.gov.hmrc.cataloguefrontend.connector.{UserManagementAuthConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.service.AuthService.TokenAndDisplayName
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.concurrent.Future

@Singleton
class AuthService @Inject()(
  userManagementAuthConnector: UserManagementAuthConnector,
  userManagementConnector: UserManagementConnector) {

  def authenticate(username: String, password: String)(
    implicit hc: HeaderCarrier): Future[Either[UmpUnauthorized, TokenAndDisplayName]] = {

    def getDisplayNameOrDefaultToUserId(userId: UmpUserId): Future[Either[UmpUnauthorized, DisplayName]] =
      userManagementConnector.getDisplayName(userId).map {
        case Some(displayName) => Right(displayName)
        case None              => Right(DisplayName(userId.value))
      }

    (for {
      umpAuthData <- EitherT(userManagementAuthConnector.authenticate(username, password))
      displayName <- EitherT(getDisplayNameOrDefaultToUserId(umpAuthData.userId))
    } yield {
      TokenAndDisplayName(umpAuthData.token, displayName)
    }).value

  }

}

object AuthService {

  final case class TokenAndDisplayName(
    token: UmpToken,
    displayName: DisplayName
  )

}
