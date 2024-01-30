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

package uk.gov.hmrc.cataloguefrontend.search

import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.cataloguefrontend.config.SearchConfig
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.search.SearchResults
import uk.gov.hmrc.cataloguefrontend.connector.model.{Log, UserLog}

import javax.inject.{Inject, Singleton}

@Singleton
class SearchController @Inject()(
  searchIndex: SearchIndex,
  view       : SearchResults,
  config     : SearchConfig,
  cc         : MessagesControllerComponents
) extends FrontendController(cc){

  def search(query: String, limit: Int) = Action { request =>
    val searchTerms =
      query.trim.split("\\s+") // query is space delimited
        .take(5)               // cap number of searchable terms at 5
        .filter(_.length > 2)  // ignore search terms less than 3 chars
        .toIndexedSeq
    Ok(view(
        matches   = searchIndex.search(searchTerms).take(limit),
        highlight = if (config.highlight) new BoldHighlighter(searchTerms) else NoHighlighter
    ))
  }
  
  def searchByLogs(logs: Seq[Log]): Seq[SearchTerm] =
    searchIndex.searchByUserLogs(logs)
}
