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

package uk.gov.hmrc.cataloguefrontend.platforminitiatives

import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.platforminitiatives.html.PlatformInitiativesListPage
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class PlatformInitiativesController @Inject() (
  mcc                           : MessagesControllerComponents,
  platformInitiativesConnector  : PlatformInitiativesConnector,
  platformInitiativesListPage   : PlatformInitiativesListPage
)(implicit val ec: ExecutionContext)
  extends FrontendController(mcc) {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  def platformInitiatives(displayChart: Boolean, displayProgress: Boolean): Action[AnyContent] = {
    Action.async { implicit request =>
      platformInitiativesConnector.allInitiatives.map { initiative =>
        PlatformInitiativesFilter.form
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(platformInitiativesListPage(
              initiatives       = Seq(),
              displayChart      = false,
              displayProgress   = false,
              formWithErrors
            )),
            _ =>
              Ok(platformInitiativesListPage(
                initiatives           = initiative,
                displayChart          = displayChart,
                displayProgress       = displayProgress,
                PlatformInitiativesFilter.form.bindFromRequest()
              ))
          )
      }
    }
  }
}

case class PlatformInitiativesFilter(initiativeName: Option[String] = None) {
  def isEmpty: Boolean = initiativeName.isEmpty
}

object PlatformInitiativesFilter {
  lazy val form: Form[PlatformInitiativesFilter] = Form(
    mapping("initiativeName" -> optional(text).transform[Option[String]](_.filter(_.trim.nonEmpty), identity))
    (PlatformInitiativesFilter.apply)(PlatformInitiativesFilter.unapply)
  )
}
