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

import javax.inject.{Inject, Singleton}
import play.api.data.{Form, Forms}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.filters.csrf.CSRF
import uk.gov.hmrc.cataloguefrontend.connector.RepoType
import uk.gov.hmrc.cataloguefrontend.service.DependenciesService
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.SearchByDependencyPage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class SearchByDependencyController @Inject()(
    mcc    : MessagesControllerComponents,
    service: DependenciesService,
    page   : SearchByDependencyPage)
  extends FrontendController(mcc) {

  def landing: Action[AnyContent] =
    Action.async { implicit request =>
      Future.successful(Ok(page(form, None)))
    }


  def search =
    Action.async { implicit request =>
      form
        .bindFromRequest()
        .fold(
          hasErrors = formWithErrors => Future.successful(Ok(page(formWithErrors, None))),
          success   = query => {
            service
              .getServicesWithDependency(query.group, query.artefact, query.versionOp, query.version)
              .map {
                case Left(err) => BadRequest(page(form.bindFromRequest().withGlobalError(err), None))
                case Right(results) => Ok(page(form.bindFromRequest(), Some(results)))
              }
          }
        )
    }

  case class SearchForm(
    group    : String,
    artefact : String,
    versionOp: String,
    version  : String)

  val form =
    Form(
      Forms.mapping(
        "group"     -> Forms.text,
        "artefact"  -> Forms.text,
        "versionOp" -> Forms.text,
        "version"   -> Forms.text
      )(SearchForm.apply)(SearchForm.unapply)
    )
}
