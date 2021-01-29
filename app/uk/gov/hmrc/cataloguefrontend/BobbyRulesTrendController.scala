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

import java.time.format.DateTimeFormatter

import cats.data.EitherT
import cats.instances.future._
import javax.inject.{Inject, Singleton}
import play.api.data.{Form, Forms}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.cataloguefrontend.connector.model.BobbyVersionRange
import uk.gov.hmrc.cataloguefrontend.connector.{ConfigConnector, ServiceDependenciesConnector}
import uk.gov.hmrc.cataloguefrontend.model.SlugInfoFlag
import uk.gov.hmrc.cataloguefrontend.service.DependenciesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.BobbyRulesTrendPage

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BobbyRulesTrendController @Inject()(
    mcc            : MessagesControllerComponents
  , service        : DependenciesService
  , configConnector: ConfigConnector
  , serviceDeps    : ServiceDependenciesConnector
  , page           : BobbyRulesTrendPage
  )(implicit val ec: ExecutionContext
  ) extends FrontendController(mcc) {

  def landing: Action[AnyContent] =
    Action.async { implicit request =>
      for {
        allRules <- configConnector.bobbyRules.map(_.libraries)
      } yield
        Ok(page(
              form.fill(SearchForm(rules = Seq.empty))
            , allRules
            , flags = SlugInfoFlag.values
            , data  = None
            ))
    }

  def display: Action[AnyContent] =
    Action.async { implicit request =>
      for {
        allRules      <- configConnector.bobbyRules
                           .map(_.libraries)
                           .map(_.sortBy(- _.from.toEpochDay))
        pageWithError =  (msg: String) => page(
                                              form.bindFromRequest().withGlobalError(msg)
                                            , allRules
                                            , flags = SlugInfoFlag.values
                                            , data  = None
                                            )
        res <- form
                .bindFromRequest()
                .fold(
                    hasErrors = formWithErrors =>
                      Future.successful(BadRequest(page( formWithErrors
                                                       , allRules
                                                       , flags = SlugInfoFlag.values
                                                       , data  = None
                                                       )))
                  , success   = query =>
                      (for {
                        violations <- EitherT.right[Result](serviceDeps.getHistoricBobbyRuleViolations)
                        countData = violations
                                      .summary
                                      .filter { case ((r, _), _) => query.rules.contains(s"${r.group}:${r.artefact}:${r.range.range}") }
                      } yield Ok(page(
                            form.bindFromRequest()
                          , allRules
                          , flags = SlugInfoFlag.values
                          , Some(countData
                                   .groupBy { case ((_, e), _) => e }
                                   .mapValues(cd =>
                                      BobbyRulesTrendController.GraphData(
                                          columns = List(("string", "Date")) ++
                                                    List(("number", "Total")) ++
                                                    cd.map(_._1).map { case (r, _) => s"${r.group}:${r.artefact}:${r.range.range}"}.toList.map(("number", _))
                                        , rows    = cd.map(_._2.toList).toList.transpose
                                                      .map(x => List(x.sum) ++ x)
                                                      .zipWithIndex.map { case (x, i) => List("\"" + violations.date.plusDays(i).format(DateTimeFormatter.ofPattern("dd MMM")) + "\"") ++ x }

                                        )))
                          ))
                      ).merge
                )
      } yield res
    }

  case class SearchForm(
      rules: Seq[String]
    )

  def form() = {
    import uk.gov.hmrc.cataloguefrontend.util.FormUtils.notEmptySeq
    Form(
      Forms.mapping(
          "rules" -> Forms.seq(Forms.text).verifying(notEmptySeq)
        )(SearchForm.apply)(SearchForm.unapply)
    )
  }
}

object BobbyRulesTrendController {
  def display(group: String, artefact: String, versionRange: BobbyVersionRange): String =
    uk.gov.hmrc.cataloguefrontend.routes.BobbyRulesTrendController.display +
      s"?rules[]=$group:$artefact:${versionRange.range}"

  case class GraphData(
      columns: List[(String, String)]
    , rows   : List[List[Any]]
    )
}