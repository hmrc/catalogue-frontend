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

import com.typesafe.config.{Config, ConfigFactory, ConfigValue}
import javax.inject.{Inject, Singleton}
import org.yaml.snakeyaml.Yaml
import uk.gov.hmrc.cataloguefrontend.connector.{ConfigConnector}
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.immutable.ListMap
import scala.collection.mutable

@Singleton
class ConfigService @Inject()(configConnector: ConfigConnector) {
  def serviceConfigYaml(str: String, serviceName: String)(implicit hc: HeaderCarrier) = configConnector.serviceConfigYaml(str, serviceName)

  def serviceConfigConf(str: String, serviceName: String)(implicit hc: HeaderCarrier) = configConnector.serviceConfigConf(str, serviceName)


  def loadConfResponseToMap(responseString: String): scala.collection.mutable.Map[String, Object] = {
    import scala.collection.mutable.Map
    import scala.collection.JavaConversions.mapAsScalaMap
    responseString match {
      case s: String if s.nonEmpty => {
        val conf: Config = ConfigFactory.parseString(responseString)
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


  private def checkSingleMapForValue(key: String, value: String, toCheck: mutable.Map[String, Object], mapName: String) = {
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
        case e: java.util.AbstractMap.SimpleImmutableEntry[Object, Object] => start.put(e.getKey.toString, ConfigEntry(e.getValue.toString))
        case e =>
      }
    start
  }

  def flattenYamlToDotNotation(start: mutable.Map[String, Object], input: mutable.Map[String, Object], prefix: String = ""): mutable.Map[String, Object] = {
    import scala.collection.JavaConversions.mapAsScalaMap
    input foreach {
      case (k: String, v: mutable.Map[String, Object]) => flattenYamlToDotNotation(start, v, if(prefix.isEmpty) {k} else {s"$prefix.$k"})
      case (k: String, v: java.util.LinkedHashMap[String, Object]) => flattenYamlToDotNotation(start, v, if(prefix.isEmpty) {k} else {s"$prefix.$k"})
      case (k: String, v: Object) => start.put(if(prefix.isEmpty) {k} else {s"$prefix.$k"}, ConfigEntry(v.toString))
    }
    start
  }

  def convertAllMapsToImmutable(input: mutable.Map[String, mutable.Map[String, Object]]): Map[String, Map[String, Object]] = {
    var result = Map[String, Map[String, Object]]()
    for ((k: String, v: mutable.Map[String, Object]) <- input) {
      result += (k -> convertSingleMapToImmutable(v))
    }
    ListMap(result.toSeq.sortWith(_._1 < _._1):_*)
  }

  private def convertSingleMapToImmutable(input: mutable.Map[String, Object]): Map[String, Object] = {
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
