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

import java.time.{Instant, LocalDate, LocalTime, ZoneId}

import javax.inject.{Inject, Singleton}
import play.api.data.{Form, Forms}
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.model.Environment.Production
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.DeploymentHistoryPage

import scala.concurrent.{ExecutionContext, Future}

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

    val today = LocalDate.now()
    val weekAgo = today.minusDays(7)

    val search = form.bindFromRequest().fold(_ => SearchForm(None, None, None, None, None), res =>
      res.copy(
        //If no query params are set, default to showing the last weeks data
        from = res.from.orElse(Some(toEpochMillis(weekAgo, startOfDay = true))),
        to = res.to.orElse(Some(toEpochMillis(today, startOfDay = false)))
      )
    )

    // Either specific platform is specified, or all of them
    val platforms = search.platform.flatMap(p => Platform.parse(p).toOption).map(Seq.apply(_)).getOrElse(Platform.values)

    for {
      deployments <- Future.sequence(
        platforms.map(p => releasesConnector.deploymentHistory(p, env, from = search.from, to = search.to, app = search.app, team = search.team))
      ).map(_.flatten)
      teams   <- teamsAndRepositoriesConnector.allTeams
    } yield Ok(
      page(
        env,
        deployments.sortBy(_.firstSeen)(Ordering[TimeSeen].reverse),
        teams.sortBy(_.name.asString),
        "",
        form.fill(search)
      )
    )
  }

  case class SearchForm(from: Option[Long], to: Option[Long], team: Option[String], app: Option[String], platform: Option[String])

  val utc = ZoneId.of("UTC")
  def toLocalDate(l: Long): LocalDate = Instant.ofEpochMilli(l).atZone(ZoneId.of("UTC")).toLocalDate
  def toEpochMillis(l: LocalDate, startOfDay: Boolean): Long = {
    (if(startOfDay) l.atStartOfDay(utc) else l.atTime(LocalTime.MAX).atZone(utc)).toInstant.toEpochMilli
  }

  lazy val form: Form[SearchForm] = {
    val dateFormat = "yyyy-MM-dd"
    Form(
      Forms.mapping(
        "from" -> Forms.optional(
          Forms
            .localDate(dateFormat)
            .transform[Long](toEpochMillis(_, startOfDay =  true), toLocalDate)),
        "to" -> Forms.optional(
          Forms
            .localDate(dateFormat)
            .transform[Long](toEpochMillis(_, startOfDay = false), toLocalDate)),
        "team" -> Forms.optional(Forms.text),
        "service" -> Forms.optional(Forms.text),
        "platform" -> Forms.optional(Forms.text)
      )(SearchForm.apply)(SearchForm.unapply)
    )
  }
}
