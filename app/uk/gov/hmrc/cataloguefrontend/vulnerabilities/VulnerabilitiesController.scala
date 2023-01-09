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

package uk.gov.hmrc.cataloguefrontend.vulnerabilities

import play.api.data.Form
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.vulnerabilities.VulnerabilitiesListPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VulnerabilitiesController @Inject() (
    override val mcc             : MessagesControllerComponents,
    override val auth            : FrontendAuthComponents,
    vulnerabilitiesConnector     : VulnerabilitiesConnector,
    vulnerabilitiesListPage      : VulnerabilitiesListPage,
    teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
) (implicit
   override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  def distinctVulnerabilitySummaries(
    vulnerability : Option[String],
    curationStatus: Option[String],
    service       : Option[String],
    team          : Option[String]
  ): Action[AnyContent] =
    Action.async { implicit request =>
      VulnerabilitiesExplorerFilter.form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(vulnerabilitiesListPage(Seq.empty, Seq.empty, formWithErrors))),
          validForm =>
            for {
              teams     <- teamsAndRepositoriesConnector.allTeams.map(_.sortBy(_.name.asString.toLowerCase))
              summaries <- vulnerabilitiesConnector.vulnerabilitySummaries(validForm.vulnerability.filterNot(_.isEmpty), validForm.curationStatus, validForm.service, validForm.team)
            } yield Ok(vulnerabilitiesListPage(summaries, teams, VulnerabilitiesExplorerFilter.form.fill(validForm)))
          )
    }

  def getDistinctVulnerabilities(service: String): Action[AnyContent] = Action.async { implicit request =>
    vulnerabilitiesConnector.distinctVulnerabilities(service).map {
      result => Ok(Json.toJson(result))
    }
  }
}

case class VulnerabilitiesExplorerFilter(
  vulnerability : Option[String] = None,
  curationStatus: Option[String] = None,
  service       : Option[String] = None,
  team          : Option[String] = None
)

object VulnerabilitiesExplorerFilter {
  import play.api.data.Forms.{mapping, optional, text}
  lazy val form: Form[VulnerabilitiesExplorerFilter] =
    Form(
      mapping(
        "vulnerability"  -> optional(text),
        "curationStatus" -> optional(text),
        "service"        -> optional(text),
        "team"           -> optional(text)
      )(VulnerabilitiesExplorerFilter.apply)(VulnerabilitiesExplorerFilter.unapply)
    )
}
