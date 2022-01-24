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

package uk.gov.hmrc.cataloguefrontend

import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.service.LeakDetectionService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.{LeakDetectionPage, LeakDetectionRuleExplorerPage}
import uk.gov.hmrc.cataloguefrontend.LeakDetectionRuleExplorerFilter.form

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class LeakDetectionController @Inject() (
  mcc: MessagesControllerComponents,
  page: LeakDetectionPage,
  ruleExplorerPage: LeakDetectionRuleExplorerPage,
  leakDetectionService: LeakDetectionService,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
)(implicit val ec: ExecutionContext)
    extends FrontendController(mcc) {

  def repoSummaries(): Action[AnyContent] =
    Action.async { implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(BadRequest(ruleExplorerPage(Seq.empty, Seq.empty, Seq.empty, formWithErrors))),
          validForm =>
            for {
              rules <- leakDetectionService.repoSummaries(validForm.rule, validForm.team)
              teams <- teamsAndRepositoriesConnector.allTeams
            } yield Ok(ruleExplorerPage(rules._1, rules._2, teams.sortBy(_.name), form.fill(validForm)))
        )
    }

  def ruleSummaries(): Action[AnyContent] =
    Action.async { implicit request =>
      for {
        rules <- leakDetectionService.ruleSummaries
        response = Ok(page(rules))
      } yield response
    }
}

case class LeakDetectionRuleExplorerFilter(
                                   rule: Option[String] = None,
                                   team: Option[String] = None
                                 )

object LeakDetectionRuleExplorerFilter {
  lazy val form: Form[LeakDetectionRuleExplorerFilter] = Form(
    mapping(
      "rule" -> optional(text),
      "team" -> optional(text)
    )(LeakDetectionRuleExplorerFilter.apply)(LeakDetectionRuleExplorerFilter.unapply)
  )
}