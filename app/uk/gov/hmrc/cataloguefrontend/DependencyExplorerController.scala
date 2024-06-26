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

package uk.gov.hmrc.cataloguefrontend

import cats.data.EitherT
import cats.implicits._
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.data.{Form, Forms}
import play.api.http.HttpEntity
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.model.{DependencyScope, RepoWithDependency}
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.{SlugInfoFlag, TeamName, VersionRange}
import uk.gov.hmrc.cataloguefrontend.service.DependenciesService
import uk.gov.hmrc.cataloguefrontend.util.{CsvUtils, FormUtils}
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.DependencyExplorerPage

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DependencyExplorerController @Inject() (
  override val mcc   : MessagesControllerComponents,
  trConnector        : TeamsAndRepositoriesConnector,
  dependenciesService: DependenciesService,
  page               : DependencyExplorerPage,
  override val auth  : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  import DependencyExplorerController._

  def landing: Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for
        teams          <- trConnector.allTeams().map(_.map(_.name).sorted)
        groupArtefacts <- dependenciesService.getGroupArtefacts()
      yield Ok(
        page(
          form           = form.fill(
                             SearchForm(
                               team         = "",
                               flag         = SlugInfoFlag.Latest.asString,
                               scope        = List(DependencyScope.Compile.asString),
                               group        = "",
                               artefact     = "",
                               versionRange = "",
                               repoType     = List(RepoType.Service.asString)
                             )
                           ),
          teams          = teams,
          flags          = SlugInfoFlag.values,
          scopes         = DependencyScope.valuesAsSeq,
          groupArtefacts = groupArtefacts,
          versionRange   = VersionRange(None, None, None, ""),
          searchResults  = None,
          pieData        = None,
          repoTypes      = RepoType.valuesAsSeq.filterNot(_ == RepoType.Prototype)
        )
      )
    }

  def search(
    group       : String,
    artefact    : String,
    versionRange: Option[String],
    team        : Option[TeamName],
    flag        : Option[String],
    `scope[]`   : Option[Seq[String]],
    `repoType[]`: Option[Seq[String]],
    asCsv       : Boolean
  ): Action[AnyContent] =
    BasicAuthAction.async(implicit request =>
      for
        teams          <- trConnector.allTeams().map(_.map(_.name).sorted)
        flags          =  SlugInfoFlag.values
        scopes         =  DependencyScope.valuesAsSeq
        repoTypes      =  RepoType.valuesAsSeq.filterNot(_ == RepoType.Prototype)
        groupArtefacts <- dependenciesService.getGroupArtefacts()
        filledForm     =
          SearchForm(
            group        = group,
            artefact     = artefact,
            versionRange = versionRange.getOrElse(VersionRange("[0.0.0,]").range),
            team         = team.fold("")(_.asString),
            flag         = flag.getOrElse(SlugInfoFlag.Latest.asString),
            scope        = `scope[]`.getOrElse(Seq(DependencyScope.Compile.asString)),
            repoType     = `repoType[]`.getOrElse(Seq(RepoType.Service.asString))
          )
        res <- {
          def pageWithError(msg: String) =
            page(
              form.fill(filledForm).withGlobalError(msg),
              teams,
              flags,
              repoTypes,
              scopes,
              groupArtefacts,
              versionRange  = VersionRange(None, None, None, ""),
              searchResults = None,
              pieData       = None
            )
          form
            .fill(filledForm)
            .fold(
              hasErrors = formWithErrors => {
                Future.successful(
                  BadRequest(
                    page(formWithErrors, teams, flags, repoTypes, scopes, groupArtefacts, versionRange = VersionRange(None, None, None, ""), searchResults = None, pieData = None)
                  )
                )
              },
              success = query =>
                (for
                  versionRange <- EitherT.fromOption[Future](VersionRange.parse(query.versionRange), BadRequest(pageWithError(s"Invalid version range")))
                  team         =  Option.when(query.team.nonEmpty)(TeamName(query.team))
                  flag         <- EitherT.fromOption[Future](SlugInfoFlag.parse(query.flag), BadRequest(pageWithError("Invalid flag")))
                  scope        <- query.scope.traverse: s =>
                                    EitherT.fromEither[Future](DependencyScope.parse(s))
                                      .leftMap(msg => BadRequest(pageWithError(msg)))
                  repoType     <- query.repoType.traverse: s =>
                                    EitherT.fromEither[Future](RepoType.parse(s))
                                      .leftMap(msg => BadRequest(pageWithError(msg)))
                  results      <- EitherT.right[Result]:
                                    dependenciesService
                                      .getServicesWithDependency(team, flag, repoType, query.group, query.artefact, versionRange, scope)
                  pieData      = if results.nonEmpty
                                 then Some(
                                        PieData(
                                          "Version spread",
                                          results
                                            .groupBy(r => s"${r.depGroup}:${r.depArtefact}:${r.depVersion}")
                                            .map(r => r._1 -> r._2.size)
                                        )
                                      )
                                 else None
                yield
                  if asCsv
                  then
                    val csv    = CsvUtils.toCsv(toRows(results, team))
                    val source = Source.single(ByteString(csv, "UTF-8"))
                    Result(
                      header = ResponseHeader(200, Map("Content-Disposition" -> "inline; filename=\"depex.csv\"")),
                      body   = HttpEntity.Streamed(source, None, Some("text/csv"))
                    )
                  else
                    Ok(
                      page(
                        form.fill(filledForm),
                        teams,
                        flags,
                        repoTypes,
                        scopes,
                        groupArtefacts,
                        versionRange,
                        Some(results),
                        pieData
                      )
                    )
                ).merge
            )
        }
      yield res
    )

  /** @param versionRange replaces versionOp and version, supporting Maven version range */
  case class SearchForm(
    team        : String,
    flag        : String,
    repoType    : Seq[String],
    scope       : Seq[String],
    group       : String,
    artefact    : String,
    versionRange: String,
    asCsv       : Boolean = false
  )

  val form: Form[SearchForm] =
    Form(
      Forms.mapping(
        "team"         -> Forms.default(Forms.text, ""),
        "flag"         -> Forms.text.verifying(FormUtils.notEmpty),
        "repoType"     -> Forms.seq(Forms.text),
        "scope"        -> Forms.seq(Forms.text),
        "group"        -> Forms.text.verifying(FormUtils.notEmpty),
        "artefact"     -> Forms.text.verifying(FormUtils.notEmpty),
        "versionRange" -> Forms.default(Forms.text, ""),
        "asCsv"        -> Forms.boolean
      )(SearchForm.apply)(f => Some(Tuple.fromProductTyped(f)))
        .verifying(flagConstraint)
    )

  val flagConstraint: Constraint[SearchForm] =
    Constraint(""): form =>
      if form.flag != SlugInfoFlag.Latest.asString && form.repoType != List(RepoType.Service.asString)
      then Invalid(Seq(ValidationError(s"Flag Integration is only applicable to Service")))
      else Valid

end DependencyExplorerController

object DependencyExplorerController {
  case class PieData(
    title  : String,
    results: Map[String, Int]
  )

  def toRow(repoWithDependency: RepoWithDependency, teamName: String): Seq[(String, String)] =
    Seq(
      "repoName"    -> repoWithDependency.repoName,
      "repoVersion" -> repoWithDependency.repoVersion.toString,
      "team"        -> teamName,
      "depGroup"    -> repoWithDependency.depGroup,
      "depArtefact" -> repoWithDependency.depArtefact,
      "depVersion"  -> repoWithDependency.depVersion.toString
    )

  def toRows(seq: Seq[RepoWithDependency], teamFilter: Option[TeamName]): Seq[Seq[(String, String)]] =
    seq.flatMap: repoWithDependency =>
      if repoWithDependency.teams.nonEmpty
      then
        teamFilter.fold(repoWithDependency.teams)(List(_))
          .map(team => toRow(repoWithDependency, team.asString))
      else
        Seq(toRow(repoWithDependency, ""))

  def groupArtefactFromForm(form: Form[?]): Option[String] =
    for
      g <- form("group").value.filter(_.nonEmpty)
      a <- form("artefact").value.filter(_.nonEmpty)
    yield s"$g:$a"

  def search(
    team        : Option[TeamName]      = None,
    flag        : SlugInfoFlag,
    scopes      : Seq[DependencyScope],
    repoTypes   : Option[Seq[RepoType]] = None,
    group       : String,
    artefact    : String,
    versionRange: VersionRange
  ): String =
    uk.gov.hmrc.cataloguefrontend.routes.DependencyExplorerController.search(
      group        = group,
      artefact     = artefact,
      `scope[]`    = Some(scopes.map(_.asString)),
      `repoType[]` = repoTypes.map(_.map(_.asString)),
      flag         = Some(flag.asString),
      team         = team,
      versionRange = Some(versionRange.range),
      asCsv        = false,
    ).toString
}
