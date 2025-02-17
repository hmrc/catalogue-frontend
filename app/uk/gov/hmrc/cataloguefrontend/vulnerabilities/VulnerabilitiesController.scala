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

import cats.data.EitherT
import play.api.data.{Form, Forms}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, MessagesRequest, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, ServiceName, TeamName}
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

  /**
    * @param vulnerability for reverse routing
    * @param curationStatus for reverse routing
    * @param service for reverse routing
    * @param team for reverse routing
    * @param flag for reverse routing
    */
  def vulnerabilitiesList(
    vulnerability : Option[String]
  , curationStatus: Option[CurationStatus]
  , service       : Option[String]
  , team          : Option[TeamName]
  , flag          : Option[SlugInfoFlag]
  ): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      (for
         teams     <- EitherT.right[Result](teamsAndRepositoriesConnector.allTeams())
         form      =  VulnerabilitiesExplorerFilter.form.bindFromRequest()
         filter    <- EitherT.fromEither[Future]:
                        form.fold(
                          formWithErrors => Left(BadRequest(vulnerabilitiesListPage(summaries = None, teams, formWithErrors)))
                        , formObject     => Right(formObject)
                        )
         summaries <- EitherT.right[Result]:
                        vulnerabilitiesConnector.vulnerabilitySummaries(
                          flag           = Some(filter.flag)
                        , serviceQuery   = filter.service.map(_.asString)
                        , team           = filter.team
                        , curationStatus = filter.curationStatus
                        )
       yield
         Ok(vulnerabilitiesListPage(summaries, teams, form.fill(filter)))
      ).merge

  /**
    * @param team for reverse routing
    * @param flag for reverse routing
    */
  def vulnerabilitiesForServices(
    curationStatus: Option[CurationStatus]
  , team          : Option[TeamName]
  , digitalService: Option[DigitalService]
  , flag          : Option[SlugInfoFlag]
  ): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      (for
         teams           <- EitherT.right[Result](teamsAndRepositoriesConnector.allTeams())
         digitalServices <- EitherT.right[Result](teamsAndRepositoriesConnector.allDigitalServices())
         form            =  VulnerabilitiesCountFilter.form.bindFromRequest()
         filter          <- EitherT.fromEither[Future]:
                              form.fold(
                                formWithErrors => Left(BadRequest(vulnerabilitiesForServicesPage(
                                                    curationStatus.getOrElse(CurationStatus.ActionRequired),
                                                    vulnerabilityCounts = Seq.empty,
                                                    teams,
                                                    digitalServices,
                                                    formWithErrors
                                                  )))
                              , formObject     => Right(formObject)
                              )
         counts          <- EitherT.right[Result]:
                              vulnerabilitiesConnector.vulnerabilityCounts(
                                flag           = filter.flag
                              , serviceName    = None // Use listJS filters
                              , team           = filter.team
                              , digitalService = filter.digitalService
                              )
       yield
         Ok(vulnerabilitiesForServicesPage(filter.curationStatus, counts, teams, digitalServices, form.fill(filter)))
      ).merge


  /**
    * @param service for reverse routing
    * @param team for reverse routing
    * @param vulnerability for reverse routing
    * @param curationStatus for reverse routing
    * @param from for reverse routing
    * @param from for reverse routing
    */
  def vulnerabilitiesTimeline(
    service       : Option[ServiceName]
  , team          : Option[TeamName]
  , vulnerability : Option[String]
  , curationStatus: Option[CurationStatus]
  , from          : LocalDate
  , to            : LocalDate
  ): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      (for
         teams    <- EitherT.right[Result]:
                       teamsAndRepositoriesConnector.allTeams().map(_.map(_.name))
         services <- EitherT.right[Result]:
                       teamsAndRepositoriesConnector.allRepositories(repoType = Some(RepoType.Service)).map(_.map(s => ServiceName(s.name)))
         form     =  VulnerabilitiesTimelineFilter.form.bindFromRequest()
         filter   <- EitherT.fromEither[Future]:
                       form.fold(
                         formWithErrors => Left(BadRequest(vulnerabilitiesTimelinePage(teams = teams, services = services, result = Seq.empty, formWithErrors)))
                       , formObject     => Right(formObject)
                       )
         counts   <- EitherT.right[Result]:
                       vulnerabilitiesConnector.timelineCounts(
                         serviceName    = filter.service,
                         team           = filter.team,
                         vulnerability  = filter.vulnerability,
                         curationStatus = filter.curationStatus,
                         from           = filter.from,
                         to             = filter.to
                       ).map(_.sortBy(_.weekBeginning))
       yield
         Ok(vulnerabilitiesTimelinePage(teams = teams, services = services, result = counts, form.fill(filter)))
      ).merge

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
        "flag"           -> Forms.default(Forms.of[SlugInfoFlag], SlugInfoFlag.Latest),
        "vulnerability"  -> Forms.optional(Forms.text),
        "curationStatus" -> Forms.optional(Forms.of[CurationStatus]),
        "service"        -> Forms.optional(Forms.of[ServiceName]),
        "team"           -> Forms.optional(Forms.of[TeamName]),
      )(VulnerabilitiesExplorerFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
    )

case class VulnerabilitiesCountFilter(
  flag          : SlugInfoFlag           = SlugInfoFlag.Latest,
  service       : Option[ServiceName]    = None,
  team          : Option[TeamName]       = None,
  digitalService: Option[DigitalService] = None,
  curationStatus: CurationStatus
)

object VulnerabilitiesCountFilter:
  lazy val form: Form[VulnerabilitiesCountFilter] =
    Form(
      Forms.mapping(
        "flag"           -> Forms.default(Forms.of[SlugInfoFlag], SlugInfoFlag.Latest)
      , "service"        -> Forms.optional(Forms.of[ServiceName])
      , "team"           -> Forms.optional(Forms.of[TeamName])
      , "digitalService" -> Forms.optional(Forms.of[DigitalService])
      , "curationStatus" -> Forms.default(Forms.of[CurationStatus], CurationStatus.ActionRequired)
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
        "from"           -> Forms.default(Forms.localDate(dateFormat), defaultFromTime()),
        "to"             -> Forms.default(Forms.localDate(dateFormat), defaultToTime()),
        "showDelta"      -> Forms.boolean
      )(VulnerabilitiesTimelineFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
        .verifying("To Date must be greater than From Date", form => form.to.isAfter(form.from))
    )
