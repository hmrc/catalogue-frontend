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

import uk.gov.hmrc.cataloguefrontend.vulnerabilities.v2.TeamVulnerabilitySummary
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, ServiceName, SlugInfoFlag, TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.util.DateHelper.{atEndOfDayInstant, atStartOfDayInstant}
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
    flag          : SlugInfoFlag
  , serviceName   : Option[ServiceName]    = None
  , team          : Option[TeamName]       = None
  , digitalService: Option[DigitalService] = None
  )(using
    HeaderCarrier
  ): Future[Seq[TotalVulnerabilityCount]] =
    given Reads[TotalVulnerabilityCount] = TotalVulnerabilityCount.reads
    httpClientV2
      .get(url"$url/vulnerabilities/api/reports/${flag.asString}/counts?service=${serviceName.map(s => s"\"${s.asString}\"")}&team=${team.map(_.asString)}&digitalService=${digitalService.map(_.asString)}")
      .execute[Seq[TotalVulnerabilityCount]]

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

  def deploymentOccurrences(
    serviceName: ServiceName,
    version    : Version
  )(using HeaderCarrier): Future[Option[DeploymentVulnerabilityReport]] =
    given Reads[DeploymentVulnerabilityReport] = DeploymentVulnerabilityReport.given_Reads_DeploymentVulnerabilityReport

    val endpoint = url"$url/vulnerabilities/api/v2/services/${serviceName.asString}/app-versions/${version.original}/occurrences"

    // TODO: Replace stub with the actual request when VULN004 is available:
    //
    // httpClientV2
    //   .get(endpoint)
    //   .execute[Option[DeploymentVulnerabilityReport]]

    val stubResponse = Json.parse(
      s"""
      {
        "serviceName": "${serviceName.asString}",
        "appVersion": "${version.original}",
        "scanStatus": "succeeded",
        "lastSuccessfulScan": "2026-09-11T10:00:00Z",
        "results": [
          {
            "occurrenceId": "OCC-1",
            "serviceName": "${serviceName.asString}",
            "appVersion": "${version.original}",
            "vulnerabilityId": "CVE-2026-0001",
            "summary": "Example dependency vulnerability",
            "cvss": 8.1,
            "kev": true,
            "epss": 0.14,
            "fixedVersions": ["2.4.1"],
            "component": "gav://uk.gov.hmrc:example-lib",
            "componentVersion": "2.4.0",
            "importedBy": [],
            "priority": "immediate",
            "sla": {
              "status": "running",
              "clockStartedAt": "2026-09-01T10:00:00Z",
              "deadline": "2026-09-15T10:00:00Z",
              "breached": true,
              "breachedAt": "2026-09-15T10:00:00Z",
              "nearBreach": true
            },
            "state": "actionable"
          },
          {
            "occurrenceId": "OCC-2",
            "serviceName": "${serviceName.asString}",
            "appVersion": "${version.original}",
            "vulnerabilityId": "CVE-2026-0002",
            "summary": "Another dependency vulnerability",
            "fixedVersions": [],
            "component": "gav://uk.gov.hmrc:another-lib",
            "componentVersion": "3.0.0",
            "importedBy": [],
            "priority": "expedite",
            "sla": {
              "status": "running",
              "deadline": "2026-10-15T10:00:00Z",
              "breached": false,
              "nearBreach": true
            },
            "state": "actionable"
          },
          {
            "occurrenceId": "OCC-3",
            "serviceName": "${serviceName.asString}",
            "appVersion": "${version.original}",
            "vulnerabilityId": "CVE-2026-0003",
            "summary": "A third dependency vulnerability",
            "fixedVersions": [],
            "component": "gav://org.apache.logging.log4j:log4j-core",
            "componentVersion": "3.0.0",
            "importedBy": [
              {
                "group": "uk.gov.hmrc",
                "artefact": "bootstrap-play",
                "version": "10.1.0"
              }
            ],
            "priority": "schedule",
            "sla": {
              "status": "running",
              "deadline": "2026-10-15T10:00:00Z",
              "breached": false,
              "nearBreach": true
            },
            "state": "riskAccepted"
          },
          {
            "occurrenceId": "OCC-4",
            "serviceName": "${serviceName.asString}",
            "appVersion": "${version.original}",
            "vulnerabilityId": "CVE-2026-0004",
            "summary": "A fourth dependency vulnerability",
            "fixedVersions": [],
            "component": "gav://uk.gov.hmrc:another-lib",
            "componentVersion": "3.0.0",
            "importedBy": [],
            "priority": "schedule",
            "sla": {
              "status": "running",
              "deadline": "2026-10-15T10:00:00Z",
              "breached": false,
              "nearBreach": true
            },
            "state": "noFixAvailable"
          }
        ],
        "page": {
          "limit": 50,
          "offset": 0,
          "total": 4
        }
      }
      """
    )

    Future.successful(Some(stubResponse.as[DeploymentVulnerabilityReport]))

  def v2TeamSummary(team: TeamName)
  (using HeaderCarrier) =
    given Reads[TeamVulnerabilitySummary] = TeamVulnerabilitySummary.format
    httpClientV2
      .get(url"$url/vulnerabilities/v2/team?team=${team.asString}")
      .execute[TeamVulnerabilitySummary]

end VulnerabilitiesConnector
