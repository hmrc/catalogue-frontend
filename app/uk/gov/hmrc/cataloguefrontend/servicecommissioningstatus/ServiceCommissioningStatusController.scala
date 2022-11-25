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

import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.servicecommissioningstatus.ServiceCommissioningStatusPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

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

//  def getCommissioningStatus(serviceName: String): Action[AnyContent] = Action.async { implicit request =>
//    serviceCommissioningStatusConnector.commissioningStatus(serviceName).map {
//      result => Ok(page())
//    }
//  }

  def getCommissioningStatus =
    BasicAuthAction.async { implicit request =>
      serviceStatusFilter.form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(Ok(serviceCommissioningStatusPage(None, formWithErrors))),
          query =>
            serviceCommissioningStatusConnector
              .commissioningStatus(query.name)
              .map { results =>
                Ok(serviceCommissioningStatusPage(results, serviceStatusFilter.form.bindFromRequest()))
              }
        )
    }


  case class serviceStatusFilter(name: Option[String] = None) {
    def isEmpty: Boolean = name.isEmpty
  }

  object serviceStatusFilter {
    lazy val form = Form(
      mapping(
        "service" -> optional(text).transform[Option[String]](x => if (x.exists(_.trim.isEmpty)) None else x, identity)
      )(serviceStatusFilter.apply)(serviceStatusFilter.unapply)
    )
  }


}
