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

package uk.gov.hmrc.cataloguefrontend.connector

import play.api.cache.AsyncCacheApi
import play.api.{Configuration, Logging}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._

import cats.implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}

import java.net.URL
import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration

object ConfluenceConnector {
  case class Blog(title: String, url: URL, createdDate: Instant)
}

@Singleton
class ConfluenceConnector @Inject()(
  config      : Configuration
, httpClientV2: HttpClientV2
, cache       : AsyncCacheApi
)(implicit
  ec : ExecutionContext
) extends Logging {
  import uk.gov.hmrc.http.HttpReads.Implicits._

  private val confluenceUrl   = config.get[String]("confluence.url")
  private val authHeaderValue = {
    import com.google.common.io.BaseEncoding
    val username = config.get[String]("confluence.username")
    val password = config.get[String]("confluence.password")
    s"Basic ${BaseEncoding.base64().encode(s"$username:$password".getBytes("UTF-8"))}"
  }
  private val searchLimit = config.get[String]("confluence.search.limit")
  private val searchLabel = config.get[String]("confluence.search.label")

  implicit private val hc = HeaderCarrier()

  private val blogCacheExpiration = config.get[Duration]("confluence.blogCacheExpiration")
  def getBlogs(): Future[List[ConfluenceConnector.Blog]] =
    cache.getOrElseUpdate("bobby-rules", blogCacheExpiration) {
      ( for {
          auth   <- authenticate()
          results <- search(auth, cql = s"""(label="$searchLabel" and type=blogpost) order by created desc""")
          blog    <- results.foldLeftM[Future, List[ConfluenceConnector.Blog]](List.empty){
                      case (xs, result) =>
                        history(auth, result.history).map { history =>
                          xs :+ ConfluenceConnector.Blog(
                            title       = result.title
                          , url         = url"${confluenceUrl + result.tinyUi}"
                          , createdDate = history.createdDate
                          )
                        }
                     }
        } yield blog
      ).recover { case UpstreamErrorResponse.Upstream4xxResponse(_) => List.empty }
    }

  case class Auth(token: String)

  private val readsAuth: Reads[Auth] =
    (__ \ "rawToken").read[String].map(Auth)

  private def authenticate(): Future[Auth] = {
    implicit val rd = readsAuth
    httpClientV2
      .post(url"$confluenceUrl/rest/pat/latest/tokens")
      .setHeader("Authorization" -> authHeaderValue)
      .withBody(Json.obj("name" -> JsString("catalogueToken"), "expirationDuration" -> JsNumber(2)))
      .withProxy
      .execute[Auth]
  }

  case class Result(title: String, tinyUi: String, history: String)

  private val readsResults: Reads[List[Result]] = {
    implicit val readsResult: Reads[Result] =
    ( (__ \ "title"                  ).read[String]
    ~ (__ \ "_links" \ "tinyui"      ).read[String]
    ~ (__ \ "_expandable" \ "history").read[String]
    )(Result.apply _)

    (__ \ "results").read[List[Result]]
  }

  private def search(auth: Auth, cql: String): Future[List[Result]] = {
    implicit val rd = readsResults
    httpClientV2
      .get(url"$confluenceUrl/rest/api/content/search?limit=$searchLimit&cql=$cql")
      .setHeader("Authorization" -> s"Bearer ${auth.token}")
      .withProxy
      .execute[List[Result]]
  }

  case class History(createdDate: Instant)

  private val readsHistory: Reads[History] =
    (__ \ "createdDate").read[Instant].map(History)

  private def history(auth: Auth, path: String): Future[History] = {
    implicit val rd = readsHistory
    httpClientV2
      .get(url"${confluenceUrl + path}")
      .setHeader("Authorization" -> s"Bearer ${auth.token}")
      .withProxy
      .execute[History]
  }
}
