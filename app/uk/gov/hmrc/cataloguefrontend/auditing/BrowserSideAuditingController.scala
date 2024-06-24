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

package uk.gov.hmrc.cataloguefrontend.auditing

import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc.{Action, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class BrowserSideAuditingController @Inject()(
  override val mcc : MessagesControllerComponents,
  override val auth: FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
  with CatalogueAuthBuilders {

  private val logger = Logger(getClass)

  /** This endpoint exists for implicit auditing */
  def sendAudit(): Action[JsValue] =
    BasicAuthAction(parse.tolerantJson) { implicit request =>
      val target = (request.body \ "target").asOpt[String]
      val referrer = request.headers.get(REFERER)
      if ((request.body \ "id").asOpt[String].isEmpty) logger.warn(s"HTML element for the link to $target from $referrer, has no id attribute")
      NoContent
    }
}
