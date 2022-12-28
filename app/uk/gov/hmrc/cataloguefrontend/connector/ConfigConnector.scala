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

package uk.gov.hmrc.cataloguefrontend.connector

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.connector.model.BobbyRuleSet
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.ConfigService._
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.DeploymentConfig
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.model.ServiceDeploymentConfig
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfigConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._

  private val serviceConfigsBaseUrl: String = servicesConfig.baseUrl("service-configs")

  implicit val cber = ConfigByEnvironment.reads
  implicit val cbkr = ConfigByKey.reads
  implicit val cser = ConfigSourceEntries.reads

  def configByEnv(service: String)(implicit hc: HeaderCarrier): Future[ConfigByEnvironment] =
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/config-by-env/$service")
      .execute[ConfigByEnvironment]

  def configByKey(service: String)(implicit hc: HeaderCarrier): Future[ConfigByKey] =
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/config-by-key/$service")
      .execute[ConfigByKey]

  def appConfig(service: String)(implicit hc: HeaderCarrier): Future[Seq[ConfigSourceEntries]] =
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/app-config/$service")
      .execute[Seq[ConfigSourceEntries]]

  def bobbyRules()(implicit hc: HeaderCarrier): Future[BobbyRuleSet] = {
    implicit val brsr = BobbyRuleSet.reads
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/bobby/rules")
      .execute[BobbyRuleSet]
  }

  def deploymentConfig(
    service: String,
    environment: Environment
  )(implicit hc: HeaderCarrier): Future[Option[DeploymentConfig]] = {
    implicit val dcr = DeploymentConfig.reads
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/deployment-config/${environment.asString}/$service")
      .execute[Option[DeploymentConfig]]
  }

  def allDeploymentConfig(implicit hc: HeaderCarrier): Future[Seq[ServiceDeploymentConfig]] = {
    implicit val adsr = ServiceDeploymentConfig.reads
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/deployment-config")
      .execute[Seq[ServiceDeploymentConfig]]
  }
}
