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

package uk.gov.hmrc.cataloguefrontend.deployments

import cats.implicits._
import play.api.Configuration
import play.api.data.{Form, Forms}
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.model.Environment.Production
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.{Pagination, ReleasesConnector}
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.deployments.DeploymentEventsPage

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeploymentEventsController @Inject()(
  releasesConnector            : ReleasesConnector,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  page                         : DeploymentEventsPage,
  config                       : Configuration,
  override val mcc             : MessagesControllerComponents,
  override val auth            : FrontendAuthComponents
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  import DeploymentEventsController._

  //TODO: link to users page in catalogue rather than UMP - this config has been deleted
  private val userProfileUrl = config.getOptional[String]("microservice.services.user-management.profileBaseUrl")

  def deploymentEvents(env: Environment = Production): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(page(env, Seq.empty, Seq.empty, None, Pagination(0, pageSize, 0), formWithErrors))),
          validForm =>
            for {
              deployments <- releasesConnector.deploymentHistory(
                               env,
                               from    = Some(validForm.from),
                               to      = Some(validForm.to),
                               team    = validForm.team,
                               service = validForm.service,
                               skip    = validForm.page.map(i => i * pageSize),
                               limit   = Some(pageSize)
                             )
              teams       <- teamsAndRepositoriesConnector.allTeams()
              pagination  =  Pagination(page = validForm.page.getOrElse(0), pageSize = pageSize, total = deployments.total)
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

object DeploymentEventsController {

  case class SearchForm(
    from   : LocalDate,
    to     : LocalDate,
    team   : Option[String],
    service: Option[String],
    page   : Option[Int]
  )

  val pageSize: Int = 50

  def defaultFromTime(referenceDate: LocalDate = LocalDate.now()): LocalDate =
    referenceDate.minusDays(7)

  def defaultToTime(referenceDate: LocalDate = LocalDate.now()): LocalDate =
    referenceDate

  lazy val form: Form[SearchForm] = {
    val dateFormat = "yyyy-MM-dd"
    Form(
      Forms
        .mapping(
          "from"    -> Forms
                        .optional(Forms.localDate(dateFormat))
                        .transform[LocalDate](o => o.getOrElse(defaultFromTime()), l => Some(l)), //Default to last week if not set
          "to"      -> Forms
                        .optional(Forms.localDate(dateFormat))
                        .transform[LocalDate](o => o.getOrElse(defaultToTime()), l => Some(l)), //Default to now if not set
          "team"    -> Forms.optional(Forms.text),
          "service" -> Forms.optional(Forms.text),
          "page"    -> Forms.optional(Forms.number(min = 0))
        )(SearchForm.apply)(SearchForm.unapply)
        .verifying("To Date must be greater than or equal to From Date", f => !f.to.isBefore(f.from))
    )
  }
}
