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

import cats.data.EitherT
import cats.instances.all._
import play.api.data.{Form, Forms}
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.{BobbyVersion, BobbyVersionRange, DependencyScope, TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.model.SlugInfoFlag
import uk.gov.hmrc.cataloguefrontend.service.DependenciesService
import uk.gov.hmrc.cataloguefrontend.util.UrlUtils.encodeQueryParam
import uk.gov.hmrc.cataloguefrontend.{routes => appRoutes}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.MetricsExplorerPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MetricsExplorerController @Inject()(
  mcc: MessagesControllerComponents,
  trConnector: TeamsAndRepositoriesConnector,
  service: DependenciesService,
  page: MetricsExplorerPage
)(implicit val ec: ExecutionContext)
    extends FrontendController(mcc) {

  import DependencyExplorerController._

  def landing: Action[AnyContent] =
    Action.async { implicit request =>
      for {
        teams          <- trConnector.allTeams.map(_.map(_.name).sorted)
        groupArtefacts <- service.getGroupArtefacts
      } yield Ok(
        page(
          form.fill(
            SearchForm(
              team = "",
              flag = SlugInfoFlag.Latest.asString,
              scope = DependencyScope.Compile.asString,
              group = "",
              artefact = ""
            )
          ),
          teams,
          flags = SlugInfoFlag.values,
          scopes = DependencyScope.values,
          groupArtefacts,
          searchResults = None,
          pieData = None
        )
      )
    }

  def search =
    Action.async { implicit request =>
      // first preserve old API
      if (request.queryString.contains("versionOp"))
        (for {
          version <- EitherT.fromOption[Future](
                       request.queryString.get("version").flatMap(_.headOption).map(Version.apply),
                       Redirect(appRoutes.DependencyExplorerController.landing)
                     )
          versionRange <- EitherT.fromOption[Future](
                            request.queryString.get("versionOp").flatMap(_.headOption).flatMap { versionOp =>
                              PartialFunction.condOpt(versionOp) {
                                case ">=" => s"[$version,)"
                                case "<=" => s"(,$version]"
                                case "==" => s"[$version]"
                              }
                            },
                            Redirect(appRoutes.DependencyExplorerController.landing)
                          )
          queryString = request.queryString - "version" - "versionOp" + ("versionRange" -> Seq(versionRange))

          // updating request with new querystring does not update uri!? - build uri manually...
          queryStr = queryString
                       .flatMap {
                         case (k, vs) =>
                           vs.map(v => encodeQueryParam(k) + "=" + encodeQueryParam(v))
                       }
                       .mkString("?", "&", "")
        } yield Redirect(request.path + queryStr)).merge
      // else continue to new API
      else search2(request)
    }

  def search2 =
    Action.async { implicit request =>
      for {
        teams <- trConnector.allTeams.map(_.map(_.name).sorted)
        flags  = SlugInfoFlag.values
        scopes = DependencyScope.values
        groupArtefacts <- service.getGroupArtefacts
        res <- {
          def pageWithError(msg: String) =
            page(
              form.bindFromRequest().withGlobalError(msg),
              teams,
              flags,
              scopes,
              groupArtefacts,
              searchResults = None,
              pieData = None
            )
          form
            .bindFromRequest()
            .fold(
              hasErrors = formWithErrors =>
                Future.successful(
                  BadRequest(
                    page(formWithErrors, teams, flags, scopes, groupArtefacts, searchResults = None, pieData = None)
                  )
                ),
              success = query =>
                (
                  for {
                  flag  <- EitherT.fromOption[Future](SlugInfoFlag.parse(query.flag), BadRequest(pageWithError("Invalid flag")))
                  team = if (query.team.isEmpty) None else Some(TeamName(query.team))
                  scope <- EitherT.fromEither[Future](DependencyScope.parse(query.scope)).leftMap(msg => BadRequest(pageWithError(msg)))
                  hardCodedVersion = BobbyVersionRange.parse("[0.0.0,)").get
                  results <- EitherT.right[Result] {
                               service
                                 .getServicesWithDependency(team, flag, query.group, query.artefact, hardCodedVersion, scope)
                             }
                  pieData = if (results.nonEmpty)
                              Some(
                                PieData(
                                  "Version spread",
                                  results
                                    .groupBy(r => s"${r.depGroup}:${r.depArtefact}:${r.depVersion}")
                                    .map(r => r._1 -> r._2.size)
                                )
                              )
                            else None
                } yield Ok(
                    page(
                      form.bindFromRequest(),
                      teams,
                      flags,
                      scopes,
                      groupArtefacts,
                      Some(results),
                      pieData
                    )
                  )
                  ).merge
            )
        }
      } yield res
    }

  /** @param versionRange replaces versionOp and version, supporting Maven version range */
  case class SearchForm(
    team: String,
    flag: String,
    scope: String,
    group: String,
    artefact: String
  )

  def form() = {
    import uk.gov.hmrc.cataloguefrontend.util.FormUtils.notEmpty
    Form(
      Forms.mapping(
        "team"         -> Forms.text,
        "flag"         -> Forms.text.verifying(notEmpty),
        "scope"        -> Forms.default(Forms.text, DependencyScope.Compile.asString),
        "group"        -> Forms.text.verifying(notEmpty),
        "artefact"     -> Forms.text.verifying(notEmpty)
      )(SearchForm.apply)(SearchForm.unapply)
    )
  }
}


