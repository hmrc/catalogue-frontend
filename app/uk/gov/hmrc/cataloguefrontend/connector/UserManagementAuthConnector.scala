/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.connector

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.cataloguefrontend.connector.model.Username
import uk.gov.hmrc.http.{BadGatewayException, HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserManagementAuthConnector @Inject()(
  http                    : HttpClient,
  userManagementAuthConfig: UserManagementAuthConfig
)(implicit val ec: ExecutionContext) {


  import UserManagementAuthConnector._
  import userManagementAuthConfig._

  def authenticate(username: String, password: String)(
    implicit headerCarrier: HeaderCarrier): Future[Either[UmpUnauthorized, TokenAndUserId]] = {
    implicit val tokenAndUserIdReads: HttpReads[Either[UmpUnauthorized, TokenAndUserId]] =
      new HttpReads[Either[UmpUnauthorized, TokenAndUserId]] {
        override def read(
          method: String,
          url: String,
          response: HttpResponse): Either[UmpUnauthorized, TokenAndUserId] =
          response.status match {
            case OK =>
              val token  = UmpToken((response.json \ "Token").as[String])
              val userId = UmpUserId((response.json \ "uid").as[String])
              Right(TokenAndUserId(token, userId))

            case UNAUTHORIZED | FORBIDDEN =>
              Logger.info(
                s"Failed to authenticate for username: $username, response was: ${response.status} ${response.body}")
              Left(UmpUnauthorized)

            case other => throw new BadGatewayException(s"Received $other from $method to $url")
          }
      }

    http.POST[JsObject, Either[UmpUnauthorized, TokenAndUserId]](
      url  = s"$baseUrl/v1/login",
      body = Json.obj("username" -> username, "password" -> password)
    )
  }

  def getUser(umpToken: UmpToken)(implicit headerCarrier: HeaderCarrier): Future[Option[User]] = {
    val responseReads = new HttpReads[Option[User]] {
      def read(method: String, url: String, response: HttpResponse): Option[User] =
        response.status match {
          case OK                       => val username = (response.json \ "uid"   ).as[String]
                                           val groups   = (response.json \ "groups").as[List[String]]
                                           Some(User(Username(username), groups))
          case UNAUTHORIZED | FORBIDDEN => None
          case other                    => throw new BadGatewayException(s"Received $other from $method to $url")
        }
    }

    val headerCarrierWithToken = headerCarrier.withExtraHeaders("Token" -> umpToken.value)
    http.GET(s"$baseUrl/v1/login")(responseReads, headerCarrierWithToken, implicitly[ExecutionContext])
  }
}

@Singleton
class UserManagementAuthConfig @Inject()(servicesConfig: ServicesConfig) {
  val baseUrl: String = {
    val key = "user-management-auth.url"
    servicesConfig.getConfString(key, throw new Exception(s"Expected to find $key in configuration"))
  }
}

object UserManagementAuthConnector {

  final case class TokenAndUserId(
    token: UmpToken,
    userId: UmpUserId
  )

  final case class UmpUserId(value: String) {
    require(value.nonEmpty)
    override def toString: String = value
  }

  final case class UmpToken(value: String) {
    require(value.nonEmpty)
  }

  object UmpToken {
    val SESSION_KEY_NAME = "ump.token"
  }

  type UmpUnauthorized = UmpUnauthorized.type
  case object UmpUnauthorized

  final case class User(
      username: Username
    , groups  : List[String]
    )
}
