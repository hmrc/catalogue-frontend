/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.servicemetrics

import cats.data.EitherT
import play.api.data.{Form, Forms}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, MessagesRequest, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, Environment, ServiceName, TeamName}
import uk.gov.hmrc.cataloguefrontend.servicemetrics.view.html.{ServiceProvisionListPage, ServiceMetricsListPage}
import uk.gov.hmrc.cataloguefrontend.util.TelemetryLinks
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import java.time.{YearMonth, LocalTime, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class ServiceMetricsController @Inject() (
  serviceMetricsConnector       : ServiceMetricsConnector
, teamsAndRepositoriesConnector : TeamsAndRepositoriesConnector
, telemetryLinks                : TelemetryLinks
, serviceMetricsPage            : ServiceMetricsListPage
, serviceProvisionPage          : ServiceProvisionListPage
, override val auth             : FrontendAuthComponents
, override val mcc              : MessagesControllerComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
    with CatalogueAuthBuilders
    with I18nSupport:

  /**
    * @param environment for reverse routing
    * @param team for reverse routing
    * @param digitalService for reverse routing
    * @param metricType for reverse routing
    */
  def serviceMetrics(environment: Environment, team: Option[TeamName], digitalService: Option[DigitalService], metricType: Option[LogMetricId]): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      ( for
         teams           <- EitherT.right[Result](teamsAndRepositoriesConnector.allTeams())
         digitalServices <- EitherT.right[Result](teamsAndRepositoriesConnector.allDigitalServices())
         form            =  ServiceMetricsFilter.form.bindFromRequest()
         filter          <- EitherT.fromEither[Future](form.fold(
                              formErrors => Left(BadRequest(serviceMetricsPage(formErrors, Seq.empty, teams, digitalServices)))
                            , formObject => Right(formObject)
                            ))
         results         <- EitherT.right[Result](serviceMetricsConnector.metrics(Some(filter.environment), filter.team, filter.digitalService, filter.metricType))
        yield
          Ok(serviceMetricsPage(form.fill(filter), results, teams, digitalServices))
      ).merge


  /**
    * @param environment for reverse routing
    * @param team for reverse routing
    * @param digitalService for reverse routing
    */
  def serviceProvision(environment: Environment, team: Option[TeamName], digitalService: Option[DigitalService]): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      ( for
         teams           <- EitherT.right[Result](teamsAndRepositoriesConnector.allTeams())
         digitalServices <- EitherT.right[Result](teamsAndRepositoriesConnector.allDigitalServices())
         form            =  ServiceProvisionFilter.form.bindFromRequest()
         filter          <- EitherT.fromEither[Future](form.fold(
                              formErrors => Left(BadRequest(serviceProvisionPage(formErrors, Seq.empty, teams, digitalServices, telemetryLinks)))
                            , formObject => Right(formObject)
                            ))
         from            =  filter.yearMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant
         to              =  filter.yearMonth.atEndOfMonth.atTime(LocalTime.MAX   ).toInstant(ZoneOffset.UTC)
         results         <- EitherT.right[Result](serviceMetricsConnector.serviceProvision(Some(filter.environment), filter.team, filter.digitalService, from = Some(from), to = Some(to)))
        yield
          Ok(serviceProvisionPage(form.fill(filter), results, teams, digitalServices, telemetryLinks))
      ).merge

case class ServiceMetricsFilter(
  serviceName   : Option[ServiceName]    = None
, team          : Option[TeamName]       = None
, digitalService: Option[DigitalService] = None
, metricType    : Option[LogMetricId]    = None
, environment   : Environment            = Environment.Production
)

object ServiceMetricsFilter:
  lazy val form: Form[ServiceMetricsFilter] =
    Form:
      Forms.mapping(
        "serviceName"    -> Forms.optional(Forms.of[ServiceName   ])
      , "team"           -> Forms.optional(Forms.of[TeamName      ])
      , "digitalService" -> Forms.optional(Forms.of[DigitalService])
      , "metricType"     -> Forms.optional(Forms.of[LogMetricId   ])
      , "environment"    -> Forms.default (Forms.of[Environment   ], Environment.Production)
      )(ServiceMetricsFilter.apply)(f => Some(Tuple.fromProductTyped(f)))

case class ServiceProvisionFilter(
  serviceName   : Option[ServiceName]    = None
, team          : Option[TeamName]       = None
, digitalService: Option[DigitalService] = None
, environment   : Environment
, yearMonth     : YearMonth
)

object ServiceProvisionFilter:
  import play.api.data.format.Formats._
  lazy val form: Form[ServiceProvisionFilter] =
    import play.api.data.format.Formatter
    import uk.gov.hmrc.cataloguefrontend.binders.Binders
    import cats.implicits.catsSyntaxEither

    given Formatter[YearMonth] =
      Binders.formFormatFromString(s => Try(YearMonth.parse(s)).toEither.leftMap(_ => "Invalid YearMonth format"), _.toString)

    Form:
      Forms.mapping(
        "serviceName"    -> Forms.optional(Forms.of[ServiceName   ])
      , "team"           -> Forms.optional(Forms.of[TeamName      ])
      , "digitalService" -> Forms.optional(Forms.of[DigitalService])
      , "environment"    -> Forms.default (Forms.of[Environment   ], Environment.Production)
      , "yearMonth"      -> Forms.default (Forms.of[YearMonth     ], YearMonth.now().minusMonths(1))
      )(ServiceProvisionFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
