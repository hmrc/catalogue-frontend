/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.search

import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.search._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SearchController @Inject()(indexBuilder: IndexBuilder, view: SearchResults, cc: MessagesControllerComponents)(implicit ec: ExecutionContext)
  extends FrontendController(cc){

  def search(query: String, limit: Int) =  Action.async { request =>
    for {
      index         <- Future.successful(indexBuilder.getIndex())
      searchMatches  = IndexBuilder.search(query, index)
    } yield Ok(view(searchMatches.take(limit)))
  }

}
