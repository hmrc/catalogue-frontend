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

import play.api.data.{Form, Forms}
import play.api.data.Forms.{mapping, optional, text}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.vulnerabilities.{VulnerabilitiesForServicesPage, VulnerabilitiesListPage, VulnerabilitiesTimelinePage}

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VulnerabilitiesController @Inject() (
    override val mcc              : MessagesControllerComponents,
    override val auth             : FrontendAuthComponents,
    vulnerabilitiesConnector      : VulnerabilitiesConnector,
    vulnerabilitiesListPage       : VulnerabilitiesListPage,
    vulnerabilitiesForServicesPage: VulnerabilitiesForServicesPage,
    vulnerabilitiesTimelinePage   : VulnerabilitiesTimelinePage,
    teamsAndRepositoriesConnector : TeamsAndRepositoriesConnector
) (implicit
   override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  def distinctVulnerabilitySummaries(
    vulnerability : Option[String],
    curationStatus: Option[String],
    service       : Option[String],
    team          : Option[String],
    component     : Option[String]
  ): Action[AnyContent] =
    Action.async { implicit request =>
      VulnerabilitiesExplorerFilter.form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(vulnerabilitiesListPage(Seq.empty, Seq.empty, formWithErrors))),
          validForm =>
            for {
              teams     <- teamsAndRepositoriesConnector.allTeams.map(_.sortBy(_.name.asString.toLowerCase))
              summaries <- vulnerabilitiesConnector.vulnerabilitySummaries(validForm.vulnerability.filterNot(_.isEmpty), validForm.curationStatus, validForm.service, validForm.team, validForm.component)
            } yield Ok(vulnerabilitiesListPage(summaries, teams, VulnerabilitiesExplorerFilter.form.fill(validForm)))
          )
    }

  def vulnerabilitiesCountForServices: Action[AnyContent] = Action.async { implicit request =>
    import uk.gov.hmrc.cataloguefrontend.vulnerabilities.VulnerabilitiesCountFilter.form
    form
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(vulnerabilitiesForServicesPage(Seq.empty, Seq.empty, formWithErrors))),
        validForm =>
         for {
           teams      <- teamsAndRepositoriesConnector.allTeams.map(_.sortBy(_.name.asString.toLowerCase))
           counts     <- vulnerabilitiesConnector.vulnerabilityCounts(
                            service     = None // Use listjs filtering
                          , team        = validForm.team
                          , environment = validForm.environment
                          )
         } yield Ok(vulnerabilitiesForServicesPage(counts, teams, form.fill(validForm)))
      )
  }

  def getDistinctVulnerabilities(service: String): Action[AnyContent] = Action.async { implicit request =>
    vulnerabilitiesConnector.distinctVulnerabilities(service).map {
      result => Ok(Json.toJson(result))
    }
  }

  def vulnerabilitiesTimeline(
     service       : Option[String],
     team          : Option[String],
     vulnerability : Option[String],
     curationStatus: Option[String],
     from          : LocalDate,
     to            : LocalDate
 ): Action[AnyContent] = Action.async { implicit request =>
    import uk.gov.hmrc.cataloguefrontend.vulnerabilities.VulnerabilitiesTimelineFilter.form

    form
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(BadRequest(vulnerabilitiesTimelinePage(teams = Seq.empty, result = Seq.empty, formWithErrors))),
        validForm      =>
          for {
            sortedTeams  <- teamsAndRepositoriesConnector.allTeams.map(_.sortBy(_.name.asString.toLowerCase))
            teamNames     = sortedTeams.map(_.name.asString)
            counts       <- vulnerabilitiesConnector.timelineCounts(
              service        = validForm.service,
              team           = validForm.team,
              vulnerability  = validForm.vulnerability,
              curationStatus = validForm.curationStatus,
              from           = validForm.from,
              to             = validForm.to
            )
            sortedCounts  = counts.sortBy(_.weekBeginning)
          } yield Ok(vulnerabilitiesTimelinePage(teams = teamNames, result = sortedCounts, form.fill(validForm)))
      )
  }
}


  case class VulnerabilitiesExplorerFilter(
    vulnerability : Option[String] = None,
    curationStatus: Option[String] = None,
    service       : Option[String] = None,
    team          : Option[String] = None,
    component     : Option[String] = None
  )

object VulnerabilitiesExplorerFilter {

  import play.api.data.Forms.{mapping, optional, text}

  lazy val form: Form[VulnerabilitiesExplorerFilter] =
    Form(
      mapping(
        "vulnerability"  -> optional(text),
        "curationStatus" -> optional(text),
        "service"        -> optional(text),
        "team"           -> optional(text),
        "component"      -> optional(text)
      )(VulnerabilitiesExplorerFilter.apply)(VulnerabilitiesExplorerFilter.unapply)
    )
}

case class VulnerabilitiesCountFilter(
 service    : Option[String] = None,
 team       : Option[String] = None,
 environment: Option[Environment] = None
)

object VulnerabilitiesCountFilter {
  lazy val form: Form[VulnerabilitiesCountFilter] = Form(
    mapping(
      "service"     -> optional(text),
      "team"        -> optional(text),
      "environment" -> optional(text)
        .transform[Option[Environment]](
          o => Environment.parse(o.getOrElse(Environment.Production.asString)),
          _.map(_.asString))
    )(VulnerabilitiesCountFilter.apply)(VulnerabilitiesCountFilter.unapply)
  )
}

case class VulnerabilitiesTimelineFilter(
  service       : Option[String],
  team          : Option[String],
  vulnerability : Option[String],
  curationStatus: Option[String],
  from          : LocalDate,
  to            : LocalDate,
)

object VulnerabilitiesTimelineFilter {
  def defaultFromTime(): LocalDate =
    LocalDate.now().minusMonths(6)

  def defaultToTime(): LocalDate =
    LocalDate.now()

  lazy val form: Form[VulnerabilitiesTimelineFilter] = {
    val dateFormat = "yyyy-MM-dd"
    Form(
      mapping(
        "service"       -> optional(text),
        "team"          -> optional(text),
        "vulnerability" -> optional(text),
        "curationStatus"-> optional(text),
        "from"          -> optional(Forms.localDate(dateFormat))
                                .transform[LocalDate](opt => opt.getOrElse(defaultFromTime()), date => Some(date)),
                                //Default to 6 months ago if loading initial page/value not set
        "to"            -> optional(Forms.localDate(dateFormat))
                                .transform[LocalDate](opt => opt.getOrElse(defaultToTime()), date => Some(date))
                                //Default to now if loading initial page/value not set
      )(VulnerabilitiesTimelineFilter.apply)(VulnerabilitiesTimelineFilter.unapply)
        .verifying("To Date must be greater than From Date", form => form.to.isAfter(form.from))
    )
  }
}

