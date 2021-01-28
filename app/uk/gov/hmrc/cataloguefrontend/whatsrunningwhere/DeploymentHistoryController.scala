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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import java.time.LocalDate

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

  import DeploymentHistoryController._

  def history(env: Environment = Production): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc: HeaderCarrier = HeaderCarrier()

    def dateRangeContains(from: Long, to: Long, deployTime: Long): Boolean =
      deployTime >= from && deployTime <= to

    form.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(page(env, Seq.empty, Seq.empty, "", formWithErrors))),
      validForm => {

        // Either specific platform is specified, or all of them
        val platforms = validForm.platform.flatMap(p => Platform.parse(p).toOption).map(Seq.apply(_)).getOrElse(Platform.values)

        for {
          deployments <- Future.sequence(
            // We always search with App=None to pull back the whole set for filtering
            platforms.map(p => releasesConnector.deploymentHistory(p, env, from = Some(validForm.from), to = Some(validForm.to), app = None, team = validForm.team))
          ).map(_.flatten)

          // Explode the deployments so there is one per deployment event
          explodedDeployments = for {
            d <- deployments
            audit <- d.deployers
            if dateRangeContains(validForm.from, validForm.to, audit.deployTime.time.toEpochMilli)
          } yield d.copy(deployers = Seq(audit), firstSeen = audit.deployTime, lastSeen = audit.deployTime)

          teams <- teamsAndRepositoriesConnector.allTeams
        } yield Ok(
          page(
            env,
            explodedDeployments.sortBy(_.firstSeen)(Ordering[TimeSeen].reverse),
            teams.sortBy(_.name.asString),
            "",
            form.fill(validForm)
          )
        )
      }
    )
  }
}

object DeploymentHistoryController {

  import uk.gov.hmrc.cataloguefrontend.DateHelper._

  case class SearchForm(from: Long, to: Long, team: Option[String], search: Option[String], platform: Option[String])

  def defaultFromTime(referenceDate: LocalDate = LocalDate.now()): Long = referenceDate.minusDays(7).atStartOfDayEpochMillis

  def defaultToTime(referenceDate: LocalDate = LocalDate.now()): Long = referenceDate.atEndOfDayEpochMillis

  lazy val form: Form[SearchForm] = {
    val dateFormat = "yyyy-MM-dd"
    Form(
      Forms.mapping(
        "from" -> Forms.optional(
          Forms
            .localDate(dateFormat)
            .transform[Long](_.atStartOfDayEpochMillis, longToLocalDate)
          ).transform[Long](o => o.getOrElse(defaultFromTime()), l => Some(l)), //Default to last week if not set
        "to" -> Forms.optional(
          Forms
            .localDate(dateFormat)
            .transform[Long](_.atEndOfDayEpochMillis, longToLocalDate)
          ).transform[Long](o => o.getOrElse(defaultToTime()), l => Some(l)),  //Default to now if not set
        "team" -> Forms.optional(Forms.text),
        "search" -> Forms.optional(Forms.text),
        "platform" -> Forms.optional(Forms.text)
      )(SearchForm.apply)(SearchForm.unapply)
        verifying("To Date must be greater than or equal to From Date", f => f.to > f.from)
    )
  }
}
