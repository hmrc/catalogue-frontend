/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.shuttering

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Reads
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterConnector.ShutterEventsFilter
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.shuttering.ShutterEventsPage

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

@Singleton
class ShutterEventsController @Inject()(
  mcc      : MessagesControllerComponents,
  connector: ShutterConnector
)(implicit val ec: ExecutionContext) extends FrontendController(mcc) {

  private val logger = Logger(getClass)

  def shutterEvents: Action[AnyContent] = Action {
    Redirect(routes.ShutterEventsController.shutterEventsList(Environment.Production))
  }

  def shutterEventsList(env: Environment, serviceName: Option[String]): Action[AnyContent] = Action.async { implicit request =>
    implicit val readsShutterEvent: Reads[ShutterEvent] = ShutterEvent.reads
    val filter = filterFor(env, serviceName)
    val form = ShutterEventsForm.fromFilter(filter)

    connector.shutterEventsByTimestampDesc(filter).recover {
      case NonFatal(ex) =>
        logger.error(s"Failed to retrieve shutter events: ${ex.getMessage}", ex)
        Seq.empty
    }.map { events =>
      Ok(ShutterEventsPage(events, form, Environment.values))
    }
  }

  private def filterFor(env: Environment, serviceNameOpt: Option[String]): ShutterEventsFilter =
    ShutterEventsFilter(env, if (serviceNameOpt.exists(_.trim.nonEmpty)) serviceNameOpt else None)
}

private case class ShutterEventsForm(env: String, serviceName: Option[String])

private object ShutterEventsForm {
  lazy val form = Form(
    mapping(
      "environment" -> nonEmptyText,
      "serviceName" -> optional(text)
    )(ShutterEventsForm.apply)(ShutterEventsForm.unapply)
  )

  def fromFilter(filter: ShutterEventsFilter): Form[ShutterEventsForm] =
    form.fill(ShutterEventsForm(filter.environment.asString, filter.serviceName))
}
