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

import java.util

import com.typesafe.config._
import javax.inject.{Inject, Singleton}
import org.yaml.snakeyaml.Yaml
import uk.gov.hmrc.cataloguefrontend.connector.ConfigConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.immutable.ListMap
import scala.collection.mutable

@Singleton
class ConfigService @Inject()(configConnector: ConfigConnector) {


  def configFromYaml(env: String, serviceName: String)(implicit hc: HeaderCarrier) = configConnector.serviceConfigYaml(env, serviceName)

  def configFromConfFile(env: String, serviceName: String)(implicit hc: HeaderCarrier) = configConnector.serviceConfigConf(env, serviceName)

  def commonConfigFromYaml(env: String, serviceType: String)(implicit hc: HeaderCarrier) = configConnector.serviceCommonConfigYaml(env, serviceType)

  def internalConfigFromConfFile(serviceName: String)(implicit hc: HeaderCarrier) = configConnector.internalConfigFile(serviceName)

  def buildConfigMap(internalConfig: String,
                     baseConfig: String,
                     devConfig: String,
                     devCommonConfig: String,
                     qaConfig: String,
                     qaCommonConfig: String,
                     stagingConfig: String,
                     stagingCommonConfig: String,
                     integrationConfig: String,
                     integrationCommonConfig: String,
                     externalTestConfig: String,
                     externalTestCommonConfig: String,
                     productionConfig: String,
                     productionCommonConfig: String) = {

    val resultMap = scala.collection.mutable.Map[String, scala.collection.mutable.Map[String, Object]](
      "internal" -> loadConfResponseToMap(internalConfig),
      "base" -> loadConfResponseToMap(baseConfig),
      "development" -> loadYamlResponseToMap(devConfig),
      "developmentCommon" -> loadYamlResponseToMap(devCommonConfig),
      "qa" -> loadYamlResponseToMap(qaConfig),
      "qaCommon" -> loadYamlResponseToMap(qaCommonConfig),
      "staging" -> loadYamlResponseToMap(stagingConfig),
      "stagingCommon" -> loadYamlResponseToMap(stagingCommonConfig),
      "integration" -> loadYamlResponseToMap(integrationConfig),
      "integrationCommon" -> loadYamlResponseToMap(integrationCommonConfig),
      "externalTest" -> loadYamlResponseToMap(externalTestConfig),
      "externalTestCommon" -> loadYamlResponseToMap(externalTestCommonConfig),
      "production" -> loadYamlResponseToMap(productionConfig),
      "productionCommon" -> loadYamlResponseToMap(productionCommonConfig)
    )

    convertAllMapsToImmutable(checkDuplicates(resultMap))
  }

  def loadConfResponseToMap(responseString: String): scala.collection.mutable.Map[String, Object] = {
    import scala.collection.mutable.Map
    import scala.collection.JavaConversions.mapAsScalaMap

    val fallbackIncluder = ConfigParseOptions.defaults().getIncluder()

    val doNotInclude = new ConfigIncluder() {
      override def withFallback(fallback: ConfigIncluder): ConfigIncluder = this
      override def include(context: ConfigIncludeContext, what: String): ConfigObject = {
//        ConfigFactory.parseString(what).root()
        ConfigFactory.empty.root()
      }
    }

    val options: ConfigParseOptions = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF).setAllowMissing(false).setIncluder(doNotInclude)
    responseString match {
      case s: String if s.nonEmpty => {
        val conf: Config = ConfigFactory.parseString(responseString, options)
        flattenConfigToDotNotation(Map(), conf)
      }
      case _ => Map()
    }
  }

  def loadYamlResponseToMap(responseString: String): scala.collection.mutable.Map[String, Object] = {
    import scala.collection.mutable.Map
    import scala.collection.JavaConversions.mapAsScalaMap
    responseString match {
      case s: String if s.nonEmpty => {
        val yamlMap: Map[String, Object] = new Yaml().load(responseString).asInstanceOf[util.LinkedHashMap[String, Object]]
        flattenYamlToDotNotation(Map(), yamlMap)
      }
      case _ => Map()
    }
  }

  def checkDuplicates(mapOfMaps: mutable.Map[String, mutable.Map[String, Object]]): mutable.Map[String, mutable.Map[String, Object]] = {
    mapOfMaps foreach {
      case (mapName: String, valueMap: Object) => {
        valueMap foreach {
          case (ke: String, ce: ConfigEntry) => {
            mapOfMaps.filter(_._1 != mapName).map { m =>
              checkSingleMapForValue(ke, ce.value, m._2, mapName)
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

  def flattenConfigToDotNotation(start: mutable.Map[String, Object], input: Config, prefix: String = ""): mutable.Map[String, Object] = {
    import scala.collection.JavaConversions._
    input.entrySet().toArray().foreach {
        case e: java.util.AbstractMap.SimpleImmutableEntry[Object, com.typesafe.config.ConfigValue] => start.put(s"hmrc_config.${e.getKey.toString}", ConfigEntry(removeQuotes(e.getValue.render)))
        case e => println("Can't do that!")
      }
    start
  }

  def removeQuotes(input: String) = {
    if(input.charAt(0).equals('"') && input.charAt(input.length - 1).equals('"')) {
      input.substring(1, input.length - 1)
    } else {
      input
    }
  }

  def flattenYamlToDotNotation(start: mutable.Map[String, Object], input: mutable.Map[String, Object], currentPrefix: String = ""): mutable.Map[String, Object] = {
    import scala.collection.JavaConversions.mapAsScalaMap
    input foreach {
      case (k: String, v: mutable.Map[String, Object]) => flattenYamlToDotNotation(start, v, buildPrefix(currentPrefix, k))
      case (k: String, v: java.util.LinkedHashMap[String, Object]) => flattenYamlToDotNotation(start, v, buildPrefix(currentPrefix, k))
      case (k: String, v: Object) => start.put(buildPrefix(currentPrefix, k), ConfigEntry(v.toString))
    }
    start
  }


  private def buildPrefix(currentPrefix: String, key: String) = {
    (currentPrefix, key) match {
      case ("", "0.0.0") => currentPrefix  // filter out the (unused) config version numbering
      case (cp, _) if cp.isEmpty => key
      case _ => s"$currentPrefix.$key"
    }
  }

  def convertAllMapsToImmutable(input: mutable.Map[String, mutable.Map[String, Object]]): Map[String, Map[String, Object]] = {
    var result = Map[String, Map[String, Object]]()
    for ((k: String, v: mutable.Map[String, Object]) <- input) {
      result += (k -> convertSingleMapToImmutable(v))
    }
    ListMap(result.toSeq.sortWith(_._1 < _._1):_*)
  }

  def convertSingleMapToImmutable(input: mutable.Map[String, Object]): Map[String, Object] = {
    import scala.collection.JavaConversions.mapAsScalaMap

    var result = Map[String, Object]()
    for((k: String, v: Object) <- input) {
      (k, v) match {
        case (k: String, v: java.util.Map[String, Object]) => result += k -> convertSingleMapToImmutable(v)
        case (_, _) => result += k -> v
      }
    }
    ListMap(result.toSeq.sortWith(_._1 < _._1):_*)
  }


}


case class ConfigEntry(value: String, repeats: List[String] = List())
