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

package uk.gov.hmrc.cataloguefrontend.service

import javax.inject._
import uk.gov.hmrc.cataloguefrontend.connector.SearchByUrlConnector
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.Future

@Singleton
class SearchByUrlService @Inject()(searchByUrlConnector: SearchByUrlConnector) {
  import SearchByUrlService._

  def search(term: Option[String])(implicit hc: HeaderCarrier): Future[SearchResults] =
    if(term.isDefined)
      Future.successful(Seq(ServiceUrl("catalogue-frontend", "/catalog", "http://github")))
      //searchByUrlConnector.search(term.get)
    else
      Future.successful(Nil)
}

object SearchByUrlService {
  type SearchResults = Seq[ServiceUrl]

  case class ServiceUrl(serviceName: String, frontendPath: String, ruleConfigurationUrl: String)
}
