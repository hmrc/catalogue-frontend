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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import cats.implicits._
import javax.inject.{Inject, Singleton}
import play.api.data.{Form, Forms}
import play.api.http.HttpEntity
import play.api.mvc._

import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.model.{BobbyVersionRange, DependencyScope, ServiceWithDependency, TeamName}
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.SlugInfoFlag
import uk.gov.hmrc.cataloguefrontend.service.DependenciesService
import uk.gov.hmrc.cataloguefrontend.util.CsvUtils
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.DependencyExplorerPage

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DependencyExplorerController @Inject() (
  override val mcc   : MessagesControllerComponents,
  trConnector        : TeamsAndRepositoriesConnector,
  dependenciesService: DependenciesService,
  page               : DependencyExplorerPage,
  override val auth  : FrontendAuthComponents
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  import DependencyExplorerController._

  def landing: Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for {
        teams          <- trConnector.allTeams.map(_.map(_.name).sorted)
        groupArtefacts <- dependenciesService.getGroupArtefacts
      } yield Ok(
        page(
          form           = form().fill(
                             SearchForm(
                               team         = "",
                               flag         = SlugInfoFlag.Latest.asString,
                               scope        = List(DependencyScope.Compile.asString),
                               group        = "",
                               artefact     = "",
                               versionRange = ""
                             )
                           ),
          teams          = teams,
          flags          = SlugInfoFlag.values,
          scopes         = DependencyScope.values,
          groupArtefacts = groupArtefacts,
          versionRange   = BobbyVersionRange(None, None, None, ""),
          searchResults  = None,
          pieData        = None
        )
      )
    }

  val search =
    BasicAuthAction.async { implicit request =>
      for {
        teams          <- trConnector.allTeams.map(_.map(_.name).sorted)
        flags          =  SlugInfoFlag.values
        scopes         =  DependencyScope.values
        groupArtefacts <- dependenciesService.getGroupArtefacts
        res <- {
          def pageWithError(msg: String) =
            page(
              form().bindFromRequest().withGlobalError(msg),
              teams,
              flags,
              scopes,
              groupArtefacts,
              versionRange  = BobbyVersionRange(None, None, None, ""),
              searchResults = None,
              pieData       = None
            )
          form()
            .bindFromRequest()
            .fold(
              hasErrors = formWithErrors =>
                Future.successful(
                  BadRequest(
                    page(formWithErrors, teams, flags, scopes, groupArtefacts, versionRange = BobbyVersionRange(None, None, None, ""), searchResults = None, pieData = None)
                  )
                ),
              success = query =>
                (for {
                  versionRange <- EitherT.fromOption[Future](BobbyVersionRange.parse(query.versionRange), BadRequest(pageWithError(s"Invalid version range")))
                  team         =  if (query.team.isEmpty) None else Some(TeamName(query.team))
                  flag         <- EitherT.fromOption[Future](SlugInfoFlag.parse(query.flag), BadRequest(pageWithError("Invalid flag")))
                  scope        <- query.scope.traverse { s =>
                                    EitherT.fromEither[Future](DependencyScope.parse(s))
                                      .leftMap(msg => BadRequest(pageWithError(msg)))
                                  }
                  results      <- EitherT.right[Result] {
                                    dependenciesService
                                      .getServicesWithDependency(team, flag, query.group, query.artefact, versionRange, scope)
                                  }
                  pieData      = if (results.nonEmpty)
                                   Some(
                                     PieData(
                                       "Version spread",
                                       results
                                         .groupBy(r => s"${r.depGroup}:${r.depArtefact}:${r.depVersion}")
                                         .map(r => r._1 -> r._2.size)
                                     )
                                   )
                                 else None
                } yield
                  if (query.asCsv) {
                    val csv    = CsvUtils.toCsv(toRows(results))
                    val source = Source.single(ByteString(csv, "UTF-8"))
                    Result(
                      header = ResponseHeader(200, Map("Content-Disposition" -> "inline; filename=\"depex.csv\"")),
                      body   = HttpEntity.Streamed(source, None, Some("text/csv"))
                    )
                  } else
                    Ok(
                      page(
                        form().bindFromRequest(),
                        teams,
                        flags,
                        scopes,
                        groupArtefacts,
                        versionRange,
                        Some(results),
                        pieData
                      )
                    )).merge
            )
        }
      } yield res
    }

  /** @param versionRange replaces versionOp and version, supporting Maven version range */
  case class SearchForm(
    team        : String,
    flag        : String,
    scope       : List[String],
    group       : String,
    artefact    : String,
    versionRange: String,
    asCsv       : Boolean = false
  )

  def form() = {
    import uk.gov.hmrc.cataloguefrontend.util.FormUtils.{notEmpty, notEmptySeq}
    Form(
      Forms.mapping(
        "team"         -> Forms.text,
        "flag"         -> Forms.text.verifying(notEmpty),
        "scope"        -> Forms.list(Forms.text).verifying(notEmptySeq),
        "group"        -> Forms.text.verifying(notEmpty),
        "artefact"     -> Forms.text.verifying(notEmpty),
        "versionRange" -> Forms.default(Forms.text, ""),
        "asCsv"        -> Forms.boolean
      )(SearchForm.apply)(SearchForm.unapply)
    )
  }
}

object DependencyExplorerController {
  case class PieData(
    title  : String,
    results: Map[String, Int]
  )

  def toRows(seq: Seq[ServiceWithDependency]): Seq[Map[String, String]] =
    seq.flatMap { serviceWithDependency =>
      val m = Map(
        "slugName"    -> serviceWithDependency.slugName,
        "slugVersion" -> serviceWithDependency.slugVersion.toString,
        "team"        -> "",
        "depGroup"    -> serviceWithDependency.depGroup,
        "depArtefact" -> serviceWithDependency.depArtefact,
        "depVersion"  -> serviceWithDependency.depVersion.toString
      )
      if (serviceWithDependency.teams.isEmpty) Seq(m)
      else serviceWithDependency.teams.map(team => m + ("team" -> team.asString))
    }

  def search(team: String = "", flag: SlugInfoFlag, scopes: Seq[DependencyScope], group: String, artefact: String, versionRange: BobbyVersionRange): String =
    uk.gov.hmrc.cataloguefrontend.routes.DependencyExplorerController.search.toString +
      s"?team=$team&flag=${flag.asString}${scopes.map(s => s"&scope[]=${s.asString}").mkString}&group=$group&artefact=$artefact&versionRange=${versionRange.range}"
}
