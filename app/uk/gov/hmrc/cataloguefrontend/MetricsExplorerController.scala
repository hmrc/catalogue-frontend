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
import uk.gov.hmrc.cataloguefrontend.connector.MetricsConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.{DependencyName, GroupName, MetricsEntry, RepositoryName}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.MetricsExplorerPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MetricsExplorerController @Inject()(
  mcc: MessagesControllerComponents,
  page: MetricsExplorerPage,
  metricsConnector: MetricsConnector
)(implicit val ec: ExecutionContext)
    extends FrontendController(mcc) {

  def landing: Action[AnyContent] =
    Action.async { implicit request =>
      for {
        groups <- metricsConnector.getAllGroups
        dependencies <- metricsConnector.getAllDependencies
        repositories <- metricsConnector.getAllRepositories
      } yield Ok(
        page(
          form.fill(
            SearchForm(
              none, none, none
            )
          ),
          groups,
          repositories,
          dependencies,
          metricsEntries = Seq.empty
        )
      )
    }

  def search =
    Action.async { implicit request =>
      for {
        groups <- metricsConnector.getAllGroups
        dependencies <- metricsConnector.getAllDependencies
        repositories <- metricsConnector.getAllRepositories
        res <- {
          form
            .bindFromRequest()
            .fold(
              hasErrors = formWithErrors =>
                Future.successful(
                  BadRequest(
                    page(
                      formWithErrors,
                      groups,
                      repositories,
                      dependencies,
                      metricsEntries = Seq.empty
                    )
                  )
                ),
              success = query =>
                  for {
                  results <- metricsConnector.query(
                      maybeGroup = query.group,
                      maybeName = query.dependency,
                      maybeRepository = query.repository
                    )
                  _ <- Future.successful(println(s"group: ${query.group}, name: ${query.dependency}, repo: ${query.repository} returned ${results.metrics.size} entries "))
                  metricsEntries = MetricsEntry(results.metrics)
                } yield Ok(
                    page(
                      form.bindFromRequest(),
                      groups,
                      repositories,
                      dependencies,
                      metricsEntries
                    )
                  )
            )
        }
      } yield res
    }

  /** @param versionRange replaces versionOp and version, supporting Maven version range */
  final case class SearchForm(
                               group: Option[GroupName],
                               dependency: Option[DependencyName],
                               repository: Option[RepositoryName]
  )

  object SearchForm {
    def applyRaw(
               group: Option[String],
               dependency: Option[String],
               repository: Option[String]
             ): SearchForm = SearchForm.apply(
      group.map(GroupName.apply),
      dependency.map(DependencyName.apply),
      repository.map(RepositoryName.apply)
    )

    def unapplyRaw(searchForm: SearchForm): Option[(Option[String], Option[String], Option[String])] = unapply(searchForm).map{ case(g, d, r) =>
      (g.map(_.value), d.map(_.value), r.map(_.value))
    }
  }

  def form(): Form[SearchForm] = {
    Form(
      Forms.mapping(
        "group"             -> Forms.optional(Forms.text),
        "dependency"        -> Forms.optional(Forms.text),
        "repository"        -> Forms.optional(Forms.text),
      )(SearchForm.applyRaw)(SearchForm.unapplyRaw)
    )
  }
}


