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

import javax.inject.{Inject, Singleton}
import play.api.data.{Form, Forms}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.service.SearchByUrlService
import uk.gov.hmrc.cataloguefrontend.view.html.SearchByUrlPage
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SearchByUrlController @Inject() (
  override val mcc  : MessagesControllerComponents,
  searchByUrlService: SearchByUrlService,
  searchByUrlPage   : SearchByUrlPage,
  override val auth : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  def searchLanding: Action[AnyContent] =
    BasicAuthAction.async { implicit request =>
      Future.successful(Ok(searchByUrlPage(UrlSearchFilter.form, Nil)))
    }

  def searchUrl =
    BasicAuthAction.async { implicit request =>
      UrlSearchFilter.form
        .bindFromRequest()
        .fold(
          formWithErrors => Future.successful(Ok(searchByUrlPage(formWithErrors, Nil))),
          query          => searchByUrlService
                              .searchFrontendPath(query.name)
                              .map: results =>
                                Ok(searchByUrlPage(UrlSearchFilter.form.bindFromRequest(), results))
        )
    }

  case class UrlSearchFilter(name: Option[String] = None):
    def isEmpty: Boolean =
      name.isEmpty

  object UrlSearchFilter:
    lazy val form = Form(
      Forms.mapping(
        "name" -> Forms.optional(Forms.text).transform[Option[String]](x => if x.exists(_.trim.isEmpty) then None else x, identity)
      )(UrlSearchFilter.apply)(f => Some(f.name))
    )

end SearchByUrlController
