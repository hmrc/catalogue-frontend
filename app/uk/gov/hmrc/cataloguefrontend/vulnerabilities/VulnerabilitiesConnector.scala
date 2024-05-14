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

package uk.gov.hmrc.cataloguefrontend.vulnerabilities

import play.api.libs.json.Reads
import uk.gov.hmrc.cataloguefrontend.connector.model.Version
import uk.gov.hmrc.cataloguefrontend.model.SlugInfoFlag
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VulnerabilitiesConnector @Inject() (
   httpClientV2  : HttpClientV2,
   servicesConfig: ServicesConfig
 )(implicit val ec: ExecutionContext) {
  import uk.gov.hmrc.http.HttpReads.Implicits._

  private val url: String = servicesConfig.baseUrl("vulnerabilities")

  def vulnerabilitySummaries(
    flag          : Option[SlugInfoFlag]   = None
  , serviceName   : Option[String]         = None
  , version       : Option[Version]        = None
  , team          : Option[String]         = None
  , curationStatus: Option[CurationStatus] = None
  )(implicit
    hc            : HeaderCarrier
  ): Future[Option[Seq[VulnerabilitySummary]]] = {
    implicit val vsrs: Reads[VulnerabilitySummary] = VulnerabilitySummary.apiFormat
    httpClientV2
      .get(url"$url/vulnerabilities/api/summaries?flag=${flag.map(_.asString)}&service=$serviceName&version=${version.map(_.original)}&team=$team&curationStatus=${curationStatus.map(_.asString)}")
      .execute[Option[Seq[VulnerabilitySummary]]]
  }

  def vulnerabilityCounts(
    flag       : SlugInfoFlag
  , serviceName: Option[String] = None
  , team       : Option[String] = None
  )(implicit
    hc         : HeaderCarrier
  ): Future[Seq[TotalVulnerabilityCount]] = {
    implicit val vcrs: Reads[TotalVulnerabilityCount] = TotalVulnerabilityCount.reads
    httpClientV2
      .get(url"$url/vulnerabilities/api/reports/${flag.asString}/counts?service=$serviceName&team=$team")
      .execute[Seq[TotalVulnerabilityCount]]
  }

  def deployedVulnerabilityCount(
    serviceName: String
  )(implicit
    hc         : HeaderCarrier
  ): Future[Option[TotalVulnerabilityCount]] = {
    implicit val vcrs: Reads[TotalVulnerabilityCount] = TotalVulnerabilityCount.reads
    httpClientV2
      .get(url"$url/vulnerabilities/api/services/$serviceName/deployed-report-count")
      .execute[Option[TotalVulnerabilityCount]]
  }

  def timelineCounts(
    serviceName   : Option[String]
  , team          : Option[String]
  , vulnerability : Option[String]
  , curationStatus: Option[CurationStatus]
  , from          : LocalDate
  , to            : LocalDate
  )(implicit
    hc: HeaderCarrier
  ): Future[Seq[VulnerabilitiesTimelineCount]] = {
    implicit val stcr: Reads[VulnerabilitiesTimelineCount] = VulnerabilitiesTimelineCount.reads

    val fromInstant = DateTimeFormatter.ISO_INSTANT.format(from.atStartOfDay().toInstant(ZoneOffset.UTC))
    val toInstant   = DateTimeFormatter.ISO_INSTANT.format(to.atTime(23,59,59).toInstant(ZoneOffset.UTC))

    httpClientV2
      .get(url"$url/vulnerabilities/api/reports/timeline?service=$serviceName&team=$team&vulnerability=$vulnerability&curationStatus=${curationStatus.map(_.asString)}&from=$fromInstant&to=$toInstant")
      .execute[Seq[VulnerabilitiesTimelineCount]]
  }
}
