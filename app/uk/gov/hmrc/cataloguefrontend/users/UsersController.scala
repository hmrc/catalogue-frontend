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
import play.api.data.{Form, Forms}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.config.UserManagementPortalConfig
import uk.gov.hmrc.cataloguefrontend.connector.{GitHubTeam, TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.cataloguefrontend.model.{TeamName, UserName}
import uk.gov.hmrc.cataloguefrontend.users.view.html.{UserInfoPage, UserListPage}
import uk.gov.hmrc.cataloguefrontend.view.html.error_404_template
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, IAAction, Resource, ResourceType, Retrieval}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UsersController @Inject()(
  userManagementConnector      : UserManagementConnector
, teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
, userInfoPage                 : UserInfoPage
, userListPage                 : UserListPage
, umpConfig                    : UserManagementPortalConfig
, override val mcc             : MessagesControllerComponents
, override val auth            : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders
     with play.api.i18n.I18nSupport
     with Logging:

  def user(username: UserName): Action[AnyContent] =
    BasicAuthAction.async:
      implicit request =>
      (for
        retrieval   <- EitherT.liftF(
                         auth.verify(Retrieval.locations(
                           resourceType = Some(ResourceType("catalogue-frontend")),
                           action = Some(IAAction("CREATE_USER"))
                         ))
                       )
        userTooling <- EitherT.liftF[Future, Result, Either[String, UserAccess]](userManagementConnector.getUserAccess(username)
                         .map(userAccess => Right(userAccess))
                         .recover:
                            case e: UpstreamErrorResponse =>
                              logger.warn(s"Received a ${e.statusCode} response when getting access for user: $username. " +
                                s"Error: ${e.message}.")
                              Left("Unable to access User Management Portal to retrieve tooling. Please check again later.")
                        )
        userOpt     <- EitherT.liftF[Future, Result, Option[User]](userManagementConnector.getUser(username))
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


  def allUsers(username: Option[UserName]): Action[AnyContent] =
    BasicAuthAction.async:
      implicit request =>
      (for
         retrieval   <- EitherT.liftF(
                          auth.verify(Retrieval.locations(
                            resourceType = Some(ResourceType("catalogue-frontend")),
                            action       = Some(IAAction("CREATE_USER"))
                          ))
                        )
         isTeamAdmin =  retrieval.exists(_.nonEmpty)
         form        <- EitherT.fromEither[Future](UsersListFilter.form.bindFromRequest().fold(
                          formWithErrors => Left(
                            BadRequest(
                              userListPage(isTeamAdmin, Seq.empty, Seq.empty, formWithErrors)
                            )
                          ),
                          validForm => Right(validForm)
                        ))
         users       <- EitherT.liftF[Future, Result, Seq[User]](userManagementConnector.getAllUsers(team = form.team))
         teams       <- EitherT.liftF[Future, Result, Seq[GitHubTeam]](teamsAndRepositoriesConnector.allTeams().map(_.sortBy(_.name.asString.toLowerCase)))
       yield
         Ok(userListPage(isTeamAdmin, users, teams, UsersListFilter.form.fill(form.copy(username = username))))
    ).merge

end UsersController

object UsersController:
  val maxRows = 500

case class UsersListFilter(
  team    : Option[TeamName] = None,
  username: Option[UserName] = None
)

object UsersListFilter:
  lazy val form: Form[UsersListFilter] =
    Form(
      Forms.mapping(
        "team"     -> Forms.optional(Forms.of[TeamName]),
        "username" -> Forms.optional(Forms.of[UserName])
      )(UsersListFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
    )
