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

package uk.gov.hmrc.cataloguefrontend.healthindicators

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.{HealthIndicatorsLeaderBoard, HealthIndicatorsPage, error_404_template}

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class HealthIndicatorsController @Inject()(
  healthIndicatorsConnector: HealthIndicatorsConnector,
  mcc: MessagesControllerComponents
)(implicit val ec: ExecutionContext)
    extends FrontendController(mcc) {

  def indicatorsForRepo(name: String): Action[AnyContent] =
    Action.async { implicit request =>
      healthIndicatorsConnector.getHealthIndicators(name).map {
        case Some(repositoryRating: RepositoryRating) => Ok(HealthIndicatorsPage(repositoryRating))
        case None                                     => NotFound(error_404_template())
      }
    }

  def indicatorsForAllRepos(): Action[AnyContent] =
    Action.async { implicit request =>
      healthIndicatorsConnector.getAllHealthIndicators().map {
        case repoRatings: Seq[RepositoryRating] => Ok(HealthIndicatorsLeaderBoard(repoRatings))
        case _ => NotFound(error_404_template())
      }
    }
}

object HealthIndicatorsController {
  def getScoreColour(score: Int): String ={
    score match {
      case x if x > 0 => "repo-score-green"
      case x if x > -100 => "repo-score-amber"
      case _ => "repo-score-red"
    }
  }
//  def repositoryLinkBuilder(repoName: String): String ={
//
//  }
}
