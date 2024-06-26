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

import play.api.data.{Form, Forms}
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.{AuthController, CatalogueAuthBuilders}
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.leakdetection.LeakDetectionExplorerFilter.form
import uk.gov.hmrc.cataloguefrontend.model.TeamName
import uk.gov.hmrc.cataloguefrontend.view.leakdetection.html._
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
    BasicAuthAction.async { implicit request =>
      leakDetectionService.ruleSummaries().map(s => Ok(rulesPage(s)))
    }

  def repoSummaries(
    includeWarnings  : Boolean,
    includeExemptions: Boolean,
    includeViolations: Boolean,
    includeNonIssues : Boolean
  ): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(repositoriesPage(Seq.empty, Seq.empty, Seq.empty, formWithErrors, includeWarnings, includeExemptions, includeViolations, includeNonIssues))),
          validForm =>
            for
              summaries <- leakDetectionService.repoSummaries(validForm.rule, validForm.team, includeWarnings, includeExemptions, includeViolations, includeNonIssues)
              rules     <- leakDetectionService.rules().map(_.filterNot(_.draft).map(_.id))
              teams     <- teamsAndRepositoriesConnector.allTeams()
            yield Ok(repositoriesPage(rules, summaries, teams.sortBy(_.name), form.fill(validForm), includeWarnings, includeExemptions, includeViolations, includeNonIssues))
        )
    }

  def branchSummaries(repository: String, includeNonIssues: Boolean): Action[AnyContent] =
    BasicAuthAction
      .async { implicit request =>
        for
          isAuthorised    <- auth.verify(Retrieval.hasPredicate(leaksPermission(repository, "RESCAN")))
          branchSummaries <- leakDetectionService.branchSummaries(repository, includeNonIssues)
        yield Ok(repositoryPage(repository, includeNonIssues, branchSummaries, isAuthorised.getOrElse(false)))
      }

  def leaksPermission(repository: String, action: String): Predicate =
    Predicate.Permission(Resource.from("repository-leaks", repository), IAAction(action))

  val draftReports: Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(draftPage(Seq.empty, Seq.empty, formWithErrors))),
          validForm =>
            for
              reports <- leakDetectionService.draftReports(validForm.rule)
              rules   <- leakDetectionService.rules().map(_.filter(_.draft))
            yield Ok(draftPage(rules, reports, form.fill(validForm)))
        )
    }

  def report(repository: String, branch: String): Action[AnyContent] =
    auth
      .authenticatedAction(
        continueUrl = AuthController.continueUrl(routes.LeakDetectionController.report(repository, branch))
      )
      .async { implicit request =>
        for
          isAuthorised <- auth.authorised(None, Retrieval.hasPredicate(leaksPermission(repository, "READ")))
          report       <- leakDetectionService.report(repository, branch)
          leaks        <- leakDetectionService.reportLeaks(report.id)
          warnings     <- leakDetectionService.reportWarnings(report.id)
          resolutionUrl = leakDetectionService.resolutionUrl
        yield Ok(leaksPage(report, report.exclusions, leaks, warnings, resolutionUrl, isAuthorised))
      }

  def reportExemptions(repository: String, branch: String): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for
        report     <- leakDetectionService.report(repository, branch)
        exemptions <- leakDetectionService.reportExemptions(report.id)
      yield Ok(exemptionsPage(repository, branch, exemptions, report.unusedExemptions))
    }

  def rescan(repository: String, branch: String): Action[AnyContent] =
    auth
      .authorizedAction(
        continueUrl = AuthController.continueUrl(routes.LeakDetectionController.branchSummaries(repository)),
        predicate = leaksPermission(repository, "RESCAN")
      ).async { implicit request =>
        for
          _ <- leakDetectionService.rescan(repository, branch)
        yield Redirect(routes.LeakDetectionController.report(repository, branch))
      }

end LeakDetectionController

case class LeakDetectionExplorerFilter(
  rule: Option[String]   = None,
  team: Option[TeamName] = None
)

object LeakDetectionExplorerFilter:
  lazy val form: Form[LeakDetectionExplorerFilter] =
    Form(
      mapping(
        "rule" -> optional(text),
        "team" -> optional(Forms.of[TeamName](TeamName.formFormat)),
      )(LeakDetectionExplorerFilter.apply)(r => Some(Tuple.fromProductTyped(r)))
    )
