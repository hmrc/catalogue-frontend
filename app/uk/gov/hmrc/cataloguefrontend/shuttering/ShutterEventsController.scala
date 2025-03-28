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

package uk.gov.hmrc.cataloguefrontend.shuttering

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.data.{Form, Forms}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, RequestHeader}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.RouteConfigurationConnector
import uk.gov.hmrc.cataloguefrontend.connector.RouteConfigurationConnector.RouteType
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.shuttering.ShutterConnector.ShutterEventsFilter
import uk.gov.hmrc.cataloguefrontend.shuttering.view.html.ShutterEventsPage
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

@Singleton
class ShutterEventsController @Inject() (
  override val mcc   : MessagesControllerComponents,
  connector          : ShutterConnector,
  routeRulesConnector: RouteConfigurationConnector,
  override val auth  : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders
     with play.api.i18n.I18nSupport:

  private val logger = Logger(getClass)

  val shutterEvents: Action[AnyContent] =
    Action:
      Redirect(routes.ShutterEventsController.shutterEventsList(Environment.Production))

  def shutterEventsList(
    env        : Environment,
    serviceName: Option[ServiceName],
    limit      : Option[Int],
    offset     : Option[Int]
  ): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      val filter = filterFor(env, serviceName)
      val form   = ShutterEventsForm.fromFilter(filter)

      for
        frontendRoutes <- routeRulesConnector.routes(service = None, routeType = Some(RouteType.Frontend), environment = None)
        services       =  frontendRoutes.map(_.serviceName.asString).distinct
        events         <- connector
                            .shutterEventsByTimestampDesc(filterFor(env, serviceName), limit, offset)
                            .recover:
                              case NonFatal(ex) =>
                                logger.error(s"Failed to retrieve shutter events: ${ex.getMessage}", ex)
                                Seq.empty
        page           =  ShutterEventsPage(services, events, form, Environment.values.toSeq)
      yield Ok(page)

  private def filterFor(env: Environment, serviceNameOpt: Option[ServiceName]): ShutterEventsFilter =
    ShutterEventsFilter(env, serviceNameOpt.filter(_.asString.trim.nonEmpty))

end ShutterEventsController

private case class ShutterEventsForm(
  env        : Environment,
  serviceName: Option[ServiceName]
)

private object ShutterEventsForm:
  lazy val form =
    Form:
      Forms.mapping(
        "environment" -> Forms.of[Environment]
      , "serviceName" -> Forms.optional(Forms.of[ServiceName])
      )(ShutterEventsForm.apply)(f => Some(Tuple.fromProductTyped(f)))

  def fromFilter(filter: ShutterEventsFilter): Form[ShutterEventsForm] =
    form.fill(ShutterEventsForm(filter.environment, filter.serviceName))
