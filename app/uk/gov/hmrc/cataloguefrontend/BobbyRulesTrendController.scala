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
import cats.instances.future._
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.ServiceDependenciesConnector
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.cataloguefrontend.model.{SlugInfoFlag, VersionRange}
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.BobbyRulesTrendPage

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BobbyRulesTrendController @Inject() (
  override val mcc : MessagesControllerComponents,
  configConnector  : ServiceConfigsConnector,
  serviceDeps      : ServiceDependenciesConnector,
  page             : BobbyRulesTrendPage,
  override val auth: FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  val landing: Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for
        allRules <- configConnector.bobbyRules().map(_.libraries)
      yield Ok(
        page(
          form.fill(SearchForm(rules = Seq.empty, from = LocalDate.now().minusYears(2) , to = LocalDate.now())),
          allRules,
          flags = SlugInfoFlag.values,
          data  = None
        )
      )
    }

  def display(
    rules: Seq[String],
    from : LocalDate,
    to   : LocalDate,
  ): Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      for
        allRules <- configConnector.bobbyRules()
                      .map(_.libraries)
                      .map(_.sortBy(-_.from.toEpochDay))
        pageWithError = (msg: String) =>
                          page(
                            form.bindFromRequest().withGlobalError(msg),
                            allRules,
                            flags = SlugInfoFlag.values,
                            data = None
                          )
        res <- form
                 .bindFromRequest()
                 .fold(
                   hasErrors = formWithErrors => Future.successful(BadRequest(page(formWithErrors, allRules, flags = SlugInfoFlag.values, data = None))),
                   success   = query =>
                     (for
                        violations <- EitherT.right[Result](serviceDeps.getHistoricBobbyRuleViolations(query.rules.toList, query.from, query.to))
                        countData = violations.summary
                      yield
                        Ok(page(
                          form.bindFromRequest(),
                          allRules,
                          flags = SlugInfoFlag.values,
                          Some(
                            countData
                              .groupBy { case ((_, e), _) => e }
                              .view
                              .mapValues : cd =>
                                BobbyRulesTrendController.GraphData(
                                  columns = List(("string", "Date"))
                                              ++ List(("number", "Total"))
                                              ++ cd.map(_._1).map { case (r, _) => s"${r.group}:${r.artefact}:${r.range.range}" }.toList.map(("number", _)),
                                  rows    = cd
                                              .map(_._2.toList)
                                              .toList
                                              .transpose
                                              .map(x => List(x.sum) ++ x)
                                              .zipWithIndex
                                              .map { case (x, i) => List("\"" + violations.date.plusDays(i).format(DateTimeFormatter.ofPattern("dd MMM")) + "\"") ++ x }
                                )
                              .toMap
                          )
                        ))
                     ).merge
                 )
      yield res
    }

  case class SearchForm(
    rules: Seq[String],
    from : LocalDate,
    to   : LocalDate
  )

  val form =
    import uk.gov.hmrc.cataloguefrontend.util.FormUtils._
    import play.api.data._
    import play.api.data.Forms._
    Form(
      Forms.mapping(
        "rules" -> Forms.seq(Forms.text).verifying(notEmptySeq),
        "from"  -> default(Forms.localDate, LocalDate.now().minusYears(2)),
        "to"    -> default(Forms.localDate, LocalDate.now())
      )(SearchForm.apply)(sf => Some(Tuple.fromProductTyped(sf)))
    )
}

object BobbyRulesTrendController:
  def display(group: String, artefact: String, versionRange: VersionRange): String =
    uk.gov.hmrc.cataloguefrontend.routes.BobbyRulesTrendController.display(
      `rules[]` = Seq(s"$group:$artefact:${versionRange.range}")
    ).toString

  case class GraphData(
    columns: List[(String, String)],
    rows   : List[List[Any]]
  )
