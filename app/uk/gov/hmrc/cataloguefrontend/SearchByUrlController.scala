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
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.{SearchByUrlPage, error_404_template}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.cataloguefrontend.service.SearchByUrlService

@Singleton
class SearchByUrlController @Inject()(
  mcc: MessagesControllerComponents,
  searchByUrlService: SearchByUrlService,
  searchByUrlPage: SearchByUrlPage
) extends FrontendController(mcc) {

  def search(): Action[AnyContent] = Action.async { implicit request =>
    UrlSearchFilter.form
      .bindFromRequest()
      .fold(
        formWithErrors => Future.successful(Ok(searchByUrlPage(formWithErrors, Nil))),
        query => {
          for {
            searchResult <- searchByUrlService.search(query.name)
          } yield searchResult match {
            case Nil => NotFound(error_404_template())
            case _ =>
              Ok(searchByUrlPage(
                UrlSearchFilter.form.bindFromRequest(),
                searchResult))
          }
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
