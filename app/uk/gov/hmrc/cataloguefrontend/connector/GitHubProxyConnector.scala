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

import play.api.Logging
import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.cataloguefrontend.model.Version

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class GitHubProxyConnector @Inject()(
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using
  ExecutionContext
) extends Logging:
  import HttpReads.Implicits._

  private lazy val gitHubProxyBaseURL = servicesConfig.baseUrl("platops-github-proxy")

  def getGitHubProxyRaw(path: String)(using HeaderCarrier): Future[Option[String]] =
    val url = URL(s"$gitHubProxyBaseURL/platops-github-proxy/github-raw$path")
    httpClientV2
      .get(url)
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
      .flatMap:
        case Right(res)                                      => Future.successful(Some(res.body))
        case Left(UpstreamErrorResponse.WithStatusCode(404)) => Future.successful(None)
        case Left(err)                                       => Future.failed(RuntimeException(s"Call to $url failed with upstream error: ${err.message}"))

  def compare(repoName: String, v1: Version, v2: Version)(using HeaderCarrier): Future[Option[GitHubProxyConnector.Compare]] =
    given Reads[GitHubProxyConnector.Compare] = GitHubProxyConnector.Compare.reads
    // API doesn't list removed commits
    val diff = if   v1 <= v2
               then s"v${v1.original}...v${v2.original}"
               else s"v${v2.original}...v${v1.original}"

    val url = URL(s"$gitHubProxyBaseURL/platops-github-proxy/github-rest/$repoName/compare/$diff")
    httpClientV2
      .get(url)
      .execute[Either[UpstreamErrorResponse, GitHubProxyConnector.Compare]]
      .flatMap:
        case Right(res) if v1 > v2                           => Future.successful(Some(res.copy(behindBy = res.aheadBy, aheadBy = 0)))
        case Right(res)                                      => Future.successful(Some(res))
        case Left(UpstreamErrorResponse.WithStatusCode(404)) => Future.successful(None)
        case Left(err)                                       => Future.failed(RuntimeException(s"Call to $url failed with upstream error: ${err.message}"))

object GitHubProxyConnector:
  case class Compare(
    aheadBy     : Int
  , behindBy    : Int
  , totalCommits: Int
  , commits     : List[Commit]
  , htmlUrl     : String
  )

  case class Commit(
    author : String
  , date   : Instant
  , message: String
  , htmlUrl: String
  )

  object Compare:
    val reads: Reads[Compare] =
      given Reads[Commit] =
        ( (__ \ "commit"  \ "author"    \ "name").read[String]
        ~ (__ \ "commit"  \ "committer" \ "date").read[Instant]
        ~ (__ \ "commit"  \ "message"           ).read[String]
        ~ (__ \ "html_url"                      ).read[String]
        )(Commit.apply)

      ( (__ \ "ahead_by"     ).read[Int]
      ~ (__ \ "behind_by"    ).read[Int]
      ~ (__ \ "total_commits").read[Int]
      ~ (__ \ "commits"      ).read[List[Commit]].map(_.take(20))
      ~ (__ \ "html_url"     ).read[String]
      )(Compare.apply)
