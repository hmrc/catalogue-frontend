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

package uk.gov.hmrc.cataloguefrontend.connector

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cataloguefrontend.service.SearchByUrlService.{SearchResults, ServiceUrl}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class SearchByUrlConnector @Inject()(
                                 http: HttpClient,
                                 servicesConfig: ServicesConfig
                               ) {

  private val url: String = s"${servicesConfig.baseUrl("service-configs")}/frontend-route"

  implicit val serviceUrlReads: Reads[ServiceUrl] = Json.reads[ServiceUrl]

  def search(term: String)(implicit hc: HeaderCarrier): Future[SearchResults] =
    http
      .GET[SearchResults](s"$url", Seq("term" -> term))
      .recover {
        case NonFatal(ex) =>
          Logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty
      }
}
