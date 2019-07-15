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

import cats.data.{EitherT, OptionT}
import cats.implicits._
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector.{UmpToken, UmpUnauthorized, UmpUserId}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector.DisplayName
import uk.gov.hmrc.cataloguefrontend.connector.{UserManagementAuthConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.service.AuthService.TokenAndDisplayName
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthService @Inject()(
  userManagementAuthConnector: UserManagementAuthConnector,
  userManagementConnector    : UserManagementConnector
)(implicit val ec: ExecutionContext) {

  def authenticate(username: String, password: String)(
    implicit hc: HeaderCarrier): Future[Either[UmpUnauthorized, TokenAndDisplayName]] =
    (for {
       umpAuthData    <- EitherT(userManagementAuthConnector.authenticate(username, password))
       optDisplayName <- EitherT.liftF[Future, UmpUnauthorized, Option[DisplayName]](userManagementConnector.getDisplayName(umpAuthData.userId))
       displayName    =  optDisplayName.getOrElse(DisplayName(umpAuthData.userId.value))
     } yield TokenAndDisplayName(umpAuthData.token, displayName)
    ).value
}

object AuthService {

  final case class TokenAndDisplayName(
    token      : UmpToken,
    displayName: DisplayName
  )
}
