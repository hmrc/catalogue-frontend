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

import javax.inject.Inject
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.ServiceDependenciesConnector
import uk.gov.hmrc.cataloguefrontend.service.BobbyService
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import views.html.BobbyExplorerPage

import scala.concurrent.ExecutionContext

class BobbyExplorerController @Inject() (
  override val mcc : MessagesControllerComponents,
  page             : BobbyExplorerPage,
  bobbyService     : BobbyService,
  serviceDeps      : ServiceDependenciesConnector,
  override val auth: FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  def list(selector: Option[String]): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        rules    <- bobbyService.getRules()
        counts   <- serviceDeps.getBobbyRuleViolations()
        response =  Ok(page(rules, counts))
      } yield response
    }
}
