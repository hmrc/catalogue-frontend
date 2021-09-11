/*
 * Copyright 2021 HM Revenue & Customs
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

import cats.implicits.none
import play.api.data.{Form, Forms}
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.metrics.connector.MetricsConnector
import uk.gov.hmrc.cataloguefrontend.metrics.model.ServiceMetricsEntry
import uk.gov.hmrc.cataloguefrontend.metrics.views.SearchForm
import uk.gov.hmrc.cataloguefrontend.metrics.views.html.MetricsDisplayPage
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class MetricsOverviewController @Inject()(
                                           mcc: MessagesControllerComponents,
                                           page: MetricsDisplayPage,
                                           metricsConnector: MetricsConnector
                                         )(implicit val ec: ExecutionContext)
  extends FrontendController(mcc) {

  def landing: Action[AnyContent] =
    Action.async { implicit request =>
      for {
        response <- metricsConnector.query(none)
        entries = response.metrics.map(ServiceMetricsEntry.apply)
      } yield Ok(
        page(
          form.fill(
            SearchForm(
              none
            )
          ),
          entries
        )
      )
    }

  def search =
    Action.async { implicit request =>
      for {
        res <- {
          form
            .bindFromRequest()
            .fold(
              hasErrors = formWithErrors =>
                Future.successful(
                  BadRequest(
                    page(
                      formWithErrors,
                      metricsEntries = Seq.empty
                    )
                  )
                ),
              success = query =>
                for {
                  response <- metricsConnector.query(
                    maybeTeam = query.team
                  )
                  entries = response.metrics.map(ServiceMetricsEntry.apply)
                } yield Ok(
                  page(
                    form.bindFromRequest(),
                    entries
                  )
                )
            )
        }
      } yield res
    }

  def form(): Form[SearchForm] = {
    Form(
      Forms.mapping(
        "team"              -> Forms.optional(Forms.text),
      )(SearchForm.applyRaw)(SearchForm.unapplyRaw)
    )
  }
}
