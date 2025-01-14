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

package uk.gov.hmrc.cataloguefrontend.users

import cats.data.EitherT
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, RequestHeader, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.config.UserManagementPortalConfig
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector
import uk.gov.hmrc.cataloguefrontend.model.{TeamName, UserName}
import uk.gov.hmrc.cataloguefrontend.users.view.html.{UserInfoPage, UserListPage, UserSearchResults, VpnRequestSentPage}
import uk.gov.hmrc.cataloguefrontend.view.html.error_404_template
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, IAAction, Resource, ResourceType, Retrieval}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class UsersController @Inject()(
  userManagementConnector: UserManagementConnector
, userInfoPage           : UserInfoPage
, userListPage           : UserListPage
, userSearchResults      : UserSearchResults
, vpnRequestSentPage     : VpnRequestSentPage
, umpConfig              : UserManagementPortalConfig
, override val mcc       : MessagesControllerComponents
, override val auth      : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders
     with play.api.i18n.I18nSupport
     with Logging:

  def user(username: UserName): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      (for
        retrieval   <- EitherT.liftF:
                         auth.verify(Retrieval.locations(
                           resourceType = Some(ResourceType("catalogue-frontend")),
                           action       = Some(IAAction("CREATE_USER"))
                         ))
        userTooling <- EitherT.liftF[Future, Result, Either[String, UserAccess]]:
                         userManagementConnector.getUserAccess(username)
                           .map(userAccess => Right(userAccess))
                           .recover:
                              case e: UpstreamErrorResponse =>
                                logger.warn(s"Received a ${e.statusCode} response when getting access for user: $username. " +
                                  s"Error: ${e.message}.")
                                Left("Unable to access User Management Portal to retrieve tooling. Please check again later.")
        userOpt     <- EitherT.liftF[Future, Result, Option[User]]:
                         userManagementConnector.getUser(username)
       yield
        userOpt match
          case Some(user) =>
            val umpProfileUrl = s"${umpConfig.userManagementProfileBaseUrl}/${user.username.asString}"
            Ok(userInfoPage(isAdminForUser(retrieval, user), userTooling, user, umpProfileUrl))
          case None =>
            NotFound(error_404_template())
      ).merge

  private def isAdminForUser(retrieval: Option[Set[Resource]], user: User): Boolean =
    val teams = retrieval.fold(Set.empty[TeamName])(_.map(_.resourceLocation.value.stripPrefix("teams/")).map(TeamName.apply))
    teams.contains(TeamName("*")) || teams.exists(user.teamNames.contains) // Global admin or admin for user's team

  val requestNewVpnCert: Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      auth.verify(Retrieval.locations(
        resourceType = Some(ResourceType("catalogue-frontend")),
        action       = Some(IAAction("CREATE_USER"))
      )).flatMap: retrieval =>
        request.body.asFormUrlEncoded.flatMap(_.get("username").flatMap(_.headOption)).map(UserName.apply)
          .fold(
            Future.failed(RuntimeException("Could not request a new vpn certificate as hidden username form field was empty"))
          ): username =>
            userManagementConnector.getUser(username).flatMap:
              case Some(user) =>
                if(isAdminForUser(retrieval, user)) then
                  userManagementConnector.requestNewVpnCert(username)
                    .map(ticketOpt => Created(vpnRequestSentPage(user, ticketOpt)))
                    .recover:
                      case NonFatal(e) =>
                        logger.error(s"Error requesting new VPN certificate: ${e.getMessage}", e)
                        Redirect(routes.UsersController.user(username)).flashing("error" -> "Error requesting VPN Certificate. Contact #team-platops")
                else
                  Future.successful(Redirect(routes.UsersController.user(username)).flashing("error" -> "Permission denied"))
              case _          =>
                Future.successful(Redirect(routes.UsersController.user(username)).flashing("error" -> "Unable to find user"))

  val users: Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      for
        retrieval      <- auth.verify:
                            Retrieval.locations(
                              resourceType = Some(ResourceType("catalogue-frontend")),
                              action       = Some(IAAction("CREATE_USER"))
                            )
        canCreateUsers =  retrieval.exists(_.nonEmpty)
      yield Ok(userListPage(canCreateUsers))

  def userSearch(query: String): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      userManagementConnector
        .searchUsers:
          query
            .split("\\s+") // query is space-delimited
            .toIndexedSeq
        .map(matches => Ok(userSearchResults(matches)))

end UsersController
