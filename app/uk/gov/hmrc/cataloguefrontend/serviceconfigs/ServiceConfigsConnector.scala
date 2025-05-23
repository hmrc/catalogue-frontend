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
import uk.gov.hmrc.cataloguefrontend.model.{Environment, DigitalService, ServiceName, TeamName, Version}
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
  private given Reads[ServiceToRepoName]    = ServiceToRepoName.reads

  def repoNameForService(service: ServiceName)(using HeaderCarrier): Future[Option[String]] =
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/services/repo-name?serviceName=${service.asString}")
      .execute[Option[String]]
    
  def serviceRepoMappings(using HeaderCarrier): Future[List[ServiceToRepoName]] =
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/service-repo-names")
      .execute[List[ServiceToRepoName]]

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

  def configChanges(
    deploymentId    : String
  , fromDeploymentId: Option[String]
  )(using
    HeaderCarrier
  ): Future[Option[ConfigChanges]] =
    given Reads[ConfigChanges] = ConfigChanges.reads
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/config-changes?deploymentId=$deploymentId&fromDeploymentId=$fromDeploymentId")
      .execute[Option[ConfigChanges]]

  def configChangesNextDeployment(
    serviceName : ServiceName,
    environment : Environment,
    version     : Version
  )(using
    HeaderCarrier
  ): Future[ConfigChanges] =
    given Reads[ConfigChanges] = ConfigChanges.reads
    httpClientV2
      .get(url"$serviceConfigsBaseUrl/service-configs/config-changes-next-deployment?serviceName=${serviceName.asString}&environment=${environment.asString}&version=${version.original}")
      .execute[ConfigChanges]

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
    service       : Option[ServiceName]    = None
  , environment   : Option[Environment]    = None
  , team          : Option[TeamName]       = None
  , digitalService: Option[DigitalService] = None
  , applied       : Boolean                = true
  )(using
    HeaderCarrier
  ): Future[Seq[DeploymentConfig]] =
    given Reads[DeploymentConfig] = DeploymentConfig.reads
    val queryParams = Seq(
      environment   .map("environment"    -> _.asString),
      service       .map("serviceName"    -> _.asString),
      team          .map("teamName"       -> _.asString),
      digitalService.map("digitalService" -> _.asString)
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

import play.api.libs.functional.syntax._
import play.api.libs.json.__

case class ConfigChange(
  from: Option[ServiceConfigsService.ConfigSourceValue]
, to  : Option[ServiceConfigsService.ConfigSourceValue]
)

object ConfigChange:
  val reads: Reads[ConfigChange] =
    given Reads[ServiceConfigsService.ConfigSourceValue] = ServiceConfigsService.ConfigSourceValue.reads
    ( (__ \ "from" ).readNullable[ServiceConfigsService.ConfigSourceValue]
    ~ (__ \ "to"   ).readNullable[ServiceConfigsService.ConfigSourceValue]
    )(ConfigChange.apply)

case class ConfigChanges(
  app              : ConfigChanges.App
, base             : ConfigChanges.BaseConfigChange
, common           : ConfigChanges.CommonConfigChange
, env              : ConfigChanges.EnvironmentConfigChange
, configChanges    : Map[ServiceConfigsService.KeyName , ConfigChange]
, deploymentChanges: Map[ServiceConfigsService.KeyName, ConfigChange]
)

object ConfigChanges:
  case class App(from: Option[Version], to: Version)
  case class CommitId(asString: String) extends AnyVal
  case class BaseConfigChange(from: Option[CommitId], to: Option[CommitId], githubUrl: String)
  case class CommonConfigChange(from: Option[CommitId], to: Option[CommitId], githubUrl: String)
  case class EnvironmentConfigChange(environment: Environment, from: Option[CommitId], to: Option[CommitId], githubUrl: String)

  val reads: Reads[ConfigChanges] =
    given Reads[App] =
      ( (__ \ "from").readNullable[Version](Version.format)
      ~ (__ \ "to"  ).read[Version](Version.format)
      )(App.apply)

    given Reads[CommitId] =
      summon[Reads[String]].map(CommitId.apply)

    given Reads[BaseConfigChange] =
      ( (__ \ "from"     ).readNullable[CommitId]
      ~ (__ \ "to"       ).readNullable[CommitId]
      ~ (__ \ "githubUrl").read[String]
      )(BaseConfigChange.apply)

    given Reads[CommonConfigChange] =
      ( (__ \ "from"     ).readNullable[CommitId]
      ~ (__ \ "to"       ).readNullable[CommitId]
      ~ (__ \ "githubUrl").read[String]
      )(CommonConfigChange.apply)

    given Reads[EnvironmentConfigChange] =
      ( (__ \ "environment").read[Environment]
      ~ (__ \ "from"       ).readNullable[CommitId]
      ~ (__ \ "to"         ).readNullable[CommitId]
      ~ (__ \ "githubUrl"  ).read[String]
      )(EnvironmentConfigChange.apply)

    ( (__ \ "app"              ).read[App]
    ~ (__ \ "base"             ).read[BaseConfigChange]
    ~ (__ \ "common"           ).read[CommonConfigChange]
    ~ (__ \ "env"              ).read[EnvironmentConfigChange]
    ~ (__ \ "configChanges"    ).read[Map[ServiceConfigsService.KeyName, ConfigChange]](ConfigChangeMap.reads)
    ~ (__ \ "deploymentChanges").read[Map[ServiceConfigsService.KeyName, ConfigChange]](ConfigChangeMap.reads)
    )(ConfigChanges.apply)

object ConfigChangeMap:
  val reads: Reads[Map[ServiceConfigsService.KeyName, ConfigChange]] =
    given Reads[ConfigChange] = ConfigChange.reads
    summon[Reads[Map[String, ConfigChange]]]
      .map:
        _.map: (k, v) =>
          (ServiceConfigsService.KeyName(k) -> v)
