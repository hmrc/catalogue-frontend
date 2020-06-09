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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import java.time.{Instant, LocalDate, ZoneId}

import javax.inject.{Inject, Singleton}
import play.api.data.{Form, Forms}
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.model.Environment.Production
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.Platform.{ECS, Heritage}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.DeploymentHistoryPage

import scala.concurrent.ExecutionContext

@Singleton
class DeploymentHistoryController @Inject()(
  releasesConnector: ReleasesConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  page: DeploymentHistoryPage,
  mcc: MessagesControllerComponents
)(implicit val ec: ExecutionContext
) extends FrontendController(mcc) {

  def history(env: Environment = Production): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val search = form.bindFromRequest().fold(_ => SearchForm(None, None, None, None), res => res)

    for {
      historyHeritage <- releasesConnector.deploymentHistory(Heritage, env, from = search.from, to = search.to, app = search.app, team = search.team)
      historyEcs <- releasesConnector.deploymentHistory(ECS, env, from = search.from, to = search.to, app = search.app, team = search.team)
      teams   <- teamsAndRepositoriesConnector.allTeams
    } yield Ok(page(env, (historyHeritage ++ historyEcs).sortBy(_.firstSeen)(Ordering[TimeSeen].reverse), teams.sortBy(_.name.asString), ""))
  }

  case class SearchForm(from: Option[Long], to: Option[Long], team: Option[String], app: Option[String])

  def form(): Form[SearchForm] = {
    val dateFormat = "yyyy-MM-dd"
    Form(
      Forms.mapping(
        "from" -> Forms.optional(
          Forms
            .localDate(dateFormat)
            .transform[Long](_.atStartOfDay(ZoneId.of("UTC")).toInstant.toEpochMilli, l => LocalDate.from(Instant.ofEpochMilli(l)))),
        "to" -> Forms.optional(
          Forms
            .localDate(dateFormat)
            .transform[Long](_.atStartOfDay(ZoneId.of("UTC")).toInstant.toEpochMilli, l => LocalDate.from(Instant.ofEpochMilli(l)))),
        "team" -> Forms.optional(Forms.text),
        "app"  -> Forms.optional(Forms.text)
      )(SearchForm.apply)(SearchForm.unapply)
    )
  }
}
