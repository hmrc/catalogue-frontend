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

package uk.gov.hmrc.cataloguefrontend.vulnerabilities

import play.api.libs.json.Reads
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VulnerabilitiesConnector @Inject() (
   httpClientV2  : HttpClientV2,
   servicesConfig: ServicesConfig
 )(implicit val ec: ExecutionContext) {
  import uk.gov.hmrc.http.HttpReads.Implicits._

  private val url: String = servicesConfig.baseUrl("vulnerabilities")

  def vulnerabilities()(implicit hc: HeaderCarrier): Future[Seq[Vulnerability]] = {
    implicit val vrs: Reads[Vulnerability] = Vulnerability.reads
    httpClientV2
      .get(url"$url/vulnerabilities/api/vulnerabilities")
      .execute[Seq[Vulnerability]]
  }

  def vulnerabilitySummaries(vulnerability: Option[String] = None, curationStatus: Option[String] = None, service: Option[String] = None, team: Option[String] = None)(implicit hc: HeaderCarrier): Future[Seq[VulnerabilitySummary]] = {
    implicit val vsrs: Reads[VulnerabilitySummary] = VulnerabilitySummary.apiFormat
    httpClientV2
      .get(url"$url/vulnerabilities/api/vulnerabilities/distinct?vulnerability=${vulnerability}&curationStatus=${curationStatus}&service=${service}&team=${team}")
      .execute[Seq[VulnerabilitySummary]]
  }

  def distinctVulnerabilities(service: String)(implicit hc: HeaderCarrier): Future[Option[Int]] = {
    httpClientV2
      .get(url"$url/vulnerabilities/api/vulnerabilities/count?service=${service}")
      .execute[Option[Int]]
  }
}
