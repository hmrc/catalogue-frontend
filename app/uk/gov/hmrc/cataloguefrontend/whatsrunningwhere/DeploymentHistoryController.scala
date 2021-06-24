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

import play.api.Configuration

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
class DeploymentHistoryController @Inject() (
  releasesConnector: ReleasesConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  page: DeploymentHistoryPage,
  config: Configuration,
  mcc: MessagesControllerComponents
)(implicit val ec: ExecutionContext)
    extends FrontendController(mcc) {

  import DeploymentHistoryController._

  private val userProfileUrl = config.getOptional[String]("microservice.services.user-management.profileBaseUrl")

  def history(env: Environment = Production): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrier()

      form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(page(env, Seq.empty, Seq.empty, None, Pagination(0, pageSize, 0), formWithErrors))),
          validForm =>
            for {
              deployments <- releasesConnector.deploymentHistory(
                               env,
                               from = Some(validForm.from),
                               to = Some(validForm.to),
                               team = validForm.team,
                               service = validForm.service,
                               skip = validForm.page.map(i => i * pageSize),
                               limit = Some(pageSize)
                             )
              teams <- teamsAndRepositoriesConnector.allTeams
              pagination = Pagination(page = validForm.page.getOrElse(0), pageSize = pageSize, total = deployments.total)
            } yield Ok(
              page(
                env,
                deployments.history,
                teams.sortBy(_.name.asString),
                userProfileUrl,
                pagination,
                form.fill(validForm)
              )
            )
        )
    }
}

object DeploymentHistoryController {

  case class SearchForm(
    from: LocalDate,
    to: LocalDate,
    team: Option[String],
    service: Option[String],
    page: Option[Int]
  )

  val pageSize: Int = 50

  def defaultFromTime(referenceDate: LocalDate = LocalDate.now()): LocalDate = referenceDate.minusDays(7)

  def defaultToTime(referenceDate: LocalDate = LocalDate.now()): LocalDate = referenceDate

  lazy val form: Form[SearchForm] = {
    val dateFormat = "yyyy-MM-dd"
    Form(
      Forms.mapping(
        "from" -> Forms
          .optional(Forms.localDate(dateFormat))
          .transform[LocalDate](o => o.getOrElse(defaultFromTime()), l => Some(l)), //Default to last week if not set
        "to" -> Forms
          .optional(Forms.localDate(dateFormat))
          .transform[LocalDate](o => o.getOrElse(defaultToTime()), l => Some(l)), //Default to now if not set
        "team"    -> Forms.optional(Forms.text),
        "service" -> Forms.optional(Forms.text),
        "page"    -> Forms.optional(Forms.number(min = 0))
      )(SearchForm.apply)(SearchForm.unapply)
        verifying ("To Date must be greater than or equal to From Date", f => f.to.isAfter(f.from))
    )
  }
}
