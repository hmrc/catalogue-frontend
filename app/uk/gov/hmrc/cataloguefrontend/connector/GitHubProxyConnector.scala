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
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GitHubProxyConnector @Inject()(
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit
  ec: ExecutionContext
) extends Logging {

  import HttpReads.Implicits._

  private lazy val gitHubProxyBaseURL = servicesConfig.baseUrl("platops-github-proxy")

  def getGitHubProxyRaw(path: String)(implicit hc: HeaderCarrier): Future[Option[String]] = {
    val url = new URL(s"$gitHubProxyBaseURL/platops-github-proxy/github-raw$path")
    httpClientV2
      .get(url)
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
      .flatMap {
        case Right(res)                                      => Future.successful(Some(res.body))
        case Left(UpstreamErrorResponse.WithStatusCode(404)) => Future.successful(None)
        case Left(err)                                       => Future.failed(new RuntimeException(s"Call to $url failed with upstream error: ${err.message}"))
      }
  }
}
