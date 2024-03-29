/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.Logging
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.users.{CreateUserRequest, UmpTeam, User}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserManagementConnector @Inject()(
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
)(implicit
  ec: ExecutionContext
) extends Logging {

  import HttpReads.Implicits._

  private val baseUrl = servicesConfig.baseUrl("user-management")

  def getTeam(team: TeamName)(implicit hc: HeaderCarrier): Future[UmpTeam] = {
    implicit val ltr: Reads[UmpTeam] = UmpTeam.reads

    httpClientV2
      .get(url"$baseUrl/user-management/teams/${team.asString}")
      .execute[UmpTeam]
  }

  def getAllTeams()(implicit hc: HeaderCarrier): Future[Seq[UmpTeam]] = {
    implicit val ltr: Reads[UmpTeam] = UmpTeam.reads

    httpClientV2
      .get(url"$baseUrl/user-management/teams")
      .execute[Seq[UmpTeam]]
  }

  def getAllUsers(team: Option[TeamName] = None)(implicit hc: HeaderCarrier): Future[Seq[User]] = {
    val url: URL = url"$baseUrl/user-management/users?team=${team.map(_.asString)}"

    implicit val ur: Reads[User] = User.reads

    httpClientV2
      .get(url)
      .execute[Seq[User]]
      .recover {
        case e =>
          logger.warn(s"Unexpected response from user-management $url - ${e.getMessage}", e)
          Seq.empty[User]
      }
  }

  def getUser(username: String)(implicit hc: HeaderCarrier): Future[Option[User]] = {
    implicit val ur: Reads[User] = User.reads

    httpClientV2
      .get(url"$baseUrl/user-management/users/$username")
      .execute[Option[User]]
  }

  def createUser(userRequest: CreateUserRequest)(implicit hc: HeaderCarrier): Future[Unit] = {
    val url: URL = url"$baseUrl/user-management/create-user"

    httpClientV2
      .post(url)
      .withBody(Json.toJson(userRequest)(CreateUserRequest.writes))
      .execute[Either[UpstreamErrorResponse, Unit]]
      .flatMap {
        case Right(res) => Future.successful(res)
        case Left(err)  => Future.failed(new RuntimeException(s"Request to $url failed with upstream error: ${err.message}"))
      }
  }
}
