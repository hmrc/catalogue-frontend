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

import scala.collection.mutable
import scala.concurrent.Future

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

class ConfigService @Inject()(configConnector: ConfigConnector, configParser: ConfigParser){
  import ConfigService._

  def buildConfigMap(serviceName: String)(implicit hc: HeaderCarrier): Future[ConfigMap] =
    allConfigs.foldLeft(Future.successful(newConfigMap)) { case (mapF, (env, source)) =>
      mapF flatMap { map => source.get(configConnector, configParser)(serviceName, env, map) }
    }

  //TODO: Wire checkDuplicates back in
  def checkDuplicates(mapOfMaps: mutable.Map[String, mutable.Map[String, Object]]): mutable.Map[String, mutable.Map[String, Object]] = {
    mapOfMaps foreach {
      case (mapName: String, valueMap: Object) => {
        valueMap foreach {
          case (ke: String, ce: ConfigEntry) => {
            mapOfMaps.filter(_._1 != mapName).map { m => checkSingleMapForValue(ke, ce.value, m._2, mapName)
            }
          }
          case _ => println("Ooooppss! That shouldn't happen!")
        }
      }
    }
    mapOfMaps
  }

  def checkSingleMapForValue(key: String, value: String, toCheck: mutable.Map[String, Object], mapName: String) = {
    toCheck.get(key) match {
      case Some(ev: ConfigEntry) if ev.value.toString == value => {
        val newValue = ev.copy(repeats = ev.repeats :+ mapName)
        toCheck.put(key, newValue)
      }
      case _ =>
    }
    toCheck
  }

}

@Singleton
object ConfigService {
  type EnvironmentConfigSource = (Environment, ConfigSource)
  type ConfigMap = Map[EnvironmentConfigSource, Map[String, Object]]

  val allConfigs = Seq(Service, Base, Development, Qa, Staging, Integration, ExternalTest, Production).flatMap(c => c.configs)
  val environments = Seq(Development, Qa, Staging, Integration, ExternalTest, Production)

  sealed trait Environment {
    def name: String
    def configs: Seq[EnvironmentConfigSource]
  }

  case object Service extends Environment {
    val name = "internal"
    val configs = Seq((Service, ApplicationConf))
  }

  case object Base extends Environment {
    val name = "base"
    val configs = Seq((Base, BaseConfig))
  }

  case object Development extends Environment {
    val name = "development"
    val configs =
      Seq((Development, AppConfig), (Development, AppConfigCommonFixed), (Development, AppConfigCommonOverridable))
  }

  case object Qa extends Environment {
    val name = "qa"
    val configs = Seq((Qa, AppConfig), (Qa, AppConfigCommonFixed), (Qa, AppConfigCommonOverridable))
  }

  case object Staging extends Environment {
    val name = "staging"
    val configs = Seq((Staging, AppConfig), (Staging, AppConfigCommonFixed), (Staging, AppConfigCommonOverridable))
  }

  case object Integration extends Environment {
    val name = "integration"
    val configs =
      Seq((Integration, AppConfig), (Integration, AppConfigCommonFixed), (Integration, AppConfigCommonOverridable))
  }

  case object ExternalTest extends Environment {
    val name = "externaltest"
    val configs =
      Seq((ExternalTest, AppConfig), (ExternalTest, AppConfigCommonFixed), (ExternalTest, AppConfigCommonOverridable))
  }

  case object Production extends Environment {
    val name = "production"
    val configs =
      Seq((Production, AppConfig), (Production, AppConfigCommonFixed), (Production, AppConfigCommonOverridable))
  }

  sealed trait ConfigSource {
    def get(connector: ConfigConnector, parser: ConfigParser)(serviceName: String, env: Environment, map: ConfigMap)(implicit hc: HeaderCarrier): Future[ConfigMap]
  }

  case object ApplicationConf extends ConfigSource {
    def get(connector: ConfigConnector, parser: ConfigParser)(serviceName: String, env: Environment, map: ConfigMap)(implicit hc: HeaderCarrier) =
      connector.serviceApplicationConfigFile(serviceName)
        .map(raw => map + ((env, this) -> parser.loadConfResponseToMap(raw).toMap))
  }

  case object BaseConfig extends ConfigSource {
    def get(connector: ConfigConnector, parser: ConfigParser)(serviceName: String, env: Environment, map: ConfigMap)(implicit hc: HeaderCarrier) =
      connector.serviceConfigConf(env.name, serviceName)
        .map(raw => map + ((env, this) -> parser.loadConfResponseToMap(raw).toMap))
  }

  case object AppConfig extends ConfigSource {
    def get(connector: ConfigConnector, parser: ConfigParser)(serviceName: String, env: Environment, map: ConfigMap)(implicit hc: HeaderCarrier) =
      connector.serviceConfigYaml(env.name, serviceName)
        .map(raw => map + ((env, this) -> parser.loadYamlResponseToMap(raw).toMap))
  }

  case object AppConfigCommonFixed extends ConfigSource {
    def get(connector: ConfigConnector, parser: ConfigParser)(serviceName: String, env: Environment, map: ConfigMap)(implicit hc: HeaderCarrier) =
      for (entries <- getServiceType(map, env) match {
        case Some(serviceType) =>
          connector.serviceCommonConfigYaml(env.name, serviceType).map(raw => parser.loadYamlResponseToMap(raw).toMap)
        case None => Future.successful(Map[String, Object]())
      }) yield map + ((env, this) -> entries)
  }

  case object AppConfigCommonOverridable extends ConfigSource {
    def get(connector: ConfigConnector, parser: ConfigParser)(serviceName: String, env: Environment, map: ConfigMap)(implicit hc: HeaderCarrier) =
      for (entries <- getServiceType(map, env) match {
        case Some(serviceType) =>
          connector.serviceCommonConfigYaml(env.name, serviceType).map(raw => parser.loadYamlResponseToMap(raw).toMap)
        case None => Future.successful(Map[String, Object]())
      }) yield map + ((env, this) -> entries)
  }

  def newConfigMap = Map[EnvironmentConfigSource, Map[String, Object]]()

  def getServiceType(acc: ConfigMap, env: Environment): Option[String] =
    acc((env, AppConfig))
      .get("type")
      .map(t => t.asInstanceOf[ConfigEntry].value)
}

case class ConfigEntry(value: String, repeats: List[String] = List())
