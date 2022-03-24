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

package uk.gov.hmrc.cataloguefrontend.leakdetection

import play.api.data.Form
import play.api.data.Forms.{boolean, mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.leakdetection.LeakDetectionExplorerFilter.form
import uk.gov.hmrc.cataloguefrontend.AuthController
import uk.gov.hmrc.internalauth.client._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.leakdetection._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class LeakDetectionController @Inject() (
  mcc: MessagesControllerComponents,
  rulesPage: LeakDetectionRulesPage,
  repositoriesPage: LeakDetectionRepositoriesPage,
  repositoryPage: LeakDetectionRepositoryPage,
  leaksPage: LeakDetectionLeaksPage,
  leakDetectionService: LeakDetectionService,
  exemptionsPage: LeakDetectionExemptionsPage,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  auth: FrontendAuthComponents
)(implicit val ec: ExecutionContext)
    extends FrontendController(mcc)
    with play.api.i18n.I18nSupport {

  def ruleSummaries(): Action[AnyContent] =
    Action.async { implicit request =>
      leakDetectionService.ruleSummaries.map(s => Ok(rulesPage(s)))
    }

  def repoSummaries(includeWarnings: Boolean, includeExemptions: Boolean, includeViolations: Boolean, includeNonIssues: Boolean): Action[AnyContent] =
    Action.async { implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(repositoriesPage(Seq.empty, Seq.empty, Seq.empty, formWithErrors, includeWarnings, includeExemptions, includeViolations, includeNonIssues))),
          validForm =>
            for {
              summaries <- leakDetectionService.repoSummaries(validForm.rule, validForm.team, includeWarnings, includeExemptions, includeViolations, includeNonIssues)
              teams     <- teamsAndRepositoriesConnector.allTeams
            } yield Ok(repositoriesPage(summaries._1, summaries._2, teams.sortBy(_.name), form.fill(validForm), includeWarnings, includeExemptions, includeViolations, includeNonIssues))
        )
    }

  def branchSummaries(repository: String, includeNonIssues: Boolean): Action[AnyContent] =
    Action.async { implicit request =>
      leakDetectionService.branchSummaries(repository, includeNonIssues).map(s => Ok(repositoryPage(repository, includeNonIssues, s)))
    }

  def leaksPermission(repository: String): Predicate =
    Predicate.Permission(Resource.from("repository-leaks", repository), IAAction("READ"))

  def report(repository: String, branch: String): Action[AnyContent] =
    auth
      .authenticatedAction(
        continueUrl = AuthController.continueUrl(routes.LeakDetectionController.report(repository, branch))
      )
      .async { implicit request =>
        for {
          isAuthorised <- auth.authorised(None, Retrieval.hasPredicate(leaksPermission(repository)))
          report       <- leakDetectionService.report(repository, branch)
          leaks        <- leakDetectionService.reportLeaks(report._id)
          warnings     <- leakDetectionService.reportWarnings(report._id)
          resolutionUrl = leakDetectionService.resolutionUrl
        } yield Ok(leaksPage(report, report.exclusions, leaks, warnings, resolutionUrl, isAuthorised))
      }

  def reportExemptions(repository: String, branch: String): Action[AnyContent] =
    Action.async { implicit request =>
      for {
        report <- leakDetectionService.report(repository, branch)
        exemptions <- leakDetectionService.reportExemptions(report._id)
      } yield Ok(exemptionsPage(repository, branch, exemptions, report.unusedExemptions))
    }
}

case class LeakDetectionExplorerFilter(
  rule: Option[String] = None,
  team: Option[String] = None
)

object LeakDetectionExplorerFilter {
  lazy val form: Form[LeakDetectionExplorerFilter] = Form(
    mapping(
      "rule" -> optional(text),
      "team" -> optional(text)
    )(LeakDetectionExplorerFilter.apply)(LeakDetectionExplorerFilter.unapply)
  )
}
