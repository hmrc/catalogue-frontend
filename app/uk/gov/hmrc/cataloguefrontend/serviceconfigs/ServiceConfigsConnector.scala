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

import play.api.Logger
import play.api.cache.AsyncCacheApi
import play.api.libs.json.Reads
import uk.gov.hmrc.cataloguefrontend.connector.ServiceType
import uk.gov.hmrc.cataloguefrontend.connector.model.BobbyRuleSet
import uk.gov.hmrc.cataloguefrontend.cost.DeploymentConfig
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, TeamName, Version}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceConfigsConnector @Inject() (
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
, cache         : AsyncCacheApi
)(using
  ExecutionContext
):
  import HttpReads.Implicits._
  import ServiceConfigsService._

  private val serviceConfigsBaseUrl: String = servicesConfig.baseUrl("service-configs")
  private val logger = Logger(getClass)

  private given Reads[ConfigByEnvironment]  = ConfigByEnvironment.reads
  private given Reads[ConfigWarning]        = ConfigWarning.reads
  private given Reads[ServiceRelationships] = ServiceRelationships.reads

  def repoNameForService(service: ServiceName)(using HeaderCarrier): Future[Option[String]] =
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/services/repo-name?serviceName=${service.asString}")
      .execute[Option[String]]

  def deploymentEvents(
    service: ServiceName,
    from   : Instant,
    to     : Instant
  )(using
    HeaderCarrier
   ): Future[Seq[DeploymentConfigEvent]] =
    given Reads[DeploymentConfigEvent] = DeploymentConfigEvent.reads
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/deployment-events/${service.asString}?from=$from&to=$to")
      .execute[Seq[DeploymentConfigEvent]]

  def configByEnv(
    service     : ServiceName
  , environments: Seq[Environment]
  , version     : Option[Version]
  , latest      : Boolean
  )(using
    HeaderCarrier
  ): Future[Map[ConfigEnvironment, Seq[ConfigSourceEntries]]]  =
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/config-by-env/${service.asString}?environment=${environments.map(_.asString)}&version=${version.map(_.original)}&latest=$latest")
      .execute[Map[ConfigEnvironment, Seq[ConfigSourceEntries]]]

  def serviceRelationships(service: ServiceName)(using HeaderCarrier): Future[ServiceRelationships] =
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/service-relationships/${service.asString}")
      .execute[ServiceRelationships]

  def bobbyRules()(using HeaderCarrier): Future[BobbyRuleSet] =
    given Reads[BobbyRuleSet] = BobbyRuleSet.reads
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/bobby/rules")
      .execute[BobbyRuleSet]

  def deploymentConfig(
    service    : Option[ServiceName] = None
  , environment: Option[Environment] = None
  , team       : Option[TeamName]    = None
  , applied    : Boolean             = true
  )(using
    HeaderCarrier
  ): Future[Seq[DeploymentConfig]] =
    given Reads[DeploymentConfig] = DeploymentConfig.reads
    val queryParams = Seq(
      environment.map("environment" -> _.asString),
      service    .map("serviceName" -> _.asString),
      team       .map("teamName"    -> _.asString)
    ).flatten.toMap
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/deployment-config?$queryParams&applied=$applied")
      .execute[Option[Seq[DeploymentConfig]]]
      .map(_.getOrElse:
        logger.info(s"No deployments found for $queryParams")
        Seq.empty
      )

  private val configKeysCacheExpiration: Duration =
    servicesConfig.getConfDuration("configKeysCacheDuration", 1.hour)

  def getConfigKeys(teamName: Option[TeamName])(using HeaderCarrier): Future[Seq[String]] =
    cache.getOrElseUpdate(s"config-keys-cache-${teamName.getOrElse("all")}", configKeysCacheExpiration):
      httpClientV2
        .get(url"$serviceConfigsBaseUrl/service-configs/configkeys?teamName=${teamName.map(_.asString)}")
        .execute[Seq[String]]

  def configSearch(
    teamName       : Option[TeamName]
  , environments   : Seq[Environment]
  , serviceType    : Option[ServiceType]
  , key            : Option[String]
  , keyFilterType  : KeyFilterType
  , value          : Option[String]
  , valueFilterType: ValueFilterType
  )(using
    HeaderCarrier
  ): Future[Either[String, Seq[AppliedConfig]]] =
    given Reads[AppliedConfig] = AppliedConfig.reads

    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/search?teamName=${teamName.map(_.asString)}&environment=${environments.map(_.asString)}&serviceType=${serviceType.map(_.asString)}&key=$key&keyFilterType=${keyFilterType.asString}&value=${value}&valueFilterType=${valueFilterType.asString}")
      .execute[Either[UpstreamErrorResponse, Seq[AppliedConfig]]]
      .flatMap:
        case Left(err) if err.statusCode == 403 => Future.successful(Left("This search has too many results - please refine parameters."))
        case Left(other)                        => Future.failed(other)
        case Right(xs)                          => Future.successful(Right(xs))

  def configWarnings(
    serviceName : ServiceName
  , environments: Seq[Environment]
  , version     : Option[Version]
  , latest      : Boolean
  )(using
    HeaderCarrier
  ): Future[Seq[ConfigWarning]] =
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/warnings?serviceName=${serviceName.asString}&environment=${environments.map(_.asString)}&version=${version.map(_.original)}&latest=$latest")
      .execute[Seq[ConfigWarning]]

end ServiceConfigsConnector
