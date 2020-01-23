/*
 * Copyright 2020 HM Revenue & Customs
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
import cats.instances.all._
import javax.inject.{Inject, Singleton}
import play.api.data.{Form, Forms}
import play.api.http.HttpEntity
import play.api.i18n.MessagesProvider
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.connector.model.{BobbyVersionRange, ServiceWithDependency, TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.SlugInfoFlag
import uk.gov.hmrc.cataloguefrontend.{ routes => appRoutes }
import uk.gov.hmrc.cataloguefrontend.service.DependenciesService
import uk.gov.hmrc.cataloguefrontend.util.CsvUtils
import uk.gov.hmrc.cataloguefrontend.util.UrlUtils.encodeQueryParam
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.DependencyExplorerPage

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DependencyExplorerController @Inject()(
    mcc        : MessagesControllerComponents
  , trConnector: TeamsAndRepositoriesConnector
  , service    : DependenciesService
  , page       : DependencyExplorerPage
  )(implicit val ec: ExecutionContext
  ) extends FrontendController(mcc) {

  import DependencyExplorerController._

  def landing: Action[AnyContent] =
    Action.async { implicit request =>
      for {
        teams          <- trConnector.allTeams.map(_.map(_.name).sorted)
        flags          =  SlugInfoFlag.values
        groupArtefacts <- service.getGroupArtefacts
      } yield Ok(page(
            form.fill(SearchForm("", SlugInfoFlag.Latest.asString, "", "", ""))
          , teams
          , flags
          , groupArtefacts
          , versionRange  = BobbyVersionRange(None, None, None, "")
          , searchResults = None
          , pieData       = None
          ))
    }


  def search =
    Action.async { implicit request =>
      // first preserve old API
      if (request.queryString.contains("versionOp")) {
        (for {
          version       <- EitherT.fromOption[Future](
                               request.queryString.get("version").flatMap(_.headOption).flatMap(Version.parse)
                             , Redirect(appRoutes.DependencyExplorerController.landing)
                             )
          versionRange  <- EitherT.fromOption[Future](
                               request.queryString.get("versionOp").flatMap(_.headOption).flatMap { versionOp =>
                                 PartialFunction.condOpt(versionOp) {
                                   case ">=" => s"[$version,)"
                                   case "<=" => s"(,$version]"
                                   case "==" => s"[$version]"
                                 }
                               }
                             , Redirect(appRoutes.DependencyExplorerController.landing)
                             )
          queryString   =  request.queryString - "version" - "versionOp" + ("versionRange" -> Seq(versionRange))

         // updating request with new querystring does not update uri!? - build uri manually...
          queryStr      =  queryString.flatMap { case (k, vs) =>
                             vs.map(v => encodeQueryParam(k) + "=" + encodeQueryParam(v))
                           }.mkString("?", "&", "")
         } yield Redirect(request.path + queryStr)
        ).merge
      // else continue to new API
      } else search2(request)
    }

  def search2 =
    Action.async { implicit request =>
      for {
        teams          <- trConnector.allTeams.map(_.map(_.name).sorted)
        flags          =  SlugInfoFlag.values
        groupArtefacts <- service.getGroupArtefacts
        res            <- {
          def pageWithError(msg: String) = page(
              form.bindFromRequest().withGlobalError(msg)
            , teams
            , flags
            , groupArtefacts
            , versionRange  = BobbyVersionRange(None, None, None, "")
            , searchResults = None
            , pieData       = None
            )
          form
            .bindFromRequest()
            .fold(
                hasErrors = formWithErrors => Future.successful(BadRequest(page(formWithErrors, teams, flags, groupArtefacts, versionRange = BobbyVersionRange(None, None, None, ""), searchResults = None, pieData = None)))
              , success   = query =>
                  (for {
                    versionRange <- EitherT.fromOption[Future](BobbyVersionRange.parse(query.versionRange), BadRequest(pageWithError(s"Invalid version range")))
                    team         =  if (query.team.isEmpty) None else Some(TeamName(query.team))
                    flag         <- EitherT.fromOption[Future](SlugInfoFlag.parse(query.flag), BadRequest(pageWithError("Invalid flag")))
                    results      <- EitherT.right[Result] {
                                      service
                                        .getServicesWithDependency(team, flag, query.group, query.artefact, versionRange)
                                    }
                    pieData      =  PieData(
                                        "Version spread"
                                      , results
                                          .groupBy(r => s"${r.depGroup}:${r.depArtefact}:${r.depVersion}")
                                          .map(r => r._1 -> r._2.size)
                                      )
                  } yield
                    if (query.asCsv)  {
                      val csv    = CsvUtils.toCsv(toRows(results))
                      val source = Source.single(ByteString(csv, "UTF-8"))
                      Result(
                        header = ResponseHeader(200, Map("Content-Disposition" -> "inline; filename=\"depex.csv\"")),
                        body   = HttpEntity.Streamed(source, None, Some("text/csv"))
                      )
                    }
                    else Ok(page(
                        form.bindFromRequest()
                      , teams
                      , flags
                      , groupArtefacts
                      , versionRange
                      , Some(results)
                      , Some(pieData)
                      ))
                  ).merge
              )
        }
      } yield res
    }


  /** @param versionRange replaces versionOp and version, supporting Maven version range */
  case class SearchForm(
      team        : String
    , flag        : String
    , group       : String
    , artefact    : String
    , versionRange: String
    , asCsv       : Boolean = false
    )

  def form(implicit messagesProvider: MessagesProvider) = {
    import uk.gov.hmrc.cataloguefrontend.util.FormUtils.notEmpty
    Form(
      Forms.mapping(
          "team"         -> Forms.text
        , "flag"         -> Forms.text.verifying(notEmpty)
        , "group"        -> Forms.text.verifying(notEmpty)
        , "artefact"     -> Forms.text.verifying(notEmpty)
        , "versionRange" -> Forms.default(Forms.text, "")
        , "asCsv"        -> Forms.boolean
        )(SearchForm.apply)(SearchForm.unapply)
    )
  }
}

object DependencyExplorerController {
  case class PieData(
      title  : String
    , results: Map[String, Int]
    )


  def toRows(seq: Seq[ServiceWithDependency]): Seq[Map[String, String]] =
    seq.flatMap { serviceWithDependency =>
      val m = Map(
          "slugName"           -> serviceWithDependency.slugName
        , "slugVersion"        -> serviceWithDependency.slugVersion
        , "team"               -> ""
        , "depGroup"           -> serviceWithDependency.depGroup
        , "depArtefact"        -> serviceWithDependency.depArtefact
        , "depVersion"         -> serviceWithDependency.depVersion
        , "depSemanticVersion" -> serviceWithDependency.depSemanticVersion.map(_.toString).getOrElse("")
        )
      if (serviceWithDependency.teams.isEmpty) Seq(m)
      else serviceWithDependency.teams.map(team => m + ("team" -> team.asString))
    }

  def search(team: String = "", flag: SlugInfoFlag, group: String, artefact: String, versionRange: BobbyVersionRange): String =
    uk.gov.hmrc.cataloguefrontend.routes.DependencyExplorerController.search() +
      s"?team=$team&flag=${flag.asString}&group=$group&artefact=$artefact&versionRange=${versionRange.range}"
}