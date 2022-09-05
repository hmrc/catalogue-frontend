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

package uk.gov.hmrc.cataloguefrontend.prcommenter

import play.api.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PrCommenterConnector @Inject()(
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) extends Logging {
  import HttpReads.Implicits._

  private val baseUrl = servicesConfig.baseUrl("pr-commenter")

  private implicit val rf = PrCommenterReport.reads

  def report(name: String)(implicit hc: HeaderCarrier): Future[Option[PrCommenterReport]] =
    httpClientV2
      .get(url"$baseUrl/pr-commenter/repositories/$name/report")
      .execute[Option[PrCommenterReport]]

  def search(name: Option[String], teamName: Option[String], commentType: Option[String])(implicit hc: HeaderCarrier): Future[Seq[PrCommenterReport]] =
    httpClientV2
      .get(url"$baseUrl/pr-commenter/reports?name=$name&teamName=$teamName&commentType=$commentType")
      .execute[Seq[PrCommenterReport]]
}
