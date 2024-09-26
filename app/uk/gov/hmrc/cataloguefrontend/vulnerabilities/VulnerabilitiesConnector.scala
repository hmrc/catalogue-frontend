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
import uk.gov.hmrc.cataloguefrontend.model.{ServiceName, SlugInfoFlag, TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.util.DateHelper.{atStartOfDayInstant, atEndOfDayInstant}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VulnerabilitiesConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using
  ExecutionContext
):
  import uk.gov.hmrc.http.HttpReads.Implicits._

  private val url: String = servicesConfig.baseUrl("vulnerabilities")

  def vulnerabilitySummaries(
    flag          : Option[SlugInfoFlag]   = None
  , serviceQuery  : Option[String]         = None
  , version       : Option[Version]        = None
  , team          : Option[TeamName]       = None
  , curationStatus: Option[CurationStatus] = None
  )(using
    HeaderCarrier
  ): Future[Option[Seq[VulnerabilitySummary]]] =
    given Reads[VulnerabilitySummary] = VulnerabilitySummary.reads
    httpClientV2
      .get(url"$url/vulnerabilities/api/summaries?flag=${flag.map(_.asString)}&service=$serviceQuery&version=${version.map(_.original)}&team=${team.map(_.asString)}&curationStatus=${curationStatus.map(_.asString)}")
      .execute[Option[Seq[VulnerabilitySummary]]]

  def vulnerabilityCounts(
    flag       : SlugInfoFlag
  , serviceName: Option[ServiceName] = None
  , team       : Option[TeamName]    = None
  )(using
    HeaderCarrier
  ): Future[Seq[TotalVulnerabilityCount]] =
    given Reads[TotalVulnerabilityCount] = TotalVulnerabilityCount.reads
    httpClientV2
      .get(url"$url/vulnerabilities/api/reports/${flag.asString}/counts?service=${serviceName.map(s => s"\"${s.asString}\"")}&team=${team.map(_.asString)}")
      .execute[Seq[TotalVulnerabilityCount]]

  def deployedVulnerabilityCount(
    serviceName: ServiceName
  )(using
    HeaderCarrier
  ): Future[Option[TotalVulnerabilityCount]] =
    given Reads[TotalVulnerabilityCount] = TotalVulnerabilityCount.reads
    httpClientV2
      .get(url"$url/vulnerabilities/api/services/${serviceName.asString}/deployed-report-count")
      .execute[Option[TotalVulnerabilityCount]]

  def timelineCounts(
    serviceName   : Option[ServiceName]
  , team          : Option[TeamName]
  , vulnerability : Option[String]
  , curationStatus: Option[CurationStatus]
  , from          : LocalDate
  , to            : LocalDate
  )(using
    HeaderCarrier
  ): Future[Seq[VulnerabilitiesTimelineCount]] =
    given Reads[VulnerabilitiesTimelineCount] = VulnerabilitiesTimelineCount.reads

    val fromInstant = from.atStartOfDayInstant
    val toInstant   = to.atEndOfDayInstant

    httpClientV2
      .get(url"$url/vulnerabilities/api/reports/timeline?service=${serviceName.map(s => s"\"${s.asString}\"")}&team=${team.map(_.asString)}&vulnerability=$vulnerability&curationStatus=${curationStatus.map(_.asString)}&from=$fromInstant&to=$toInstant")
      .execute[Seq[VulnerabilitiesTimelineCount]]

end VulnerabilitiesConnector
