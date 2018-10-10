/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.cataloguefrontend.service

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.connector.ConfigConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.collection.SortedMap
import scala.concurrent.Future

class ConfigService @Inject()(configConnector: ConfigConnector, configParser: ConfigParser) {
  import ConfigService._

  def configByEnvironment(serviceName: String)(implicit hc: HeaderCarrier): Future[ConfigByEnvironment] =
    allConfigs.foldLeft(Future.successful(newConfigMap)) {
      case (mapF, (env, source)) =>
        mapF flatMap { map => source.get(configConnector, configParser)(serviceName, env, map)
        }
    }

  def configByKey(map: ConfigByEnvironment) =
    map.foldLeft(SortedMap[String, Map[Environment, ConfigSourceValueMap]]()) {
      case (acc, ((env, src), keyValues)) =>
        acc ++ (keyValues map {
          case (key, entry) =>
            val keyMap = acc.getOrElse(key, newEnvironmentValueMap)
            key -> (keyMap + (env -> (keyMap(env) + (src -> entry.value))))
        })
    }
}

@Singleton
object ConfigService {
  type EnvironmentConfigSource = (Environment, ConfigSource)
  type ConfigSourceValueMap    = Map[ConfigSource, String]
  type ConfigByEnvironment     = Map[EnvironmentConfigSource, Map[String, ConfigEntry]]
  type ConfigByKey             = SortedMap[String, Map[Environment, ConfigSourceValueMap]]

  val sourcePrecedence: Seq[ConfigSource] =
    Seq(ApplicationConf, BaseConfig, AppConfigCommonOverridable, AppConfig, AppConfigCommonFixed)
  val environments: Seq[Environment] = Seq(Local, Development, Qa, Staging, Integration, ExternalTest, Production)
  val allConfigs: Seq[EnvironmentConfigSource] =
    Seq(Local, Development, Qa, Staging, Integration, ExternalTest, Production)
      .flatMap(env => env.sources.map(c => env -> c))

  case class ConfigEntry(value: String)

  sealed trait Environment {
    def name: String
    def sources: Seq[ConfigSource]
  }

  case object Local extends Environment {
    val name    = "local"
    val sources = Seq(ApplicationConf)
  }

  case object Development extends Environment {
    val name    = "development"
    val sources = Seq(ApplicationConf, BaseConfig, AppConfig, AppConfigCommonFixed, AppConfigCommonOverridable)
  }

  case object Qa extends Environment {
    val name    = "qa"
    val sources = Seq(ApplicationConf, BaseConfig, AppConfig, AppConfigCommonFixed, AppConfigCommonOverridable)
  }

  case object Staging extends Environment {
    val name    = "staging"
    val sources = Seq(ApplicationConf, BaseConfig, AppConfig, AppConfigCommonFixed, AppConfigCommonOverridable)
  }

  case object Integration extends Environment {
    val name    = "integration"
    val sources = Seq(ApplicationConf, BaseConfig, AppConfig, AppConfigCommonFixed, AppConfigCommonOverridable)
  }

  case object ExternalTest extends Environment {
    val name    = "externaltest"
    val sources = Seq(ApplicationConf, BaseConfig, AppConfig, AppConfigCommonFixed, AppConfigCommonOverridable)
  }

  case object Production extends Environment {
    val name    = "production"
    val sources = Seq(ApplicationConf, BaseConfig, AppConfig, AppConfigCommonFixed, AppConfigCommonOverridable)
  }

  sealed trait ConfigSource {
    def get(connector: ConfigConnector, parser: ConfigParser)(
      serviceName: String,
      env: Environment,
      map: ConfigByEnvironment)(implicit hc: HeaderCarrier): Future[ConfigByEnvironment]
  }

  case object ApplicationConf extends ConfigSource {
    def get(connector: ConfigConnector, parser: ConfigParser)(
      serviceName: String,
      env: Environment,
      map: ConfigByEnvironment)(implicit hc: HeaderCarrier) =
      connector
        .serviceApplicationConfigFile(serviceName)
        .map(raw => map + ((env, this) -> parser.loadConfResponseToMap(raw).toMap))
  }

  case object BaseConfig extends ConfigSource {
    def get(connector: ConfigConnector, parser: ConfigParser)(
      serviceName: String,
      env: Environment,
      map: ConfigByEnvironment)(implicit hc: HeaderCarrier) =
      connector
        .serviceConfigConf(env.name, serviceName)
        .map(raw => map + ((env, this) -> parser.loadConfResponseToMap(raw).toMap))
  }

  case object AppConfig extends ConfigSource {
    def get(connector: ConfigConnector, parser: ConfigParser)(
      serviceName: String,
      env: Environment,
      map: ConfigByEnvironment)(implicit hc: HeaderCarrier) =
      connector
        .serviceConfigYaml(env.name, serviceName)
        .map { raw =>
          map + ((env, this) -> parser
            .loadYamlResponseToMap(raw)
            .map { case (k, v) => k.replace("hmrc_config.", "") -> v }
            .toMap)
        }
  }

  case object AppConfigCommonFixed extends ConfigSource {
    def get(connector: ConfigConnector, parser: ConfigParser)(
      serviceName: String,
      env: Environment,
      map: ConfigByEnvironment)(implicit hc: HeaderCarrier) =
      for (entries <- getServiceType(map, env) match {
                       case Some(serviceType) =>
                         connector.serviceCommonConfigYaml(env.name, serviceType).map { raw =>
                           parser
                             .loadYamlResponseToMap(raw)
                             .filterKeys(key => key.startsWith("hmrc_config.fixed"))
                             .map { case (k, v) => k.replace("hmrc_config.fixed.", "") -> v }
                             .toMap
                         }
                       case None => Future.successful(Map[String, ConfigEntry]())
                     }) yield map + ((env, this) -> entries)
  }

  case object AppConfigCommonOverridable extends ConfigSource {
    def get(connector: ConfigConnector, parser: ConfigParser)(
      serviceName: String,
      env: Environment,
      map: ConfigByEnvironment)(implicit hc: HeaderCarrier) =
      for (entries <- getServiceType(map, env) match {
                       case Some(serviceType) =>
                         connector.serviceCommonConfigYaml(env.name, serviceType).map { raw =>
                           parser
                             .loadYamlResponseToMap(raw)
                             .filterKeys(key => key.startsWith("hmrc_config.overridable"))
                             .map { case (k, v) => k.replace("hmrc_config.overridable.", "") -> v }
                             .toMap
                         }
                       case None => Future.successful(Map[String, ConfigEntry]())
                     }) yield map + ((env, this) -> entries)
  }

  def newConfigMap = Map[EnvironmentConfigSource, Map[String, ConfigEntry]]()

  def newEnvironmentValueMap: Map[Environment, Map[ConfigSource, String]] =
    environments.map(e => e -> Map[ConfigSource, String]()).toMap

  def getServiceType(map: ConfigByEnvironment, env: Environment): Option[String] =
    map((env, AppConfig))
      .get("type")
      .map(t => t.value)

  def getValueByPrecendence(configSourceValues: ConfigSourceValueMap) =
    sourcePrecedence
      .map(src => (src, configSourceValues.get(src)))
      .filter { case (_, v) => v.isDefined }
      .map { case (src, v) => (src, v.get) }
}
