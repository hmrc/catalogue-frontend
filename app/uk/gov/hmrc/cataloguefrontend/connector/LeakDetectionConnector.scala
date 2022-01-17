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

  def ruleViolations(implicit hc: HeaderCarrier): Future[Seq[LeakDetectionRuleViolations]] = {
    implicit val ldrs = LeakDetectionRuleViolations.reads
    http
      .GET[Seq[LeakDetectionRuleViolations]](
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

final case class LeakDetectionRuleViolations(rule: Rule, violations: Seq[Violation])

object LeakDetectionRuleViolations {
  implicit val rr = Rule.reads
  implicit val vsr = Violation.reads
  val reads: Reads[LeakDetectionRuleViolations] = Json.reads[LeakDetectionRuleViolations]
}

final case class Rule(
                       id: String,
                       scope: String,
                       regex: String,
                       description: String,
                       ignoredFiles: List[String],
                       ignoredExtensions: List[String],
                       priority: String
                     )

object Rule {
  implicit val reads = Json.reads[Rule]
}

case class Violation(
                              repository: String,
                              scannedAt: Instant,
                              unresolvedCount: Int
                            )

object Violation {
  implicit val reads = Json.reads[Violation]
}