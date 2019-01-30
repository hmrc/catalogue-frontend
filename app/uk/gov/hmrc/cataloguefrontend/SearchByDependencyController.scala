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
import cats.syntax.all._
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.data.{Form, Forms}
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import play.filters.csrf.CSRF
import uk.gov.hmrc.cataloguefrontend.connector.RepoType
import uk.gov.hmrc.cataloguefrontend.service.DependenciesService
import uk.gov.hmrc.cataloguefrontend.connector.model.{ServiceWithDependency, Version, VersionOp}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.SearchByDependencyPage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

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
      def pageWithError(msg: String) = page(form.bindFromRequest().withGlobalError(msg), None)
      form
        .bindFromRequest()
        .fold(
          hasErrors = formWithErrors => Future.successful(BadRequest(page(formWithErrors, None))),
          success   = query => {
            (for {
              versionOp <- EitherT.fromOption[Future](VersionOp.parse(query.versionOp), BadRequest(pageWithError("Invalid version op")))
              version   <- EitherT.fromOption[Future](Version.parse(query.version), BadRequest(pageWithError("Invalid version")))
              results   <- EitherT.right[Result] {
                             service
                              .getServicesWithDependency(query.group, query.artefact, versionOp, version)
                           }
             } yield Ok(page(form.bindFromRequest(), Some(results)))
            ).merge
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
