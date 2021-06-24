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

package uk.gov.hmrc.cataloguefrontend.connector

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.connector.model.BobbyRuleSet
import uk.gov.hmrc.cataloguefrontend.service.ConfigService._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfigConnector @Inject() (
  http: HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._

  private val serviceConfigsBaseUrl: String = servicesConfig.baseUrl("service-configs")

  implicit val cber = ConfigByEnvironment.reads
  implicit val cbkr = ConfigByKey.reads

  def configByEnv(service: String)(implicit hc: HeaderCarrier): Future[ConfigByEnvironment] =
    http.GET[ConfigByEnvironment](s"$serviceConfigsBaseUrl/config-by-env/$service")

  def configByKey(service: String)(implicit hc: HeaderCarrier): Future[ConfigByKey] =
    http.GET[ConfigByKey](s"$serviceConfigsBaseUrl/config-by-key/$service")

  def bobbyRules()(implicit hc: HeaderCarrier): Future[BobbyRuleSet] = {
    implicit val brsr = BobbyRuleSet.reads
    http.GET[BobbyRuleSet](s"$serviceConfigsBaseUrl/bobby/rules")
  }
}
