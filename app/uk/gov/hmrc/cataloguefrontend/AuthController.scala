/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend

import javax.inject.{Inject, Singleton}
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

@Singleton
class AuthController @Inject() (
  mcc: MessagesControllerComponents
) extends FrontendController(mcc) {

  def signIn(targetUrl: Option[String]) =
    // TODO provide this from internal-auth-client?
    Action(
      Redirect(
        s"/internal-auth-frontend/sign-in",
        Map("continue_url" -> Seq(targetUrl.getOrElse(routes.CatalogueController.index.url)))
      )
    )

  val signOut =
    Action(
      Redirect(routes.CatalogueController.index).withNewSession
    )
}
