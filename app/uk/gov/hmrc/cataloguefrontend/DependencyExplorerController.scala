/*
 * Copyright 2019 HM Revenue & Customs
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
import javax.inject.{Inject, Singleton}
import play.api.data.{Form, Forms}
import play.api.i18n.{Messages, MessagesProvider}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.cataloguefrontend.service.DependenciesService
import uk.gov.hmrc.cataloguefrontend.connector.model.{Version, VersionOp}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.DependencyExplorerPage

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DependencyExplorerController @Inject()(
    mcc    : MessagesControllerComponents,
    service: DependenciesService,
    page   : DependencyExplorerPage)
  extends FrontendController(mcc) {


  import ExecutionContext.Implicits.global

  def landing: Action[AnyContent] =
    Action.async { implicit request =>
      service.getGroupArtefacts.map { groupArtefacts =>
        Ok(page(form, groupArtefacts, searchResults = None, pieData = None))
      }
    }


  def search =
    Action.async { implicit request =>
      service.getGroupArtefacts.flatMap { groupArtefacts =>
        def pageWithError(msg: String) = page(form.bindFromRequest().withGlobalError(msg), groupArtefacts, searchResults = None, pieData = None)
        form
          .bindFromRequest()
          .fold(
            hasErrors = formWithErrors => Future.successful(BadRequest(page(formWithErrors, groupArtefacts, searchResults = None, pieData = None))),
            success   = query => {
              (for {
                versionOp <- EitherT.fromOption[Future](VersionOp.parse(query.versionOp), BadRequest(pageWithError("Invalid version op")))
                version   <- EitherT.fromOption[Future](Version.parse(query.version), BadRequest(pageWithError("Invalid version")))
                results   <- EitherT.right[Result] {
                              service
                                .getServicesWithDependency(query.group, query.artefact, versionOp, version)
                             }
                pieData   =  DependencyExplorerController.PieData(
                               "Version spread",
                               results
                                 .groupBy(r => s"${r.depGroup}:${r.depArtefact}:${r.depVersion}")
                                 .map(r => r._1 -> r._2.size))
              } yield Ok(page(form.bindFromRequest(), groupArtefacts, Some(results), Some(pieData)))
              ).merge
            }
          )
      }
    }

  case class SearchForm(
    group    : String,
    artefact : String,
    versionOp: String,
    version  : String)

  def notEmptyOr(s: String) = {
    import play.api.data.validation._
    Constraint[String]("") { o =>
      if (o == null || o.trim.isEmpty || o == s) Invalid(ValidationError("error.required")) else Valid
    }
  }

  def form(implicit messagesProvider: MessagesProvider) =
    Form(
      Forms.mapping(
        "group"     -> Forms.text.verifying(notEmptyOr(Messages("dependencyexplorer.select.group"))),
        "artefact"  -> Forms.text.verifying(notEmptyOr(Messages("dependencyexplorer.select.artefact"))),
        "versionOp" -> Forms.text,
        "version"   -> Forms.text
      )(SearchForm.apply)(SearchForm.unapply)
    )
}

object DependencyExplorerController {
  case class PieData(
    title  : String,
    results: Map[String, Int])
}