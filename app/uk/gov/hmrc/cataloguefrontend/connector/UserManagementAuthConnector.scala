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

package uk.gov.hmrc.cataloguefrontend.connector

import javax.inject.{Inject, Singleton}
import play.api.http.Status._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.cataloguefrontend.config.ServicesConfig
import uk.gov.hmrc.cataloguefrontend.service.AuthService.{UmpToken, UmpUnauthorized}
import uk.gov.hmrc.http.{BadGatewayException, HeaderCarrier, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class UserManagementAuthConnector @Inject()(http: HttpClient, servicesConfig: ServicesConfig) {

  import servicesConfig._

  private val serviceUrl = baseUrl("user-management-auth")

  def authenticate(username: String, password: String)(
    implicit headerCarrier: HeaderCarrier): Future[Either[UmpUnauthorized, UmpToken]] =
    http.POST[JsObject, Either[UmpUnauthorized, UmpToken]](
      url  = s"$serviceUrl/v1/login",
      body = Json.obj("username" -> username, "password" -> password)
    )

  private implicit val umpTokenReads: HttpReads[Either[UmpUnauthorized, UmpToken]] =
    new HttpReads[Either[UmpUnauthorized, UmpToken]] {
      override def read(method: String, url: String, response: HttpResponse): Either[UmpUnauthorized, UmpToken] =
        response.status match {
          case OK           => Right(UmpToken((response.json \ "token").as[String]))
          case UNAUTHORIZED => Left(UmpUnauthorized)
          case other        => throw new BadGatewayException(s"Received $other from $method to $url")
        }
    }
}
