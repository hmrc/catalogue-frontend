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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.model.{ServiceName, SlugInfoFlag}
import uk.gov.hmrc.cataloguefrontend.vulnerabilities.CurationStatus.ActionRequired
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{Instant, LocalDate}
import scala.concurrent.ExecutionContext.Implicits.global

class VulnerabilitiesConnectorSpec
  extends AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with HttpClientV2Support
    with WireMockSupport {

  val servicesConfig = ServicesConfig(
    Configuration(
      "microservice.services.vulnerabilities.host" -> wireMockHost,
      "microservice.services.vulnerabilities.port" -> wireMockPort
    )
  )

  val vulnerabilitiesConnector = VulnerabilitiesConnector(httpClientV2, servicesConfig)
  given HeaderCarrier = HeaderCarrier()

  "timelineCounts" should {
    "return a sequence of VulnerabilitiesTimelineCounts" in {
      stubFor(
        get(urlMatching("/vulnerabilities/api/reports/timeline\\?from=2023-01-16T00:00:00Z&to=2023-01-30T23:59:59Z"))
          .willReturn(aResponse().withBody(
            """[
              |{"weekBeginning": "2023-01-16T00:00:00.00Z", "count": 5},
              |{"weekBeginning": "2023-01-23T00:00:00.00Z", "count": 8},
              |{"weekBeginning": "2023-01-30T00:00:00.00Z", "count": 2}
              |]""".stripMargin))
      )

      vulnerabilitiesConnector.timelineCounts(None, None, None, None, LocalDate.parse("2023-01-16"), LocalDate.parse("2023-01-30")).futureValue shouldBe
        Seq(
          VulnerabilitiesTimelineCount(weekBeginning = Instant.parse("2023-01-16T00:00:00.00Z"), 5),
          VulnerabilitiesTimelineCount(weekBeginning = Instant.parse("2023-01-23T00:00:00.00Z"), 8),
          VulnerabilitiesTimelineCount(weekBeginning = Instant.parse("2023-01-30T00:00:00.00Z"), 2),
        )
    }
  }

  "vulnerabilitySummaries" should {
    "return a sequence of vulnerabilitySummaries" in {
      stubFor(
        get(urlMatching("/vulnerabilities/api/summaries"))
          .willReturn(aResponse().withBody(
            """[{
              "distinctVulnerability": {
                "vulnerableComponentName": "deb://ubuntu/xenial:test",
                "vulnerableComponentVersion": "1.2.5.4-1",
                "vulnerableComponents": [{"component": "deb://ubuntu/xenial:test", "version": "1.2.5.4-1"}],
                "id": "CVE-123",
                "score": 10,
                "description": "testing",
                "fixedVersions": ["2.5.2"],
                "references": ["http://test.com"],
                "publishedDate": "2019-08-20T10:53:37.00Z",
                "firstDetected": "2019-08-20T10:53:37.00Z",
                "assessment": "Serious",
                "curationStatus": "ACTION_REQUIRED",
                "ticket": "ticket1"
              },
              "occurrences": [{"service": "service1", "serviceVersion": "2.0", "componentPathInSlug": "some/test/path"}],
              "teams": ["TeamA", "TeamB"]
            }]""".stripMargin
          ))
      )

      vulnerabilitiesConnector.vulnerabilitySummaries(None, None, None, None, None).futureValue shouldBe Some(Seq(
        VulnerabilitySummary(
          distinctVulnerability = DistinctVulnerability(
                                    vulnerableComponentName    = "deb://ubuntu/xenial:test",
                                    vulnerableComponentVersion = "1.2.5.4-1",
                                    vulnerableComponents       = Seq(VulnerableComponent("deb://ubuntu/xenial:test", "1.2.5.4-1")),
                                    id                         = "CVE-123",
                                    score                      = Some(10.0),
                                    description                = "testing",
                                    fixedVersions              = Some(Seq("2.5.2")),
                                    references                 = Seq("http://test.com"),
                                    publishedDate              = Instant.parse("2019-08-20T10:53:37.00Z"),
                                    firstDetected              = Some(Instant.parse("2019-08-20T10:53:37.00Z")),
                                    assessment                 = Some("Serious"),
                                    curationStatus             = Some(ActionRequired),
                                    ticket                     = Some("ticket1")
                                  ),
          occurrences           = Seq(VulnerabilityOccurrence(
                                    service             = ServiceName("service1"),
                                    serviceVersion      = "2.0",
                                    componentPathInSlug = "some/test/path"
                                  )),
          teams                 = Seq("TeamA", "TeamB")
        )
      ))
    }
  }

  "deployedVulnerabilityCount" should {
    "return a count of distinct vulnerabilities for the given service" in {
      stubFor(
        get(urlMatching("/vulnerabilities/api/services/service1/deployed-report-count"))
          .willReturn(aResponse().withBody(
            """{
              "service": "service1",
              "actionRequired": 5,
              "noActionRequired": 4,
              "investigationOngoing": 3,
              "uncurated": 2
            }"""
          ))
      )

      vulnerabilitiesConnector.deployedVulnerabilityCount(ServiceName("service1")).futureValue shouldBe Some(
        TotalVulnerabilityCount(
          service              = ServiceName("service1"),
          actionRequired       = 5,
          noActionRequired     = 4,
          investigationOngoing = 3,
          uncurated            = 2
        )
      )
    }
  }

  "vulnerabilitiesCounts" should {
    "return a sequence of TotalVulnerabilityCounts" in {
      stubFor(
        get(urlMatching("/vulnerabilities/api/reports/latest/counts"))
          .willReturn(aResponse().withBody(
            """[{
              "service": "service1",
              "actionRequired": 5,
              "noActionRequired": 4,
              "investigationOngoing": 3,
              "uncurated": 2
            }]"""
          ))
      )

      vulnerabilitiesConnector.vulnerabilityCounts(SlugInfoFlag.Latest, None, None).futureValue shouldBe Seq(
        TotalVulnerabilityCount(
          service              = ServiceName("service1"),
          actionRequired       = 5,
          noActionRequired     = 4,
          investigationOngoing = 3,
          uncurated            = 2
        )
      )
    }
  }
}
