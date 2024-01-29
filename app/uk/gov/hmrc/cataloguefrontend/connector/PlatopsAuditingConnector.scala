/*
 * Copyright 2024 HM Revenue & Customs
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
import play.api.libs.json.Reads
import uk.gov.hmrc.cataloguefrontend.connector.model.UserLog
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlatopsAuditingConnector @Inject()(
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
)(implicit
  ec: ExecutionContext
) extends Logging {

  import HttpReads.Implicits._

  private val baseUrl = servicesConfig.baseUrl("platops-auditing")

  def userLogs(userName: String)(implicit hc: HeaderCarrier): Future[Option[UserLog]] = {
    val url: URL = url"$baseUrl/getUserLog?userName=${userName}"

    implicit val lr: Reads[UserLog] = UserLog.userLogFormat

    httpClientV2
      .get(url)
      .execute[Option[UserLog]]
  }
}
