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

package uk.gov.hmrc.cataloguefrontend.healthmetrics

import cats.data.EitherT
import play.api.data.{Form, Forms}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, MessagesRequest, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.healthmetrics.HealthMetric.OpenPRForReposOwnedByTeam
import uk.gov.hmrc.cataloguefrontend.healthmetrics.view.html.HealthMetricsTimelinePage
import uk.gov.hmrc.cataloguefrontend.model.TeamName
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HealthMetricsController @Inject() (
  override val mcc             : MessagesControllerComponents
, override val auth            : FrontendAuthComponents
, teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
, healthMetricsConnector       : HealthMetricsConnector
, healthMetricsTimelinePage    : HealthMetricsTimelinePage
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  def healthMetricsTimeline(
    teamName    : TeamName
  , healthMetric: HealthMetric = HealthMetric.OpenPRForReposOwnedByTeam
  , from        : LocalDate
  , to          : LocalDate
  ): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      (for
         teams     <- EitherT.right[Result](teamsAndRepositoriesConnector.allTeams()).map(_.map(_.name))
         form      =  HealthMetricsFilter.form.bindFromRequest()
         filter    <- EitherT.fromEither[Future]:
                        form.fold(
                          formWithErrors => Left(BadRequest(healthMetricsTimelinePage(teams = teams, result = Seq.empty, formWithErrors)))
                        , formObject     => Right(formObject)
                        )
         counts    <- EitherT.right[Result]:
                        healthMetricsConnector.healthMetricsTimelineCounts(
                          team         = filter.team
                        , healthMetric = filter.healthMetric
                        , from         = filter.from
                        , to           = filter.to
                        )
       yield
         Ok(healthMetricsTimelinePage(teams = teams, result = counts, form.fill(filter)))
      ).merge

case class HealthMetricsFilter(
  team        : TeamName
, healthMetric: HealthMetric
, from        : LocalDate
, to          : LocalDate
)

object HealthMetricsFilter:
  def defaultFromTime(): LocalDate =
    LocalDate.now().minusMonths(6)

  def defaultToTime(): LocalDate =
    LocalDate.now()

  lazy val form: Form[HealthMetricsFilter] =
    val dateFormat = "yyyy-MM-dd"
    Form(
      Forms.mapping(
        "team"         -> Forms.of[TeamName]
      , "healthMetric" -> Forms.default(Forms.of[HealthMetric], HealthMetric.OpenPRForReposOwnedByTeam)
      , "from"         -> Forms.default(Forms.localDate(dateFormat), defaultFromTime())
      , "to"           -> Forms.default(Forms.localDate(dateFormat), defaultToTime())
      )(HealthMetricsFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
        .verifying("To Date must be greater than From Date", form => form.to.isAfter(form.from))
    )
