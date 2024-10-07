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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.{ServiceName, TeamName}
import uk.gov.hmrc.cataloguefrontend.vulnerabilities.view.html.{VulnerabilitiesForServicesPage, VulnerabilitiesListPage, VulnerabilitiesTimelinePage}
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.cataloguefrontend.model.SlugInfoFlag

@Singleton
class VulnerabilitiesController @Inject() (
  override val mcc              : MessagesControllerComponents,
  override val auth             : FrontendAuthComponents,
  vulnerabilitiesConnector      : VulnerabilitiesConnector,
  vulnerabilitiesListPage       : VulnerabilitiesListPage,
  vulnerabilitiesForServicesPage: VulnerabilitiesForServicesPage,
  vulnerabilitiesTimelinePage   : VulnerabilitiesTimelinePage,
  teamsAndRepositoriesConnector : TeamsAndRepositoriesConnector
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  def vulnerabilitiesList(
    vulnerability : Option[String],
    curationStatus: Option[String],
    service       : Option[String],
    team          : Option[TeamName],
    flag          : Option[String]
  ): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      VulnerabilitiesExplorerFilter.form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(vulnerabilitiesListPage(None, Seq.empty, formWithErrors))),
          validForm =>
            for
              teams     <- teamsAndRepositoriesConnector.allTeams().map(_.sortBy(_.name.asString.toLowerCase))
              summaries <- vulnerabilitiesConnector.vulnerabilitySummaries(
                             flag           = Some(validForm.flag)
                           , serviceQuery   = validForm.service.map(_.asString)
                           , team           = validForm.team
                           , curationStatus = validForm.curationStatus
                           )
            yield Ok(vulnerabilitiesListPage(summaries, teams, VulnerabilitiesExplorerFilter.form.fill(validForm)))
          )
    }

  def vulnerabilitiesForServices(
    teamName: Option[TeamName] = None //TeamName is read from form, this param only exists for reverse routes
  ): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      import uk.gov.hmrc.cataloguefrontend.vulnerabilities.VulnerabilitiesCountFilter.form
      form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(vulnerabilitiesForServicesPage(Seq.empty, Seq.empty, formWithErrors))),
          validForm =>
            for
              teams  <- teamsAndRepositoriesConnector.allTeams().map(_.sortBy(_.name.asString.toLowerCase))
              counts <- vulnerabilitiesConnector.vulnerabilityCounts(
                          flag        = validForm.flag
                        , serviceName = None // Use listJS filters
                        , team        = validForm.team
                        )
            yield Ok(vulnerabilitiesForServicesPage(counts, teams, form.fill(validForm)))
        )
    }

  def vulnerabilitiesTimeline(
    service       : Option[ServiceName],
    team          : Option[TeamName],
    vulnerability : Option[String],
    curationStatus: Option[String],
    from          : LocalDate,
    to            : LocalDate
  ): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      import VulnerabilitiesTimelineFilter.form
      for
        teams    <- teamsAndRepositoriesConnector.allTeams().map(_.map(_.name))
        services <- teamsAndRepositoriesConnector.allRepositories(repoType = Some(RepoType.Service)).map(_.map(s => ServiceName(s.name)))
        res      <- form.bindFromRequest()
                      .fold(
                        formWithErrors =>
                          Future.successful(BadRequest(vulnerabilitiesTimelinePage(teams = teams, services = services, result = Seq.empty, formWithErrors))),
                        validForm      =>
                          for
                            counts <- vulnerabilitiesConnector.timelineCounts(
                                        serviceName    = validForm.service,
                                        team           = validForm.team,
                                        vulnerability  = validForm.vulnerability,
                                        curationStatus = validForm.curationStatus,
                                        from           = validForm.from,
                                        to             = validForm.to
                                      ).map(_.sortBy(_.weekBeginning))
                          yield Ok(vulnerabilitiesTimelinePage(teams = teams, services = services, result = counts, form.fill(validForm)))
                      )
      yield res
    }

end VulnerabilitiesController


case class VulnerabilitiesExplorerFilter(
  flag          : SlugInfoFlag           = SlugInfoFlag.Latest,
  vulnerability : Option[String]         = None,
  curationStatus: Option[CurationStatus] = None,
  service       : Option[ServiceName]    = None,
  team          : Option[TeamName]       = None,
)

object VulnerabilitiesExplorerFilter:
  lazy val form: Form[VulnerabilitiesExplorerFilter] =
    Form(
      Forms.mapping(
        "flag"           -> Forms.optional(Forms.of[SlugInfoFlag]).transform(_.getOrElse(SlugInfoFlag.Latest), Some.apply),
        "vulnerability"  -> Forms.optional(Forms.text),
        "curationStatus" -> Forms.optional(Forms.of[CurationStatus]),
        "service"        -> Forms.optional(Forms.of[ServiceName]),
        "team"           -> Forms.optional(Forms.of[TeamName]),
      )(VulnerabilitiesExplorerFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

case class VulnerabilitiesCountFilter(
  flag   : SlugInfoFlag        = SlugInfoFlag.Latest,
  service: Option[ServiceName] = None,
  team   : Option[TeamName]    = None,
)

object VulnerabilitiesCountFilter:
  lazy val form: Form[VulnerabilitiesCountFilter] =
    Form(
      Forms.mapping(
        "flag"    -> Forms.optional(Forms.of[SlugInfoFlag]).transform(_.getOrElse(SlugInfoFlag.Latest), Some.apply),
        "service" -> Forms.optional(Forms.of[ServiceName]),
        "team"    -> Forms.optional(Forms.of[TeamName]),
      )(VulnerabilitiesCountFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

case class VulnerabilitiesTimelineFilter(
  service       : Option[ServiceName],
  team          : Option[TeamName],
  vulnerability : Option[String],
  curationStatus: Option[CurationStatus],
  from          : LocalDate,
  to            : LocalDate,
  showDelta     : Boolean
)

object VulnerabilitiesTimelineFilter:
  def defaultFromTime(): LocalDate =
    LocalDate.now().minusMonths(6)

  def defaultToTime(): LocalDate =
    LocalDate.now()

  lazy val form: Form[VulnerabilitiesTimelineFilter] =
    val dateFormat = "yyyy-MM-dd"
    Form(
      Forms.mapping(
        "service"        -> Forms.optional(Forms.of[ServiceName]),
        "team"           -> Forms.optional(Forms.of[TeamName]),
        "vulnerability"  -> Forms.optional(Forms.text),
        "curationStatus" -> Forms.optional(Forms.of[CurationStatus]),
        "from"           -> Forms.optional(Forms.localDate(dateFormat)).transform[LocalDate](_.getOrElse(defaultFromTime()), Some.apply), // Default to 6 months ago if loading initial page/value not set
        "to"             -> Forms.optional(Forms.localDate(dateFormat)).transform[LocalDate](_.getOrElse(defaultToTime()  ), Some.apply), // Default to now if loading initial page/value not set
        "showDelta"      -> Forms.boolean
      )(VulnerabilitiesTimelineFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
        .verifying("To Date must be greater than From Date", form => form.to.isAfter(form.from))
    )
