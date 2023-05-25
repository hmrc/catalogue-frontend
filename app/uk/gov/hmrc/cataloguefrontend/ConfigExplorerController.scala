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

package uk.gov.hmrc.cataloguefrontend

import play.api.data.Forms.nonEmptyText
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.service.ConfigService
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.ConfigExplorerPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfigExplorerController @Inject()(
  override val mcc   : MessagesControllerComponents
 ,override val auth  : FrontendAuthComponents
 ,configService      : ConfigService
 ,configExplorerPage : ConfigExplorerPage
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  def landing: Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      Future.successful(Ok(configExplorerPage(ConfigExplorerSearch.form)))
    }

  def search(key: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      ConfigExplorerSearch.form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(Ok(configExplorerPage(formWithErrors))),
          query => configService
            .searchAppliedConfig(query.key)
            .map { results =>
              Ok(configExplorerPage(
                ConfigExplorerSearch.form.fill(query),
                results,
                key
              ))
            }
        )
    }

}

case class ConfigExplorerSearch(key: String)

object ConfigExplorerSearch {
  import play.api.data.Form
  import play.api.data.Forms.mapping

  lazy val form: Form[ConfigExplorerSearch] = Form(
    mapping(
      "key"-> nonEmptyText(minLength = 3)
    )(ConfigExplorerSearch.apply)(ConfigExplorerSearch.unapply)
  )
}
