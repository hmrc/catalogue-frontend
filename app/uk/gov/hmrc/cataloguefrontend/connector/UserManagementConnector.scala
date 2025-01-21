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
import play.api.libs.json.*
import play.api.libs.ws.writeableOf_JsValue
import uk.gov.hmrc.cataloguefrontend.model.{TeamName, UserName}
import uk.gov.hmrc.cataloguefrontend.users.{CreateUserRequest, EditUserAccessRequest, ResetLdapPassword, UmpTeam, User, UserAccess}
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
)(using
  ExecutionContext
) extends Logging:

  import HttpReads.Implicits._

  private val baseUrl = servicesConfig.baseUrl("user-management")

  def getTeam(team: TeamName)(using HeaderCarrier): Future[UmpTeam] =
    given Reads[UmpTeam] = UmpTeam.reads
    httpClientV2
      .get(url"$baseUrl/user-management/teams/${team.asString}")
      .execute[UmpTeam]

  def getAllTeams()(using HeaderCarrier): Future[Seq[UmpTeam]] =
    given Reads[UmpTeam] = UmpTeam.reads
    httpClientV2
      .get(url"$baseUrl/user-management/teams")
      .execute[Seq[UmpTeam]]

  def getAllUsers(team: Option[TeamName] = None)(using HeaderCarrier): Future[Seq[User]] =
    val url: URL = url"$baseUrl/user-management/users?team=${team.map(_.asString)}"
    given Reads[User] = User.reads
    httpClientV2
      .get(url)
      .execute[Seq[User]]
      .recover:
        case e =>
          logger.warn(s"Unexpected response from user-management $url - ${e.getMessage}", e)
          Seq.empty[User]

  def searchUsers(searchTerms: Seq[String], includeDeleted: Boolean)(using HeaderCarrier): Future[Seq[User]] =
    given Reads[User] = User.reads
    httpClientV2
      .get(url"$baseUrl/user-management/users-search?query=$searchTerms&includeDeleted=$includeDeleted")
      .execute[Seq[User]]

  def getUser(username: UserName)(using HeaderCarrier): Future[Option[User]] =
    given Reads[User] = User.reads
    httpClientV2
      .get(url"$baseUrl/user-management/users/${username.asString}")
      .execute[Option[User]]

  def getUserAccess(username: UserName)(using HeaderCarrier): Future[UserAccess] =
    given Reads[UserAccess] = UserAccess.format
    httpClientV2
      .get(url"$baseUrl/user-management/users/${username.asString}/access")
      .execute[Option[UserAccess]]
      .map(_.getOrElse(UserAccess.empty))

  def editUserAccess(userRequest: EditUserAccessRequest)(using HeaderCarrier): Future[Unit] =
    val url: URL = url"$baseUrl/user-management/edit-user-access"
    httpClientV2
      .post(url)
      .withBody(Json.toJson(userRequest)(EditUserAccessRequest.writes))
      .execute[Either[UpstreamErrorResponse, Unit]]
      .flatMap:
        case Right(res) => Future.successful(res)
        case Left(err)  => Future.failed(RuntimeException(s"Request to $url failed with upstream error: ${err.message}"))

  def createUser(userRequest: CreateUserRequest)(using HeaderCarrier): Future[Unit] =
    val url: URL = url"$baseUrl/user-management/create-user"
    httpClientV2
      .post(url)
      .withBody(Json.toJson(userRequest)(CreateUserRequest.writes))
      .execute[Either[UpstreamErrorResponse, Unit]]
      .flatMap:
        case Right(res) => Future.successful(res)
        case Left(err)  => Future.failed(RuntimeException(s"Request to $url failed with upstream error: ${err.message}"))

  def requestNewVpnCert(username: UserName)(using HeaderCarrier): Future[Option[String]] =
    val url: URL = url"$baseUrl/user-management/users/${username.asString}/vpn"
    httpClientV2
      .post(url)
      .execute[Either[UpstreamErrorResponse, JsValue]]
      .flatMap:
        case Right(json) => Future.successful((json \ "ticket_number").validate[String].asOpt)
        case Left(err)   => Future.failed(RuntimeException(s"Request to $url failed with upstream error: ${err.message}"))

  def resetLdapPassword(resetLdapPassword: ResetLdapPassword)(using HeaderCarrier): Future[Option[String]] =
    val url: URL = url"$baseUrl/user-management/reset-ldap-password"
    httpClientV2
      .post(url)
      .withBody(Json.toJson(resetLdapPassword)(ResetLdapPassword.writes))
      .execute[Either[UpstreamErrorResponse, JsValue]]
      .flatMap:
        case Right(json) => Future.successful((json \ "ticket_number").validate[String].asOpt)
        case Left(err)   => Future.failed(RuntimeException(s"Request to $url failed with upstream error: ${err.message}"))