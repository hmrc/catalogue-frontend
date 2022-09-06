/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.data.Forms.{boolean, mapping, optional, text}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.CatalogueFrontendSwitches
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.helper.form
import views.html.vulnerabilities.VulnerabilitiesListPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VulnerabilitiesController @Inject() (
    override val mcc             : MessagesControllerComponents,
    override val auth            : FrontendAuthComponents,
    vulnerabilitiesConnector: VulnerabilitiesConnector,
    vulnerabilitiesListPage: VulnerabilitiesListPage
) (implicit
   override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  def distinctVulnerabilitySummaries(vulnerability: Option[String], requiresAction: Option[Boolean]): Action[AnyContent] = Action.async { implicit request =>
    import uk.gov.hmrc.cataloguefrontend.vulnerabilities.VulnerabilitiesExplorerFilter.form
    implicit val vcsr: OFormat[VulnerabilityCountSummary] = VulnerabilityCountSummary.reads
      form
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(vulnerabilitiesListPage(Seq.empty, formWithErrors))),
        validForm =>
          for {
            summaries <- vulnerabilitiesConnector.vulnerabilitySummaries(validForm.vulnerability.filterNot(_.isEmpty), validForm.requiresActionOnly )
          } yield Ok(vulnerabilitiesListPage(summaries, form.fill(validForm)))
        )
  }
}

case class VulnerabilitiesExplorerFilter(
  vulnerability: Option[String] = None,
  requiresActionOnly: Option[Boolean] = None
)

object VulnerabilitiesExplorerFilter {
  lazy val form: Form[VulnerabilitiesExplorerFilter] = Form(
    mapping(
      "vulnerability" -> optional(text),
      "requiresActionOnly" -> optional(boolean)
    )(VulnerabilitiesExplorerFilter.apply)(VulnerabilitiesExplorerFilter.unapply)
  )
}