/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.WhatsRunningWhere._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class ReleasesConnector @Inject()(http: HttpClient,
                                  servicesConfig: ServicesConfig)(implicit ec: ExecutionContext) {

  private val serviceUrl: String = servicesConfig.baseUrl("releases-api")

  def releases(profileName: Option[ProfileName])(implicit hc: HeaderCarrier): Future[Seq[WhatsRunningWhere]] = {
    val baseUrl = s"$serviceUrl/releases-api/whats-running-where"
    http
      .GET[Seq[WhatsRunningWhere]](profileName.fold(baseUrl)(profileName => s"$baseUrl?profile=${profileName.asString}"))
      .recover {
        case NonFatal(ex) =>
          Logger.error(s"An error occurred when connecting to $serviceUrl: ${ex.getMessage}", ex)
          Seq.empty
      }
  }
}
