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

package uk.gov.hmrc.cataloguefrontend.connector

import cats.implicits._
import play.api.cache.AsyncCacheApi
import play.api.{Configuration, Logging}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2

import java.net.URL
import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object ConfluenceConnector:
  case class Blog(
    title      : String,
    url        : URL,
    createdDate: Instant
  )

@Singleton
class ConfluenceConnector @Inject()(
  config      : Configuration
, httpClientV2: HttpClientV2
, cache       : AsyncCacheApi
)(using
  ExecutionContext
) extends Logging:
  import HttpReads.Implicits._

  private val confluenceUrl =
    config.get[String]("confluence.url")

  private val authHeaderValue =
    import com.google.common.io.BaseEncoding
    val username = config.get[String]("confluence.username")
    val password = config.get[String]("confluence.password")
    s"Basic ${BaseEncoding.base64().encode(s"$username:$password".getBytes("UTF-8"))}"

  private val searchLimit =
    config.get[String]("confluence.search.limit")

  private val searchLabel =
    config.get[String]("confluence.search.label")

  given HeaderCarrier = HeaderCarrier()

  private val blogCacheExpiration =
    config.get[Duration]("confluence.blogCacheExpiration")

  def getBlogs(): Future[List[ConfluenceConnector.Blog]] =
    cache
      .getOrElseUpdate("blog-cache", blogCacheExpiration):
        for
          results <- search(cql = s"""(label="$searchLabel" and type=blogpost) order by created desc""")
          blog    <- results.foldLeftM[Future, List[ConfluenceConnector.Blog]](List.empty): (xs, result) =>
                       history(result.history).map: history =>
                         xs
                          :+ ConfluenceConnector.Blog(
                               title       = result.title
                             , url         = url"${confluenceUrl + result.tinyUi}"
                             , createdDate = history.createdDate
                             )
        yield blog
      .recover:
        case UpstreamErrorResponse.Upstream4xxResponse(ex) =>
          logger.warn(s"Could not get a list of Confluence Blogs: ${ex.getMessage}")
          List.empty

  case class Result(
    title  : String,
    tinyUi : String,
    history: String
  )

  private val readsResults: Reads[List[Result]] =
    given Reads[Result] =
      ( (__ \ "title"                  ).read[String]
      ~ (__ \ "_links"      \ "tinyui" ).read[String]
      ~ (__ \ "_expandable" \ "history").read[String]
      )(Result.apply)
    (__ \ "results").read[List[Result]]

  private def search(cql: String): Future[List[Result]] =
    given Reads[List[Result]] = readsResults
    httpClientV2
      .get(url"$confluenceUrl/rest/api/content/search?limit=$searchLimit&cql=$cql")
      .setHeader("Authorization" -> authHeaderValue)
      .execute[List[Result]]

  case class History(createdDate: Instant)

  private val readsHistory: Reads[History] =
    (__ \ "createdDate").read[Instant].map(History.apply)

  private def history(path: String): Future[History] =
    given Reads[History] = readsHistory
    httpClientV2
      .get(url"${confluenceUrl + path}")
      .setHeader("Authorization" -> authHeaderValue)
      .execute[History]

end ConfluenceConnector
