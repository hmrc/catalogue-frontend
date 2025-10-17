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

package uk.gov.hmrc.cataloguefrontend.leakdetection

import cats.data.EitherT
import play.api.data.{Form, Forms}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result, RequestHeader, Request}
import uk.gov.hmrc.cataloguefrontend.auth.{AuthController, CatalogueAuthBuilders}
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.leakdetection.view.html._
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, TeamName}
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class LeakDetectionController @Inject()(
  override val mcc             : MessagesControllerComponents,
  rulesPage                    : LeakDetectionRulesPage,
  repositoriesPage             : LeakDetectionRepositoriesPage,
  repositoryPage               : LeakDetectionRepositoryPage,
  leaksPage                    : LeakDetectionLeaksPage,
  leakDetectionService         : LeakDetectionService,
  exemptionsPage               : LeakDetectionExemptionsPage,
  draftPage                    : LeakDetectionDraftPage,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  override val auth            : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders
     with play.api.i18n.I18nSupport:

  val ruleSummaries: Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      leakDetectionService.ruleSummaries().map(s => Ok(rulesPage(s)))

  /**
    * @param team for reverse routing
    * @param digitalService for reverse routing
    */
  def repoSummaries(
    team             : Option[TeamName]
  , digitalService   : Option[DigitalService]
  , includeWarnings  : Boolean
  , includeExemptions: Boolean
  , includeViolations: Boolean
  , includeNonIssues : Boolean
  ): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given Request[AnyContent] = request
      (for
         teams           <- EitherT.right[Result](teamsAndRepositoriesConnector.allTeams())
         digitalServices <- EitherT.right[Result](teamsAndRepositoriesConnector.allDigitalServices())
         rules           <- EitherT.right[Result](leakDetectionService.rules().map(_.filterNot(_.draft).map(_.id)))
         form            =  LeakDetectionExplorerFilter.form.bindFromRequest()
         filter          <- EitherT.fromEither[Future](form.fold(
                             formErrors => Left(BadRequest(repositoriesPage(formErrors, rules, teams, digitalServices, includeWarnings, includeExemptions, includeViolations, includeNonIssues, results = None)))
                           , formObject => Right(formObject)
                            ))
         results         <- EitherT.right[Result]:
                              leakDetectionService.repoSummaries(filter.rule, filter.team, filter.digitalService, includeWarnings, includeExemptions, includeViolations, includeNonIssues)
       yield
         Ok(repositoriesPage(form.fill(filter), rules, teams, digitalServices, includeWarnings, includeExemptions, includeViolations, includeNonIssues, results = Some(results)))
      ).merge

  def branchSummaries(repository: String, includeNonIssues: Boolean): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      for
        isAuthorised    <- auth.verify(Retrieval.hasPredicate(leaksPermission(repository, "RESCAN")))
        branchSummaries <- leakDetectionService.branchSummaries(repository, includeNonIssues)
      yield Ok(repositoryPage(repository, includeNonIssues, branchSummaries, isAuthorised.getOrElse(false)))

  private def leaksPermission(repository: String, action: String): Predicate =
    Predicate.Permission(Resource.from("repository-leaks", repository), IAAction(action))

  val draftReports: Action[AnyContent] =
    BasicAuthAction.async: request =>
      given Request[AnyContent] = request
      (for
         rules   <- EitherT.right[Result](leakDetectionService.rules().map(_.filter(_.draft)))
         form    =  LeakDetectionExplorerFilter.form.bindFromRequest()
         filter  <- EitherT.fromEither[Future](form.fold(
                     formErrors => Left(BadRequest(draftPage(formErrors, rules, results = None)))
                   , formObject => Right(formObject)
                    ))
         results <- EitherT.right[Result]:
                      leakDetectionService.draftReports(filter.rule)
       yield
         Ok(draftPage(form.fill(filter), rules, results = Some(results)))
      ).merge

  def report(repository: String, branch: String): Action[AnyContent] =
    auth
      .authenticatedAction(
        continueUrl = AuthController.continueUrl(routes.LeakDetectionController.report(repository, branch)),
        retrieval   = Retrieval.hasPredicate(leaksPermission(repository, "READ"))
      )
      .async: request =>
        given AuthenticatedRequest[AnyContent, Boolean] = request
        val isAuthorised  = request.retrieval
        for
          report                 <- leakDetectionService.report(repository, branch)
          leaks                  <- leakDetectionService.reportLeaks(report.id)
          warnings               <- leakDetectionService.reportWarnings(report.id)
          resolutionUrl          =  leakDetectionService.resolutionUrl
          removeSensitiveInfoUrl = leakDetectionService.removeSensitiveInfoUrl()
        yield Ok(leaksPage(report, report.exclusions, leaks, warnings, resolutionUrl, removeSensitiveInfoUrl, isAuthorised))

  def reportExemptions(repository: String, branch: String): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      for
        report     <- leakDetectionService.report(repository, branch)
        exemptions <- leakDetectionService.reportExemptions(report.id)
      yield Ok(exemptionsPage(repository, branch, exemptions, report.unusedExemptions))

  def rescan(repository: String, branch: String): Action[AnyContent] =
    auth
      .authorizedAction(
        continueUrl = AuthController.continueUrl(routes.LeakDetectionController.branchSummaries(repository)),
        predicate   = leaksPermission(repository, "RESCAN")
      ).async: request =>
        given RequestHeader = request
        for
          _ <- leakDetectionService.rescan(repository, branch)
        yield Redirect(routes.LeakDetectionController.report(repository, branch))

end LeakDetectionController

case class LeakDetectionExplorerFilter(
  rule          : Option[String]         = None,
  team          : Option[TeamName]       = None,
  digitalService: Option[DigitalService] = None
)

object LeakDetectionExplorerFilter:
  lazy val form: Form[LeakDetectionExplorerFilter] =
    Form:
      Forms.mapping(
        "rule"           -> Forms.optional(Forms.text),
        "team"           -> Forms.optional(Forms.of[TeamName]),
        "digitalService" -> Forms.optional(Forms.of[DigitalService]),
      )(LeakDetectionExplorerFilter.apply)(r => Some(Tuple.fromProductTyped(r)))
