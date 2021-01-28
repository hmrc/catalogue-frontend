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

package uk.gov.hmrc.cataloguefrontend.service

import java.net.{URI, URISyntaxException}

import javax.inject._
import uk.gov.hmrc.cataloguefrontend.connector.SearchByUrlConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SearchByUrlService @Inject()(
  searchByUrlConnector: SearchByUrlConnector
)(implicit val ec: ExecutionContext) {

  import SearchByUrlService._

  def search(term: Option[String], environment: String = "production")(implicit hc: HeaderCarrier): Future[Seq[FrontendRoutes]] =
    if (isValidSearchTerm(term)) {
      searchByUrlConnector
        .search(takeUrlPath(term.get))
        .map(results => results.filter(frontendRoute => frontendRoute.environment == environment))
    }
    else {
      Future.successful(Nil)
    }

  private def isValidSearchTerm(term: Option[String]): Boolean = {
    if (term.isEmpty || term.getOrElse("").trim.isEmpty || term.getOrElse("").trim == "/")
      return false

    try {
      val url = new URI(term.get)

      Option(url.getPath).getOrElse("").nonEmpty &&
        (!Option(url.getPath).getOrElse("").contains("tax.service.gov.uk") ||
          Option(url.getHost).getOrElse("").isEmpty &&
            Option(url.getPath).getOrElse("").contains("tax.service.gov.uk") &&
            url.getPath.substring(url.getPath.indexOf(".gov.uk") + 7).trim.nonEmpty)
    } catch {
      case e: URISyntaxException => false
    }
  }

  private def takeUrlPath(term: String): String = {
    val url = new URI(term)

    if (Option(url.getHost).getOrElse("").trim.nonEmpty)
      return url.getPath.trim

    if (Option(url.getHost).getOrElse("").trim.isEmpty &&
      Option(url.getPath).getOrElse("").contains("tax.service.gov.uk"))
      return url.getPath.substring(url.getPath.indexOf(".gov.uk") + 7).trim

    url.getPath.trim
  }
}

object SearchByUrlService {
  case class FrontendRoute(frontendPath: String, backendPath :String, ruleConfigurationUrl: String = "", isRegex: Boolean = false)
  case class FrontendRoutes(service: String, environment: String, routes: Seq[FrontendRoute])
}
