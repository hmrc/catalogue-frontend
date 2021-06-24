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

import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.service.SearchByUrlService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.SearchByUrlPage

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SearchByUrlController @Inject() (
  mcc: MessagesControllerComponents,
  searchByUrlService: SearchByUrlService,
  searchByUrlPage: SearchByUrlPage
)(implicit val ec: ExecutionContext)
    extends FrontendController(mcc) {

  private val serviceNameToUrl = routes.CatalogueController.service _

  def searchLanding: Action[AnyContent] =
    Action.async { implicit request =>
      Future.successful(Ok(searchByUrlPage(UrlSearchFilter.form, Nil, serviceNameToUrl)))
    }

  def searchUrl =
    Action.async { implicit request =>
      UrlSearchFilter.form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(Ok(searchByUrlPage(formWithErrors, Nil, serviceNameToUrl))),
          query =>
            searchByUrlService
              .search(query.name)
              .map { results =>
                Ok(searchByUrlPage(UrlSearchFilter.form.bindFromRequest(), results, serviceNameToUrl))
              }
        )
    }

  case class UrlSearchFilter(name: Option[String] = None) {
    def isEmpty: Boolean = name.isEmpty
  }

  object UrlSearchFilter {
    lazy val form = Form(
      mapping(
        "name" -> optional(text).transform[Option[String]](x => if (x.exists(_.trim.isEmpty)) None else x, identity)
      )(UrlSearchFilter.apply)(UrlSearchFilter.unapply)
    )
  }
}
