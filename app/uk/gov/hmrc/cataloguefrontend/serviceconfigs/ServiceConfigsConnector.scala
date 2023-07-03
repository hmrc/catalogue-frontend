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

package uk.gov.hmrc.cataloguefrontend.serviceconfigs

import play.api.cache.AsyncCacheApi
import play.api.libs.json.Reads

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.connector.model.{BobbyRuleSet, TeamName}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.DeploymentConfig
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.model.ServiceDeploymentConfig
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceConfigsConnector @Inject() (
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
, cache         : AsyncCacheApi
)(implicit val ec: ExecutionContext) {
  import HttpReads.Implicits._
  import ServiceConfigsService._

  private val serviceConfigsBaseUrl: String = servicesConfig.baseUrl("service-configs")

  implicit val cber = ConfigByEnvironment.reads
  implicit val cbkr = ConfigByKey.reads
  implicit val cser = ConfigSourceEntries.reads
  implicit val srr  = ServiceRelationships.reads

  def configByKey(service: String, latest: Boolean)(implicit hc: HeaderCarrier): Future[ConfigByKey] =
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/config-by-key/$service?latest=$latest")
      .execute[ConfigByKey]

  def serviceRelationships(service: String)(implicit hc: HeaderCarrier): Future[ServiceRelationships] =
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/service-relationships/$service")
      .execute[ServiceRelationships]

  def bobbyRules()(implicit hc: HeaderCarrier): Future[BobbyRuleSet] = {
    implicit val brsr = BobbyRuleSet.reads
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/bobby/rules")
      .execute[BobbyRuleSet]
  }

  def deploymentConfig(
    service    : String,
    environment: Environment
  )(implicit hc: HeaderCarrier): Future[Option[DeploymentConfig]] = {
    implicit val dcr = DeploymentConfig.reads
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/deployment-config/${environment.asString}/$service")
      .execute[Option[DeploymentConfig]]
  }

  def allDeploymentConfig()(implicit hc: HeaderCarrier): Future[Seq[ServiceDeploymentConfig]] = {
    implicit val adsr = ServiceDeploymentConfig.reads
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/deployment-config")
      .execute[Seq[ServiceDeploymentConfig]]
  }

  private val configKeysCacheExpiration: Duration = servicesConfig.getConfDuration("configKeysCacheDuration", 1.hour)
  def getConfigKeys(teamName: Option[TeamName])(implicit hc: HeaderCarrier): Future[Seq[String]] =
    cache.getOrElseUpdate(s"config-keys-cache-${teamName.getOrElse("all")}", configKeysCacheExpiration) {
      httpClientV2
        .get(url"$serviceConfigsBaseUrl/service-configs/configkeys?teamName=${teamName.map(_.asString)}")
        .execute[Seq[String]]
    }

  def configSearch(
    teamName       : Option[TeamName]
  , environments   : Seq[Environment]
  , serviceType    : Option[ServiceType]
  , key            : Option[String]
  , keyFilterType  : KeyFilterType
  , value          : Option[String]
  , valueFilterType: ValueFilterType
  )(implicit hc: HeaderCarrier): Future[Either[String, Seq[AppliedConfig]]] = {
    implicit val acR: Reads[AppliedConfig] = AppliedConfig.reads

    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/search?teamName=${teamName.map(_.asString)}&environment=${environments.map(_.asString)}&serviceType=${serviceType.map(_.asString)}&key=$key&keyFilterType=${keyFilterType.asString}&value=${value}&valueFilterType=${valueFilterType.asString}")
      .execute[Either[UpstreamErrorResponse, Seq[AppliedConfig]]]
      .flatMap {
        case Left(err) if err.statusCode == 403 => Future.successful(Left("This search has too many results - please refine parameters."))
        case Left(other)                        => Future.failed(other)
        case Right(xs)                          => Future.successful(Right(xs))
      }
  }
}
