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

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.config.UserManagementPortalConfig
import uk.gov.hmrc.cataloguefrontend.connector.UserManagementConnector
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.error_404_template
import views.html.users.UserInfoPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class UsersController @Inject()(
  userManagementConnector: UserManagementConnector
, userInfoPage           : UserInfoPage
, umpConfig              : UserManagementPortalConfig
, override val mcc       : MessagesControllerComponents
, override val auth      : FrontendAuthComponents
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

}
