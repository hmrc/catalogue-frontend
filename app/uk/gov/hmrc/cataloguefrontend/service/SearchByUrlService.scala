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

package uk.gov.hmrc.cataloguefrontend.service

import java.net.{URI, URISyntaxException}

import javax.inject._
import uk.gov.hmrc.cataloguefrontend.connector.SearchByUrlConnector
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SearchByUrlService @Inject() (
  searchByUrlConnector: SearchByUrlConnector
)(using ec: ExecutionContext):

  import SearchByUrlService._

  def search(
    term       : Option[String],
    environment: Environment   = Environment.Production
  )(using HeaderCarrier): Future[Seq[FrontendRoutes]] =
    if isValidSearchTerm(term)
    then
      searchByUrlConnector
        .search(takeUrlPath(term.get))
        .map(_.filter(_.environment == environment))
    else
      Future.successful(Nil)

  private def isValidSearchTerm(term: Option[String]): Boolean =
    if term.isEmpty || term.getOrElse("").trim.isEmpty || term.getOrElse("").trim == "/"
    then
      false
    else
      try {
        val url = URI(term.get)

        Option(url.getPath).getOrElse("").nonEmpty
          && ( !Option(url.getPath).getOrElse("").contains("tax.service.gov.uk")
               || Option(url.getHost).getOrElse("").isEmpty
               && Option(url.getPath).getOrElse("").contains("tax.service.gov.uk")
               && url.getPath.substring(url.getPath.indexOf(".gov.uk") + 7).trim.nonEmpty
             )
      } catch {
        case e: URISyntaxException => false
      }

  private def takeUrlPath(term: String): String =
    val url = URI(term)

    if Option(url.getHost).getOrElse("").trim.nonEmpty
    then
      url.getPath.trim
    else if Option(url.getHost).getOrElse("").trim.isEmpty
         && Option(url.getPath).getOrElse("").contains("tax.service.gov.uk")
    then
        url.getPath.substring(url.getPath.indexOf(".gov.uk") + 7).trim
    else
      url.getPath.trim

end SearchByUrlService

object SearchByUrlService:
  case class FrontendRoute(
    frontendPath        : String,
    ruleConfigurationUrl: String = "",
    isRegex             : Boolean = false
  )

  case class FrontendRoutes(
    service    : ServiceName,
    environment: Environment,
    routes     : Seq[FrontendRoute]
  )
