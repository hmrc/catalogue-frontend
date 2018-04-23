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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementAuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

@Singleton
class AuthService @Inject()(userManagementAuthConnector: UserManagementAuthConnector) {

  import AuthService._

  def authenticate(username: String, password: String)(
    implicit hc: HeaderCarrier): Future[Either[UmpUnauthorized, UmpToken]] =
    userManagementAuthConnector.authenticate(username, password)
}

object AuthService {
  final case class UmpToken(value: String) extends AnyVal
  type UmpUnauthorized = UmpUnauthorized.type
  case object UmpUnauthorized
}
