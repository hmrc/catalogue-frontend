/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus

import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.error_404_template
import views.html.servicecommissioningstatus.ServiceCommissioningStatusPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ServiceCommissioningStatusController @Inject() (
  serviceCommissioningStatusConnector: ServiceCommissioningStatusConnector
, serviceCommissioningStatusPage     : ServiceCommissioningStatusPage
, override val mcc                   : MessagesControllerComponents
, override val auth                  : FrontendAuthComponents
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  def getCommissioningState(serviceName: String) =
    BasicAuthAction.async { implicit request =>
      serviceCommissioningStatusConnector
        .commissioningStatus(serviceName)
        .map(_.fold(NotFound(error_404_template()))(result => Ok(serviceCommissioningStatusPage(serviceName, result))))
    }
}
