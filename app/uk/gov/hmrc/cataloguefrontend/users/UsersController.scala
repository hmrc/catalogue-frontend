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

import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.config.UserManagementPortalConfig
import uk.gov.hmrc.cataloguefrontend.connector.{TeamsAndRepositoriesConnector, UserManagementConnector}
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.error_404_template
import views.html.users.{UserInfoPage, UserListPage}

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
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  def user(username: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      userManagementConnector.getUser(username)
        .map {
          case Some(user) =>
            val umpProfileUrl = s"${umpConfig.userManagementProfileBaseUrl}/${user.username}"
            Ok(userInfoPage(user, umpProfileUrl))
          case None =>
            NotFound(error_404_template())
        }
    }

  def allUsers(username: Option[String]): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      UsersListFilter.form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(userListPage(Seq.empty, Seq.empty, formWithErrors))),
          validForm      => for {
                              users <- userManagementConnector.getAllUsers(team = validForm.team)
                              teams <- teamsAndRepositoriesConnector.allTeams().map(_.sortBy(_.name.asString.toLowerCase))
                            }
          yield Ok(userListPage(users, teams, UsersListFilter.form.fill(validForm.copy(username = username))))
        )
    }
}

object UsersController {
  val maxRows = 500
}

case class UsersListFilter(
  team    : Option[String] = None,
  username: Option[String] = None
)

object UsersListFilter {
  lazy val form: Form[UsersListFilter] = Form(
    mapping(
      "team"     -> optional(text),
      "username" -> optional(text)
    )(UsersListFilter.apply)(UsersListFilter.unapply)
  )
}
