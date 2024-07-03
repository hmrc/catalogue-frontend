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

package uk.gov.hmrc.cataloguefrontend.healthindicators

import play.api.data.{Form, Forms}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.healthindicators.HealthIndicatorsFilter.form
import uk.gov.hmrc.cataloguefrontend.healthindicators.view.html.{HealthIndicatorsLeaderBoard, HealthIndicatorsPage}
import uk.gov.hmrc.cataloguefrontend.model.TeamName
import uk.gov.hmrc.cataloguefrontend.view.html.error_404_template
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class HealthIndicatorsController @Inject() (
  healthIndicatorsConnector    : HealthIndicatorsConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  override val mcc             : MessagesControllerComponents,
  healthIndicatorsService      : HealthIndicatorsService,
  override val auth            : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  def breakdownForRepo(name: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for
        indicator <- healthIndicatorsConnector.getIndicator(name)
        history   <- healthIndicatorsConnector.getHistoricIndicators(name)
        average   <- healthIndicatorsConnector.getAveragePlatformScore()
      yield indicator match
        case Some(indicator) => Ok(HealthIndicatorsPage(indicator, history, average.map(_.averageScore)))
        case None            => NotFound(error_404_template())
    }

  def indicatorsForRepoType(): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(HealthIndicatorsLeaderBoard(Seq.empty, Seq.empty, Seq.empty, formWithErrors))),
          validForm =>
            for
              indicatorsWithTeams <- healthIndicatorsService.findIndicatorsWithTeams(
                                       repoType       = validForm.repoType
                                     , repoNameFilter = None // Use listjs filtering
                                     )
              teams               <- teamsAndRepositoriesConnector.allTeams()
              indicators          =  indicatorsWithTeams.filter(t => validForm.team.fold(true)(t.owningTeams.contains))
            yield Ok(HealthIndicatorsLeaderBoard(indicators, RepoType.values.toSeq, teams.sortBy(_.name), form.fill(validForm)))
        )
    }
end HealthIndicatorsController

object HealthIndicatorsController:
  def getScoreColour(score: Int): String =
    score match
      case x if x > 0    => "repo-score-green"
      case x if x > -100 => "repo-score-amber"
      case _             => "repo-score-red"

case class HealthIndicatorsFilter(
  repoName: Option[String],
  team    : Option[TeamName] = None,
  repoType: Option[RepoType] = None
)

object HealthIndicatorsFilter:
  lazy val form: Form[HealthIndicatorsFilter] =
    Form(
      Forms.mapping(
        "repoName" -> Forms.optional(Forms.text),
        "team"     -> Forms.optional(Forms.of[TeamName]),
        "repoType" -> Forms.optional(Forms.of[RepoType])
      )(HealthIndicatorsFilter.apply)(r => Some(Tuple.fromProductTyped(r)))
    )
