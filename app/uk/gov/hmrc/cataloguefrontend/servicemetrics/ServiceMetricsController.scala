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
import play.api.libs.json.Reads
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, MessagesRequest, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.servicemetrics.view.html.ServiceMetricsListPage
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, Environment, TeamName}
import uk.gov.hmrc.cataloguefrontend.util.{FormFormat, FromString, FromStringEnum, Parser}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceMetricsController @Inject() (
  serviceMetricsPage            : ServiceMetricsListPage
, serviceMetricsConnector       : ServiceMetricsConnector
, teamsAndRepositoriesConnector : TeamsAndRepositoriesConnector
, override val auth             : FrontendAuthComponents
, override val mcc              : MessagesControllerComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
    with CatalogueAuthBuilders
    with I18nSupport:

  def serviceMetrics(): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      ( for
         teams           <- EitherT.right[Result](teamsAndRepositoriesConnector.allTeams())
         digitalServices <- EitherT.right[Result](teamsAndRepositoriesConnector.allDigitalServices())
         form            =  ServiceMetricsFilter.form.bindFromRequest()
         filter          <- EitherT.fromEither[Future](form.fold(
                              formWithErrors => Left(BadRequest(serviceMetricsPage(form, Seq.empty, Seq.empty, Seq.empty)))
                            , formObject     => Right(formObject)
                            ))
         results <- EitherT.right[Result](serviceMetricsConnector.metrics(filter.team, filter.digitalService, filter.metricType, Some(filter.environment)))
        yield
          Ok(serviceMetricsPage(form.fill(filter), results, teams, digitalServices))
      ).merge

import FromStringEnum._

given Parser[LogMetricId] = Parser.parser(LogMetricId.values)

enum LogMetricId(
  override val asString: String,
  val displayString    : String
) extends FromString
  derives Reads, FormFormat:
  case ContainerKill    extends LogMetricId(asString = "container-kill"    , displayString = "Container Kill"    )
  case NonIndexedQuery  extends LogMetricId(asString = "non-indexed-query" , displayString = "Non-indexed Query" )
  case SlowRunningQuery extends LogMetricId(asString = "slow-running-query", displayString = "Slow Running Query")

case class ServiceMetricsFilter(
  team          : Option[TeamName]       = None
, digitalService: Option[DigitalService] = None
, metricType    : Option[LogMetricId]    = None
, environment   : Environment            = Environment.Production
)

object ServiceMetricsFilter:
  lazy val form: Form[ServiceMetricsFilter] =
    Form(
      Forms.mapping(
        "team"           -> Forms.optional(Forms.of[TeamName      ])
      , "digitalService" -> Forms.optional(Forms.of[DigitalService])
      , "metricType"     -> Forms.optional(Forms.of[LogMetricId   ])
      , "environment"    -> Forms.optional(Forms.of[Environment   ]).transform(_.getOrElse(Environment.Production), Some.apply)
      )(ServiceMetricsFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
    )
