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

import play.api.Logger
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class LeakDetectionConnector @Inject() (
  http: HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  private val url: String = servicesConfig.baseUrl("leak-detection")

  def repositoriesWithLeaks(implicit hc: HeaderCarrier): Future[Seq[RepositoryWithLeaks]] = {
    implicit val rwlr = RepositoryWithLeaks.reads
    http
      .GET[Seq[RepositoryWithLeaks]](
          url"$url/api/repository",
          headers = Seq("Accept" -> "application/json")
        )
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty
      }
  }

  def leakDetectionRuleSummaries(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionRuleSummary]] = {
    implicit val ldrs = LeakDetectionRuleSummary.reads
    http
      .GET[Seq[LeakDetectionRuleSummary]](
        url"$url/api/rules",
        headers = Seq("Accept" -> "application/json")
      )
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty
      }
  }
}

final case class RepositoryWithLeaks(name: String) extends AnyVal

object RepositoryWithLeaks {
  val reads: Reads[RepositoryWithLeaks] =
    implicitly[Reads[String]].map(RepositoryWithLeaks.apply)
}

final case class LeakDetectionRuleSummary(rule: LeakDetectionRule, leaks: Seq[LeakDetectionRepositorySummary])

object LeakDetectionRuleSummary {
  implicit val ldrr = LeakDetectionRule.reads
  implicit val ldrsr = LeakDetectionRepositorySummary.reads
  val reads: Reads[LeakDetectionRuleSummary] = Json.reads[LeakDetectionRuleSummary]
}

final case class LeakDetectionRule(
                       id: String,
                       scope: String,
                       regex: String,
                       description: String,
                       ignoredFiles: List[String],
                       ignoredExtensions: List[String],
                       priority: String
                     )

object LeakDetectionRule {
  implicit val reads = Json.reads[LeakDetectionRule]
}

case class LeakDetectionRepositorySummary(
                              repository: String,
                              firstScannedAt: Instant,
                              lastScannedAt: Instant,
                              unresolvedCount: Int
                            )

object LeakDetectionRepositorySummary {
  implicit val reads = Json.reads[LeakDetectionRepositorySummary]
}