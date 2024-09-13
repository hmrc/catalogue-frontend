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

import cats.implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.connector.{ServiceType, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.cost.DeploymentConfig
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{LifecycleStatus, ServiceCommissioningStatusConnector}
import uk.gov.hmrc.cataloguefrontend.util.Base64Util
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.collection.immutable.TreeMap
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceConfigsService @Inject()(
  serviceConfigsConnector      : ServiceConfigsConnector,
  teamsAndReposConnector       : TeamsAndRepositoriesConnector,
  serviceCommissioningConnector: ServiceCommissioningStatusConnector
)(using
  ExecutionContext
):
  import ServiceConfigsService._

  private def configByKey(
    serviceName : ServiceName,
    environments: Seq[Environment],
    version     : Option[Version],
    latest      : Boolean
  )(using
    HeaderCarrier
  ): Future[Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]]] =
    serviceConfigsConnector
      .configByEnv(serviceName, environments, version, latest)
      .map:
        _
          .foldLeft(Map.empty[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]]):
            case (acc, (e -> cses)) =>
              cses.foldLeft(acc): (acc2, cse) =>
                acc2 ++ cse.entries.map: (key, value) =>
                  val envMap = acc2.getOrElse(key, Map.empty)
                  val values = envMap.getOrElse(e, Seq.empty)
                  key -> (envMap + (e -> (values :+ ConfigSourceValue(cse.source, cse.sourceUrl, value))))
      .map(xs => scala.collection.immutable.ListMap(xs.toSeq.sortBy(_._1.asString)*)) // sort by keys

  def configChanges(
    deploymentId    : String
  , fromDeploymentId: Option[String]
  )(using
    HeaderCarrier
  ): Future[ConfigChanges] =
    serviceConfigsConnector
      .configChanges(deploymentId, fromDeploymentId)
      .map: configChanges =>
        configChanges.copy(
          changes = configChanges.changes.filter(c => c._2.from.exists(!_.isReferenceConf) || c._2.to.exists(!_.isReferenceConf))
        )

  def configChangesNextDeployment(
    serviceName: ServiceName,
    environment: Environment,
    version    : Version
  )(using
    HeaderCarrier
  ): Future[ConfigChanges] =
    serviceConfigsConnector
      .configChangesNextDeployment(serviceName, environment, version)
      .map: configChanges =>
        configChanges.copy(
          changes = configChanges.changes.filter(c => c._2.from.exists(!_.isReferenceConf) || c._2.to.exists(!_.isReferenceConf))
        )

  def configByKeyWithNextDeployment(
    serviceName : ServiceName,
    environments: Seq[Environment] = Nil,
    version     : Option[Version]  = None
  )(using
    HeaderCarrier
  ): Future[Map[KeyName, Map[ConfigEnvironment, Seq[(ConfigSourceValue, Boolean)]]]] =
    for
      latestConfigByKey   <- configByKey(serviceName, environments, version, latest = true )
      deployedConfigByKey <- configByKey(serviceName, environments, None   , latest = false)
      configByKey         =  deployedConfigByKey.map: (k, m) =>
                               k -> m.map: (e, vs) =>
                                      latestConfigByKey.getOrElse(k, Map.empty).getOrElse(e, Seq.empty).lastOption match
                                        case Some(n) if vs.lastOption.exists(_.value != n.value) => e -> (vs.map(x => (x -> false)) :+ (n -> true))
                                        case None    if vs.lastOption.isDefined                  => e -> (vs.map(x => (x -> false)) :+ (ConfigSourceValue(source = "", sourceUrl = None, value = "") -> true))
                                        case _                                                   => e -> (vs.map(x => (x -> false)))
      newConfig           =  latestConfigByKey.map: (k, m) =>
                               k -> m.flatMap: (e, vs) =>
                                      configByKey.getOrElse(k, Map.empty).getOrElse(e, Seq.empty) match
                                        case Nil => vs.lastOption.map(n => e -> Seq(n -> true))
                                        case xs  => Some(e -> xs)
    yield newConfig

  def serviceRelationships(serviceName: ServiceName)(using HeaderCarrier): Future[ServiceRelationshipsEnriched] =
    for
      repos    <- teamsAndReposConnector.allRepositories()
      srs      <- serviceConfigsConnector.serviceRelationships(serviceName)
      outbound <- srs.outboundServices
                    .filterNot(_ == serviceName)
                    .foldLeftM[Future, Seq[ServiceRelationship]](Seq.empty): (acc, service) =>
                      repos.find(_.name == service.asString)
                        .map: repo =>
                          serviceCommissioningConnector
                            .getLifecycle(service)
                            .map: status =>
                              ServiceRelationship(service, hasRepo = true, status.map(_.lifecycleStatus), repo.endOfLifeDate)
                        .getOrElse(Future.successful(ServiceRelationship(service, hasRepo = false, lifecycleStatus = None, endOfLifeDate = None)))
                        .map(_ +: acc)
      inbound  =  srs.inboundServices
                    .filterNot(_ == serviceName)
                    .map: service =>
                      ServiceRelationship(service, hasRepo = repos.exists(_.name == service.asString), lifecycleStatus = None, endOfLifeDate = None)
    yield
      ServiceRelationshipsEnriched(
        inbound.sortBy(_.service)
      , outbound.sortBy: a =>
          ( !a.lifecycleStatus.exists(List(LifecycleStatus.Deprecated, LifecycleStatus.DecommissionInProgress).contains)
          , a.service
          )
      )

  def configKeys(teamName: Option[TeamName] = None)(using HeaderCarrier): Future[Seq[String]] =
    serviceConfigsConnector.getConfigKeys(teamName)

  def deploymentConfig(
    environment: Option[Environment] = None,
    serviceName: Option[ServiceName] = None
  )(using
    HeaderCarrier
  ): Future[Seq[DeploymentConfig]] =
    serviceConfigsConnector.deploymentConfig(serviceName, environment)

  def deploymentConfigByKeyWithNextDeployment(serviceName: ServiceName)(using HeaderCarrier): Future[Map[KeyName, Map[ConfigEnvironment, Seq[(ConfigSourceValue, Boolean)]]]] =
    for
      applied        <- serviceConfigsConnector.deploymentConfig(service = Some(serviceName), applied = true )
      nextDeployment <- serviceConfigsConnector.deploymentConfig(service = Some(serviceName), applied = false)
      allEnvs        =  (applied ++ nextDeployment).map(_.environment).distinct
      allKeys        =  (applied ++ nextDeployment).flatMap(_.asMap.keys).distinct
    yield
      allKeys
        .map: key =>
          val keyValues =
            for
              env          <- allEnvs
              appliedValue =  applied.find(_.environment == env).flatMap(_.asMap.get(key))
              nextValue    =  nextDeployment.find(_.environment == env).flatMap(_.asMap.get(key))
            yield
              ((appliedValue, nextValue) match
                case (Some(applied), Some(next)) if applied == next => Seq((applied, false))
                case (Some(applied), Some(next)) => Seq((applied, false), (next, true))
                case (Some(applied), None      ) => Seq((applied, false))
                case (None         , Some(next)) => Seq((next, true))
                case _                           => Seq.empty
              ).map: (value, nextDeployment) =>
                ConfigSourceValue(
                  source    = "appConfigEnvironment"
                , sourceUrl = Some(s"https://github.com/hmrc/app-config-${env.asString}/blob/main/${serviceName.asString}.yaml")
                , value     = if   key.startsWith("environment.") && value.length > 100 && Base64Util.isBase64Decodable(value)
                              then "<<BASE64 ENCODED STRING>>"
                              else value
                ) -> nextDeployment

          val configEnvs: Seq[ConfigEnvironment] =
            allEnvs.map(ConfigEnvironment.ForEnvironment.apply)

          KeyName(key) -> configEnvs.zip(keyValues).toMap
        .toMap

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
    serviceConfigsConnector.configSearch(
      teamName,
      environments,
      serviceType,
      key             = key,
      keyFilterType   = keyFilterType,
      value           = value,
      valueFilterType
    )

  private def sorted[K, V](unsorted: Map[K, V])(using ordering: Ordering[K]): Map[K, V] =
    TreeMap[K, V]() ++ unsorted

  def toKeyServiceEnvironmentMap(
    appliedConfig: Seq[AppliedConfig]
  ): Map[KeyName, Map[ServiceName, Map[Environment, ConfigSourceValue]]] =
    appliedConfig
      .groupBy(_.key)
      .view
      .mapValues: xs =>
        sorted(xs.groupBy(_.serviceName).view.mapValues(_.map(_.environments).flatten.toMap).toMap)
      .toMap

  def toServiceKeyEnvironmentMap(appliedConfig: Seq[AppliedConfig]): Map[ServiceName, Map[KeyName, Map[Environment, ConfigSourceValue]]] =
    appliedConfig
      .groupBy(_.serviceName)
      .view
      .mapValues: xs =>
        sorted(xs.groupBy(_.key).view.mapValues(_.map(_.environments).flatten.toMap).toMap)
      .toMap

  def configWarnings(
    serviceName : ServiceName
  , environments: Seq[Environment]
  , version     : Option[Version]
  , latest      : Boolean
  )(using HeaderCarrier): Future[Seq[ConfigWarning]] =
    serviceConfigsConnector
      .configWarnings(serviceName, environments,  version, latest)

  def toServiceKeyEnvironmentWarningMap(configWarnings: Seq[ConfigWarning]): Map[ServiceName, Map[KeyName, Map[Environment, Seq[ConfigWarning]]]] =
    configWarnings
      .groupBy(_.serviceName)
      .view
      .mapValues: xs =>
        sorted(xs.groupBy(_.key).view.mapValues(_.groupBy(_.environment)).toMap)(using Ordering.by(_.asString))
      .toMap


  def deploymentConfigChanges(
    service    : ServiceName,
    environment: Environment
  )(using HeaderCarrier): Future[Seq[DeploymentConfigChange]] =
    for
      appliedConfig  <- serviceConfigsConnector.deploymentConfig(service = Some(service), environment = Some(environment), applied = true ).map(_.headOption)
      newConfig      <- serviceConfigsConnector.deploymentConfig(service = Some(service), environment = Some(environment), applied = false).map(_.headOption)
      slots          =  valChanges(
                          "slots",
                          appliedConfig.map(_.deploymentSize.slots.toString),
                          newConfig.map(_.deploymentSize.slots.toString)
                        )
      instances      =  valChanges(
                          "instances",
                          appliedConfig.map(_.deploymentSize.instances.toString),
                          newConfig.map(_.deploymentSize.instances.toString)
                        )
      envVars        =  mapChanges(
                          "environment",
                          appliedConfig.fold(Map.empty[String, String])(_.envVars),
                          newConfig.fold(Map.empty[String, String])(_.envVars)
                        )
      jvm            =  mapChanges(
                          "jvm",
                          appliedConfig.fold(Map.empty[String, String])(_.jvm),
                          newConfig.fold(Map.empty[String, String])(_.jvm)
                        )
    yield (slots ++ instances ++ envVars ++ jvm).sortBy(_.k)

  private def valChanges(key: String, appliedVal: Option[String], newVal: Option[String]): Seq[DeploymentConfigChange] =
    (appliedVal, newVal) match
      case (Some(appliedVal), Some(newVal)) if appliedVal != newVal => Seq(DeploymentConfigChange.ChangedConfig(key, appliedVal, newVal))
      case (None            , Some(newVal))                         => Seq(DeploymentConfigChange.NewConfig(key, newVal))
      case (Some(appliedVal), None        )                         => Seq(DeploymentConfigChange.DeletedConfig(key, appliedVal))
      case _                                                        => Seq.empty

  private def mapChanges(keyPrefix: String, appliedConf: Map[String, String], newConf: Map[String, String]): Seq[DeploymentConfigChange] =
    appliedConf.toSeq
      .collect:
        case (k, v) if newConf.get(k).isEmpty =>
          DeploymentConfigChange.DeletedConfig(s"$keyPrefix.${k}", v)
      ++
        newConf.toSeq.flatMap: (k, v) =>
          appliedConf.get(k) match
            case Some(appliedV) if appliedV != v => Seq(DeploymentConfigChange.ChangedConfig(s"$keyPrefix.${k}", appliedV, v))
            case None                            => Seq(DeploymentConfigChange.NewConfig(s"$keyPrefix.${k}", v))
            case _                               => Seq.empty
end ServiceConfigsService

object ServiceConfigsService:

  case class KeyName(asString: String) extends AnyVal
  object KeyName:
    given Ordering[KeyName] = Ordering.by(_.asString.toLowerCase)

    val deploymentConfigOrder: Ordering[KeyName] =
      Ordering.by: (key: KeyName) =>
        key.asString match
          case "instances"                       => (0, key)
          case "slots"                           => (1, key)
          case s if s.startsWith("jvm.")         => (2, key)
          case s if s.startsWith("environment.") => (3, key)
          case _                                 => (4, key)

  sealed trait ConfigEnvironment { def asString: String; def displayString: String }

  object ConfigEnvironment:
    case object Local                           extends ConfigEnvironment:
      override def asString      = "local"
      override def displayString = "Local"

    case class ForEnvironment(env: Environment) extends ConfigEnvironment:
      override def asString      = env.asString
      override def displayString = env.displayString

    val values: Seq[ConfigEnvironment] =
      Local +: Environment.values.toSeq.map(ForEnvironment.apply)

    val reads: Reads[ConfigEnvironment] =
      (json: JsValue) =>
        json
          .validate[JsString]
          .flatMap:
            case JsString("local") => JsSuccess(ConfigEnvironment.Local)
            case s                 => summon[Reads[Environment]].reads(s).map(ForEnvironment.apply)

  type ConfigByEnvironment = Map[ConfigEnvironment, Seq[ConfigSourceEntries]]

  object ConfigByEnvironment:
    val reads: Reads[ConfigByEnvironment] =
      given Reads[ConfigEnvironment]   = ConfigEnvironment.reads
      given Reads[ConfigSourceEntries] = ConfigSourceEntries.reads
      Reads
        .of[Map[String, Seq[ConfigSourceEntries]]]
        .map:
          _.map: (k, v) =>
            (JsString(k).as[ConfigEnvironment], v)

  case class ConfigSourceEntries(
    source   : String,
    sourceUrl: Option[String],
    entries  : Map[KeyName, String]
  )

  object ConfigSourceEntries:
    val reads: Reads[ConfigSourceEntries] =
      ( (__ \ "source"   ).read[String]
      ~ (__ \ "sourceUrl").readNullable[String]
      ~ (__ \ "entries"  ).read[Map[String, String]].map(_.map((k, v) => KeyName(k) -> v).toMap)
      )(ConfigSourceEntries.apply)

  case class ConfigSourceValue(
    source   : String,
    sourceUrl: Option[String],
    value    : String
  ):
    val isReferenceConf: Boolean =
      source == "referenceConf"

    val isSuppressed: Boolean =
      value == "<<SUPPRESSED>>"

    val displayString: String =
      if      source.trim.isEmpty then "<<DELETED>>"
      else if value.trim.isEmpty  then "<<BLANK>>"
      else                             value

  object ConfigSourceValue {
    val reads: Reads[ConfigSourceValue] =
      ( (__ \ "source"   ).read[String]
      ~ (__ \ "sourceUrl").readNullable[String]
      ~ (__ \ "value"    ).read[String]
      )(ConfigSourceValue.apply)
  }

  case class ServiceRelationships(
    inboundServices : Seq[ServiceName],
    outboundServices: Seq[ServiceName]
  )

  object ServiceRelationships {
    val reads: Reads[ServiceRelationships] =
      ( (__ \ "inboundServices" ).read[Seq[ServiceName]]
      ~ (__ \ "outboundServices").read[Seq[ServiceName]]
      )(ServiceRelationships.apply)
  }

  case class ServiceRelationship(
    service        : ServiceName,
    hasRepo        : Boolean,
    lifecycleStatus: Option[LifecycleStatus],
    endOfLifeDate  : Option[Instant]
  )

  case class ServiceRelationshipsEnriched(
    inboundServices : Seq[ServiceRelationship],
    outboundServices: Seq[ServiceRelationship]
  ):
    def size: Int =
      Seq(inboundServices.size, outboundServices.size).max

    def hasDeprecatedDownstream: Boolean =
      outboundServices
        .exists(_.lifecycleStatus.contains(LifecycleStatus.Deprecated))

  def friendlySourceName(
    source     : String,
    environment: ConfigEnvironment,
    key        : Option[KeyName]
  ): String =
    source match
      case "loggerConf"                 => "Microservice application-json-logger.xml file"
      case "referenceConf"              => "Microservice reference.conf files"
      case "bootstrapFrontendConf"      => "Bootstrap frontend.conf file"
      case "bootstrapBackendConf"       => "Bootstrap backend.conf file"
      case "applicationConf"            => "Microservice application.conf file"
      case "baseConfig"                 => "App-config-base"
      case "appConfigEnvironment"       => s"App-config-${environment.asString}"
      case "appConfigCommonFixed"       => "App-config-common fixed settings"
      case "appConfigCommonOverridable" => "App-config-common overridable settings"
      case "base64"                     => s"Base64 (decoded from config ${key.fold("'key'")(_.asString)}.base64)"
      case _                            => source

  def warningDescription(
    warningType: String
  ): String =
    warningType match
      case "NotOverriding"       => "Config doesn't appear to override any existing key"
      case "TypeChange"          => "Config value overrides a value with different type"
      case "Localhost"           => "Use of localhost in value"
      case "Debug"               => "Use of Debug log level. This should only be enabled briefly in Production"
      case "TestOnlyRoutes"      => "Use of test only routes. This should not be enabled in Production"
      case "ReactiveMongoConfig" => "Use of obsolete reactivemongo config"
      case "Unencrypted"         => "Value looks like it should be encrypted"
      case _                     => ""

  case class AppliedConfig(
    serviceName  : ServiceName
  , key          : KeyName
  , environments : Map[Environment, ConfigSourceValue]
  )

  object AppliedConfig:
    val reads: Reads[AppliedConfig] =
      given Reads[ConfigSourceValue] = ConfigSourceValue.reads
      given Reads[Map[Environment, ConfigSourceValue]] =
        import uk.gov.hmrc.cataloguefrontend.util.CategoryHelper.given cats.Applicative[JsResult]

        Reads
          .of[Map[JsString, ConfigSourceValue]]
          .flatMap: m =>
            _ =>
              m
                .toSeq
                .traverse: (k, v) =>
                  summon[Reads[Environment]].reads(k).map: e =>
                    (e, v)
                .map(_.toMap)


      ( (__ \ "serviceName"  ).read[ServiceName]
      ~ (__ \ "key"          ).read[String].map(KeyName.apply)
      ~ (__ \ "environments" ).read[Map[Environment, ConfigSourceValue]]
      )(AppliedConfig.apply)

  case class ConfigWarning(
    serviceName: ServiceName
  , environment: Environment
  , key        : KeyName
  , value      : ConfigSourceValue
  , warning    : String
  )

  object ConfigWarning:
    val reads: Reads[ConfigWarning] =
      ( (__ \ "serviceName").read[ServiceName]
      ~ (__ \ "environment").read[Environment]
      ~ (__ \ "key"        ).read[String].map(KeyName.apply)
      ~ (__ \ "value"      ).read[ConfigSourceValue](ConfigSourceValue.reads)
      ~ (__ \ "warning"    ).read[String]
      )(ConfigWarning.apply)

end ServiceConfigsService

enum DeploymentConfigChange(val k: String):
  case NewConfig    (override val k: String, v: String)                       extends DeploymentConfigChange(k)
  case DeletedConfig(override val k: String, previousV: String)               extends DeploymentConfigChange(k)
  case ChangedConfig(override val k: String, previousV: String, newV: String) extends DeploymentConfigChange(k)
