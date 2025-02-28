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

package uk.gov.hmrc.cataloguefrontend.platforminitiatives

import play.api.libs.json.Format
import uk.gov.hmrc.cataloguefrontend.model.{DigitalService, TeamName}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlatformInitiativesConnector @Inject()(
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using ExecutionContext):
  import HttpReads.Implicits._

  private val platformInitiativesBaseUrl: String =
    servicesConfig.baseUrl("platform-initiatives")

  private given Format[PlatformInitiative] = PlatformInitiative.format

  def getInitiatives(
    teamName      : Option[TeamName]       = None
  , digitalService: Option[DigitalService] = None
  )(using HeaderCarrier): Future[Seq[PlatformInitiative]] =
    httpClientV2
      .get(url"$platformInitiativesBaseUrl/platform-initiatives/initiatives?teamName=${teamName.map(_.asString)}&digitalService=${digitalService.map(_.asString)}")
      .execute[Seq[PlatformInitiative]]
