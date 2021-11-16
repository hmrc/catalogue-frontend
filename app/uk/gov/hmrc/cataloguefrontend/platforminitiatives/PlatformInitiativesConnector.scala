/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.platforminitiatives

import play.api.libs.json.OFormat
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PlatformInitiativesConnector @Inject()(
  httpClient: HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._

  private val platformInitiativesBaseUrl: String =
    servicesConfig.baseUrl("platform-initiatives")

  def allInitiatives(implicit hc: HeaderCarrier): Future[Seq[PlatformInitiative]] = {
    implicit val formatPI: OFormat[PlatformInitiative] = PlatformInitiative.format
    httpClient.GET[Seq[PlatformInitiative]](url"$platformInitiativesBaseUrl/platform-initiatives/initiatives")
  }
}
