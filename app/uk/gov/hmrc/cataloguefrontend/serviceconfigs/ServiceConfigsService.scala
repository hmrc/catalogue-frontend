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
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.connector.model.{TeamName, Version}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.CostEstimationService.DeploymentConfig
import uk.gov.hmrc.cataloguefrontend.servicecommissioningstatus.{LifecycleStatus, ServiceCommissioningStatusConnector}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.collection.immutable.TreeMap
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceConfigsService @Inject()(
  serviceConfigsConnector      : ServiceConfigsConnector,
  teamsAndReposConnector       : TeamsAndRepositoriesConnector,
  serviceCommissioningConnector: ServiceCommissioningStatusConnector
)(implicit
  ec: ExecutionContext
) {
  import ServiceConfigsService._

  private def configByKey(
    serviceName : String,
    environments: Seq[Environment],
    version     : Option[Version],
    latest      : Boolean
  )(implicit
    hc          : HeaderCarrier
  ): Future[Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]]] =
    serviceConfigsConnector
      .configByEnv(serviceName, environments, version, latest)
      .map(_.foldLeft(Map.empty[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]]) { case (acc, (e -> cses)) =>
        cses.foldLeft(acc) { case (acc2, cse) =>
          acc2 ++ cse.entries.map { case (key, value) =>
            val envMap = acc2.getOrElse(key, Map.empty)
            val values = envMap.getOrElse(e, Seq.empty)
            key -> (envMap + (e -> (values :+ ConfigSourceValue(cse.source, cse.sourceUrl, value))))
          }
        }
      }).map(xs => scala.collection.immutable.ListMap(xs.toSeq.sortBy(_._1.asString): _*)) // sort by keys

  def removedConfig(
    serviceName : String,
    environments: Seq[Environment] = Nil,
    version     : Option[Version]  = None
  )(implicit
    hc: HeaderCarrier
  ): Future[Map[KeyName, Map[ConfigEnvironment, Seq[(ConfigSourceValue, Boolean)]]]] =
    for {
      latestConfigByKey   <- configByKey(serviceName, environments, version, latest = true )
      deployedConfigByKey <- configByKey(serviceName, environments, None   , latest = false)
      configByKey         =  deployedConfigByKey.map {
                               case (k, m) => k -> m.map {
                                 case (e, vs) =>
                                   latestConfigByKey.getOrElse(k, Map.empty).getOrElse(e, Seq.empty).lastOption match {
                                     case Some(n) if vs.lastOption.exists(_.value != n.value) => e -> (vs.map(x => (x -> false)))
                                     case None    if vs.lastOption.isDefined                  => e -> (vs.map(x => (x -> true)))
                                     case _                                                   => e -> (vs.map(x => (x -> false)))
                                   }
                               }
                             }
    } yield (configByKey)

  def configByKeyWithNextDeployment(
    serviceName : String,
    environments: Seq[Environment] = Nil,
    version     : Option[Version]  = None
  )(implicit
    hc: HeaderCarrier
  ): Future[Map[KeyName, Map[ConfigEnvironment, Seq[(ConfigSourceValue, Boolean)]]]] =
    for {
      latestConfigByKey   <- configByKey(serviceName, environments, version, latest = true )
      deployedConfigByKey <- configByKey(serviceName, environments, None   , latest = false)
      configByKey         =  deployedConfigByKey.map {
                               case (k, m) => k -> m.map {
                                 case (e, vs) =>
                                   latestConfigByKey.getOrElse(k, Map.empty).getOrElse(e, Seq.empty).lastOption match {
                                     case Some(n) if vs.lastOption.exists(_.value != n.value) => e -> (vs.map(x => (x -> false)) :+ (n -> true))
                                     case None    if vs.lastOption.isDefined                  => e -> (vs.map(x => (x -> false)) :+ (ConfigSourceValue(source = "", sourceUrl = None, value = "") -> true))
                                     case _                                                   => e -> (vs.map(x => (x -> false)))
                                   }
                               }
                             }
       newConfig         =   latestConfigByKey.map {
                               case (k, m) => k -> m.flatMap {
                                 case (e, vs) =>
                                   configByKey.getOrElse(k, Map.empty).getOrElse(e, Seq.empty) match {
                                     case Nil => vs.lastOption.map(n => e -> Seq(n -> true))
                                     case xs  => Some(e -> xs)
                                   }
                               }
                             }
    } yield (newConfig)

def serviceRelationships(serviceName: String)(implicit hc: HeaderCarrier): Future[ServiceRelationshipsEnriched] =
    for {
      repos    <- teamsAndReposConnector.allRepositories()
      srs      <- serviceConfigsConnector.serviceRelationships(serviceName)
      outbound <- srs.outboundServices
                    .filterNot(_ == serviceName)
                    .foldLeftM[Future, Seq[ServiceRelationship]](Seq.empty) { (acc, service) =>
                      val hasRepo = repos.exists(_.name == service)
                      (if (hasRepo)
                         serviceCommissioningConnector
                           .getLifecycleStatus(service)
                           .map(status => ServiceRelationship(service, hasRepo, status))
                       else
                         Future.successful(ServiceRelationship(service, hasRepo, lifecycleStatus = None))
                      ).map(_ +: acc)
                    }
      inbound  =  srs.inboundServices
                    .filterNot(_ == serviceName)
                    .sorted
                    .map { service =>
                      ServiceRelationship(service, hasRepo = repos.exists(_.name == service), lifecycleStatus = None)
                    }
    } yield ServiceRelationshipsEnriched(
      inbound,
      outbound.sortBy(a => (if (a.lifecycleStatus.contains(LifecycleStatus.DecommissionInProgress)) 0 else 1, a.service.toLowerCase))
    )

  def configKeys(teamName: Option[TeamName] = None)(implicit hc: HeaderCarrier): Future[Seq[String]] =
    serviceConfigsConnector.getConfigKeys(teamName)

  def deploymentConfig(
    environment: Option[Environment] = None,
    serviceName: Option[String]      = None
  )(implicit
    hc         : HeaderCarrier
  ): Future[Seq[DeploymentConfig]] =
    serviceConfigsConnector.deploymentConfig(serviceName, environment)

  def configSearch(
    teamName       : Option[TeamName]
  , environments   : Seq[Environment]
  , serviceType    : Option[ServiceType]
  , key            : Option[String]
  , keyFilterType  : KeyFilterType
  , value          : Option[String]
  , valueFilterType: ValueFilterType
  )(implicit hc: HeaderCarrier): Future[Either[String, Seq[AppliedConfig]]] =
    serviceConfigsConnector.configSearch(teamName, environments, serviceType, key = key, keyFilterType = keyFilterType, value = value, valueFilterType)

  private def sorted[K, V](unsorted: Map[K, V])(implicit ordering: Ordering[K]): Map[K, V] = TreeMap[K, V]() ++ unsorted

  def toKeyServiceEnvironmentMap(appliedConfig: Seq[AppliedConfig]): Map[KeyName, Map[ServiceName, Map[Environment, ConfigSourceValue]]] =
    appliedConfig
      .groupBy(_.key)
      .view
      .mapValues(xs => sorted(xs.groupBy(_.serviceName).view.mapValues(_.map(_.environments).flatten.toMap).toMap)(Ordering.by(_.asString))).toMap

  def toServiceKeyEnvironmentMap(appliedConfig: Seq[AppliedConfig]): Map[ServiceName, Map[KeyName, Map[Environment, ConfigSourceValue]]] =
    appliedConfig
      .groupBy(_.serviceName)
      .view
      .mapValues(xs => sorted(xs.groupBy(_.key).view.mapValues(_.map(_.environments).flatten.toMap).toMap)(Ordering.by(_.asString))).toMap

  def configWarnings(
    serviceName : ServiceConfigsService.ServiceName
  , environments: Seq[Environment]
  , version     : Option[Version]
  , latest      : Boolean
  )(implicit hc: HeaderCarrier): Future[Seq[ConfigWarning]] =
    serviceConfigsConnector
      .configWarnings(serviceName, environments,  version, latest)

  def toServiceKeyEnvironmentWarningMap(configWarnings: Seq[ConfigWarning]): Map[ServiceName, Map[KeyName, Map[Environment, Seq[ConfigWarning]]]] =
    configWarnings
      .groupBy(_.serviceName)
      .view
      .mapValues(xs => sorted(xs.groupBy(_.key).view.mapValues(_.groupBy(_.environment)).toMap)(Ordering.by(_.asString))).toMap


  def deploymentConfigChanges(service: ServiceName, environment: Environment)(implicit hc: HeaderCarrier): Future[Seq[ConfigChange]] =
    for {
      appliedConfig  <- serviceConfigsConnector.deploymentConfig(service = Some(service.asString), environment = Some(environment), applied = true ).map(_.headOption)
      newConfig      <- serviceConfigsConnector.deploymentConfig(service = Some(service.asString), environment = Some(environment), applied = false).map(_.headOption)
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
    } yield (slots ++ instances ++ envVars ++ jvm).sortBy(_.k)

    private def valChanges(key: String, appliedVal: Option[String], newVal: Option[String]): Seq[ConfigChange] =
      (appliedVal, newVal) match {
        case (Some(appliedVal), Some(newVal)) if appliedVal != newVal => Seq(ConfigChange.ChangedConfig(key, appliedVal, newVal))
        case (None            , Some(newVal))                         => Seq(ConfigChange.NewConfig(key, newVal))
        case (Some(appliedVal), None        )                         => Seq(ConfigChange.DeletedConfig(key, appliedVal))
        case _                                                        => Seq.empty
      }

    private def mapChanges(keyPrefix: String, appliedConf: Map[String, String], newConf: Map[String, String]): Seq[ConfigChange] =
      appliedConf.toSeq.collect { case (k, v) if newConf.get(k).isEmpty =>
        ConfigChange.DeletedConfig(s"$keyPrefix.${k}", v)
      } ++
        newConf.toSeq.flatMap { case (k, v) =>
          appliedConf.get(k) match {
            case Some(appliedV) if appliedV != v => Seq(ConfigChange.ChangedConfig(s"$keyPrefix.${k}", appliedV, v))
            case None                            => Seq(ConfigChange.NewConfig(s"$keyPrefix.${k}", v))
            case _                               => Seq.empty
          }
        }
  }

object ServiceConfigsService {

  case class KeyName(asString: String) extends AnyVal
  case class ServiceName(asString: String) extends AnyVal

  trait ConfigEnvironment { def asString: String; def displayString: String }
  object ConfigEnvironment {
    case object Local                           extends ConfigEnvironment {
      override def asString      = "local"
      override def displayString = "Local"
    }
    case class ForEnvironment(env: Environment) extends ConfigEnvironment {
      override def asString      = env.asString
      override def displayString = env.displayString
    }

    val values: List[ConfigEnvironment] =
      Local :: Environment.values.map(ForEnvironment.apply)

    val reads: Reads[ConfigEnvironment] =
      new Reads[ConfigEnvironment] {
        override def reads(json: JsValue) =
          json
            .validate[String]
            .flatMap {
              case "local" => JsSuccess(ConfigEnvironment.Local)
              case s       => Environment.parse(s) match {
                                case Some(env) => JsSuccess(ForEnvironment(env))
                                case None      => JsError(__, s"Invalid Environment '$s'")
                              }
            }
      }
  }

  type ConfigByEnvironment = Map[ConfigEnvironment, Seq[ConfigSourceEntries]]

  object ConfigByEnvironment {
    val reads: Reads[ConfigByEnvironment] = {
      implicit val cer  = ConfigEnvironment.reads
      implicit val cser = ConfigSourceEntries.reads
      Reads
        .of[Map[String, Seq[ConfigSourceEntries]]]
        .map(_.map { case (k, v) => (JsString(k).as[ConfigEnvironment], v) })
    }
  }

  case class ConfigSourceEntries(
    source   : String,
    sourceUrl: Option[String],
    entries  : Map[KeyName, String],

  )

  object ConfigSourceEntries {
    val reads: Reads[ConfigSourceEntries] =
      ( (__ \ "source"   ).read[String]
      ~ (__ \ "sourceUrl").readNullable[String]
      ~ (__ \ "entries"  ).read[Map[String, String]].map(_.map { case (k, v) => KeyName(k) -> v }.toMap)
      )(ConfigSourceEntries.apply _)
  }

  case class ConfigSourceValue(
    source   : String,
    sourceUrl: Option[String],
    value    : String
  ){
    val isReferenceConf: Boolean =
      source == "referenceConf"

    val isSuppressed: Boolean =
      value == "<<SUPPRESSED>>"

    val displayString: String =
      if      (source.trim.isEmpty) "<<DELETED>>"
      else if (value.trim.isEmpty ) "<<BLANK>>"
      else                          value
  }

  object ConfigSourceValue {
    val reads: Reads[ConfigSourceValue] =
      ( (__ \ "source"   ).read[String]
      ~ (__ \ "sourceUrl").readNullable[String]
      ~ (__ \ "value"    ).read[String]
      )(ConfigSourceValue.apply _)
  }

  case class ServiceRelationships(
    inboundServices : Seq[String],
    outboundServices: Seq[String]
  )

  object ServiceRelationships {
    val reads: Reads[ServiceRelationships] =
      ( (__ \ "inboundServices" ).read[Seq[String]]
      ~ (__ \ "outboundServices").read[Seq[String]]
      )(ServiceRelationships.apply _)
  }

  case class ServiceRelationship(
    service        : String,
    hasRepo        : Boolean,
    lifecycleStatus: Option[LifecycleStatus]
  )

  case class ServiceRelationshipsEnriched(
    inboundServices : Seq[ServiceRelationship],
    outboundServices: Seq[ServiceRelationship]
  ) {
    def size: Int = Seq(inboundServices.size, outboundServices.size).max

    def hasDecommissioningDownstream: Boolean =
      outboundServices
        .exists(_.lifecycleStatus.contains(LifecycleStatus.DecommissionInProgress))
  }

  def friendlySourceName(
    source     : String,
    environment: ConfigEnvironment,
    key        : Option[KeyName]
  ): String =
    source match {
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
    }

  def warningDescription(
    warningType: String
  ): String =
    warningType match {
      case "NotOverriding"       => "Config doesn't appear to override any existing key"
      case "TypeChange"          => "Config value overrides a value with different type"
      case "Localhost"           => "Use of localhost in value"
      case "Debug"               => "Use of Debug log level. This should only be enabled briefly in Production"
      case "TestOnlyRoutes"      => "Use of test only routes. This should not be enabled in Production"
      case "ReactiveMongoConfig" => "Use of obsolete reactivemongo config"
      case "Unencrypted"         => "Value looks like it should be encrypted"
      case _                     => ""
    }

  case class AppliedConfig(
    serviceName  : ServiceName
  , key          : KeyName
  , environments : Map[Environment, ConfigSourceValue]
  )

  object AppliedConfig {
    val reads: Reads[AppliedConfig] = {
     implicit val readsV = ConfigSourceValue.reads
     implicit val readsEnvMap: Reads[Map[Environment, ConfigSourceValue]] =
        Reads
          .of[Map[String, ConfigSourceValue]]
          .map(_.map { case (k, v) => (Environment.parse(k).getOrElse(sys.error(s"Invalid Environment: $k")), v) })

      ( (__ \ "serviceName"  ).read[String].map(ServiceName.apply)
      ~ (__ \ "key"          ).read[String].map(KeyName.apply)
      ~ (__ \ "environments" ).read[Map[Environment, ConfigSourceValue]]
      )(AppliedConfig.apply _)
    }
  }

  case class ConfigWarning(
    serviceName: ServiceName
  , environment: Environment
  , key        : KeyName
  , value      : ConfigSourceValue
  , warning    : String
  )

  object ConfigWarning {
    val reads: Reads[ConfigWarning] = {
      implicit val readVal = ConfigSourceValue.reads
      implicit val readEnv = Environment.format
      ( (__ \ "serviceName").read[String].map(ServiceName.apply)
      ~ (__ \ "environment").read[Environment]
      ~ (__ \ "key"        ).read[String].map(KeyName.apply)
      ~ (__ \ "value"      ).read[ConfigSourceValue]
      ~ (__ \ "warning"    ).read[String]
      )(ConfigWarning.apply _)
    }
  }
}

sealed trait ConfigChange { def k: String }
object ConfigChange {
  case class NewConfig(k: String, v: String)                           extends ConfigChange
  case class DeletedConfig(k: String, previousV: String)               extends ConfigChange
  case class ChangedConfig(k: String, previousV: String, newV: String) extends ConfigChange
}
