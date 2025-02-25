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

package uk.gov.hmrc.cataloguefrontend.bobby

import cats.data.EitherT
import cats.instances.future._
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, MessagesRequest, Result}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.bobby.view.html.BobbyRulesTrendPage
import uk.gov.hmrc.cataloguefrontend.connector.ServiceDependenciesConnector
import uk.gov.hmrc.cataloguefrontend.model.{SlugInfoFlag, VersionRange}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

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
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      for
        allRules <- configConnector.bobbyRules().map(_.libraries)
      yield Ok(
        page(
          form.fill(SearchForm(rules = Seq.empty, from = LocalDate.now().minusYears(2) , to = LocalDate.now())),
          allRules,
          flags = SlugInfoFlag.values.toSeq,
          data  = None
        )
      )

  /**
    * @param rules for reverse routing
    * @param from for reverse routing
    * @param to for reverse routing
    */
  def display(
    rules: Seq[String],
    from : LocalDate,
    to   : LocalDate,
  ): Action[AnyContent] =
    BasicAuthAction.async: request =>
      given MessagesRequest[AnyContent] = request
      for
        allRules <- configConnector.bobbyRules()
                      .map(_.libraries)
                      .map(_.sortBy(-_.from.toEpochDay))
        flags         = SlugInfoFlag.values.toSeq
        pageWithError = (msg: String) =>
                          page(
                            form.bindFromRequest().withGlobalError(msg),
                            allRules,
                            flags,
                            data = None
                          )
        res <- form
                 .bindFromRequest()
                 .fold(
                   hasErrors = formWithErrors => Future.successful(BadRequest(page(formWithErrors, allRules, flags, data = None))),
                   success   = query =>
                     (for
                        violations <- EitherT.right[Result](serviceDeps.getHistoricBobbyRuleViolations(query.rules.toList, query.from, query.to))
                        countData = violations.summary
                      yield
                        Ok(page(
                          form.bindFromRequest(),
                          allRules,
                          flags,
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

  case class SearchForm(
    rules: Seq[String],
    from : LocalDate,
    to   : LocalDate
  )

  val form =
    import uk.gov.hmrc.cataloguefrontend.util.FormUtils.notEmptySeq
    import play.api.data.{Form, Forms}
    Form(
      Forms.mapping(
        "rules" -> Forms.seq(Forms.text).verifying(notEmptySeq),
        "from"  -> Forms.default(Forms.localDate, LocalDate.now().minusYears(2)),
        "to"    -> Forms.default(Forms.localDate, LocalDate.now())
      )(SearchForm.apply)(sf => Some(Tuple.fromProductTyped(sf)))
    )
}

object BobbyRulesTrendController:
  def display(group: String, artefact: String, versionRange: VersionRange): String =
    uk.gov.hmrc.cataloguefrontend.bobby.routes.BobbyRulesTrendController.display(
      `rules[]` = Seq(s"$group:$artefact:${versionRange.range}")
    ).toString

  case class GraphData(
    columns: List[(String, String)],
    rows   : List[List[Any]]
  )
