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

package uk.gov.hmrc.cataloguefrontend.bobby

import cats.data.EitherT
import play.api.data.{Form, Forms}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, MessagesRequest, Result, RequestHeader}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.bobby.view.html.{BobbyExplorerPage, BobbyViolationsPage}
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, ServiceDependenciesConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, SlugInfoFlag, TeamName}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class BobbyExplorerController @Inject() (
  teamsAndReposConnector: TeamsAndRepositoriesConnector
, serviceDeps           : ServiceDependenciesConnector
, bobbyService          : BobbyService
, bobbyViolationsPage   : BobbyViolationsPage
, bobbyExplorerPage     : BobbyExplorerPage
, override val mcc      : MessagesControllerComponents
, override val auth     : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  /**
    * @param teamName for reverse routing
    * @param digitalService for reverse routing
    * @param flag for reverse routing
    * @param isActive for reverse routing
    */
  def bobbyViolations(
    teamName      : Option[TeamName],
    digitalService: Option[DigitalService],
    flag          : Option[SlugInfoFlag],
    isActive      : Option[Boolean]
  ): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      (for
         teams           <- EitherT.right[Result](teamsAndReposConnector.allTeams())
         digitalServices <- EitherT.right[Result](teamsAndReposConnector.allDigitalServices())
         form            =  BobbyReportFilter.form.bindFromRequest()
         filter          <- EitherT.fromEither[Future](form.fold(
                             formErrors => Left(BadRequest(bobbyViolationsPage(form, teams, digitalServices, results = None)))
                           , formObject => Right(formObject)
                            ))
         results         <- EitherT.right[Result]:
                             serviceDeps.bobbyReports(filter.teamName, filter.digitalService, filter.repoType, filter.flag)
       yield
         Ok(bobbyViolationsPage(form.fill(filter), teams, digitalServices, results = Some(results)))
      ).merge

  /**
    * @param selector for reverse routing
    */
  def list(selector: Option[String]): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      for
        rules  <- bobbyService.getRules()
        counts <- serviceDeps.getBobbyRuleViolations()
      yield Ok(bobbyExplorerPage(rules, counts))

case class BobbyReportFilter(
  teamName      : Option[TeamName]       = None
, digitalService: Option[DigitalService] = None
, repoType      : Option[RepoType]       = None
, flag          : SlugInfoFlag           = SlugInfoFlag.Latest
, isActive      : Option[Boolean]        = None
)

object BobbyReportFilter:
  lazy val form: Form[BobbyReportFilter] =
    Form(
      Forms.mapping(
        "teamName"       -> Forms.optional(Forms.of[TeamName])
      , "digitalService" -> Forms.optional(Forms.of[DigitalService])
      , "repoType"       -> Forms.optional(Forms.of[RepoType])
      , "flag"           -> Forms.optional(Forms.of[SlugInfoFlag]).transform(_.getOrElse(SlugInfoFlag.Latest), Some.apply)
      , "isActive"       -> Forms.optional(Forms.boolean)
      )(BobbyReportFilter.apply)(f => Some(Tuple.fromProductTyped(f)))
    )
