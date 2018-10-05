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
import javax.inject.Singleton
import org.yaml.snakeyaml.Yaml
import uk.gov.hmrc.cataloguefrontend.service.ConfigService.ConfigEntry

import scala.collection.mutable

@Singleton
class ConfigParser {

  def loadConfResponseToMap(responseString: String): scala.collection.mutable.Map[String, ConfigEntry] = {
    import scala.collection.mutable.Map

    val fallbackIncluder = ConfigParseOptions.defaults().getIncluder()

    val doNotInclude = new ConfigIncluder() {
      override def withFallback(fallback: ConfigIncluder): ConfigIncluder             = this
      override def include(context: ConfigIncludeContext, what: String): ConfigObject =
        //        ConfigFactory.parseString(what).root()
        ConfigFactory.empty.root()
    }

    val options: ConfigParseOptions =
      ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF).setAllowMissing(false).setIncluder(doNotInclude)
    responseString match {
      case s: String if s.nonEmpty => {
        val conf: Config = ConfigFactory.parseString(responseString, options)
        flattenConfigToDotNotation(Map(), conf)
      }
      case _ => Map()
    }
  }

  def loadYamlResponseToMap(responseString: String): scala.collection.mutable.Map[String, ConfigEntry] = {
    import scala.collection.JavaConversions.mapAsScalaMap
    import scala.collection.mutable.Map
    responseString match {
      case s: String if s.nonEmpty => {
        val yamlMap: Map[String, Object] =
          new Yaml().load(responseString).asInstanceOf[util.LinkedHashMap[String, Object]]
        flattenYamlToDotNotation(Map(), yamlMap)
      }
      case _ => Map()
    }
  }

  private def flattenConfigToDotNotation(
    start: mutable.Map[String, ConfigEntry],
    input: Config,
    prefix: String = ""): mutable.Map[String, ConfigEntry] = {
    input.entrySet().toArray().foreach {
      case e: java.util.AbstractMap.SimpleImmutableEntry[Object, com.typesafe.config.ConfigValue] =>
        start.put(s"${e.getKey.toString}", ConfigEntry(removeQuotes(e.getValue.render)))
      case e => println("Can't do that!")
    }
    start
  }

  private def removeQuotes(input: String) =
    if (input.charAt(0).equals('"') && input.charAt(input.length - 1).equals('"')) {
      input.substring(1, input.length - 1)
    } else {
      input
    }

  private def flattenYamlToDotNotation(
    start: mutable.Map[String, ConfigEntry],
    input: mutable.Map[String, Object],
    currentPrefix: String = ""): mutable.Map[String, ConfigEntry] = {
    import scala.collection.JavaConversions.mapAsScalaMap
    input foreach {
      case (k: String, v: mutable.Map[String, Object]) =>
        flattenYamlToDotNotation(start, v, buildPrefix(currentPrefix, k))
      case (k: String, v: java.util.LinkedHashMap[String, Object]) =>
        flattenYamlToDotNotation(start, v, buildPrefix(currentPrefix, k))
      case (k: String, v: Object) => start.put(buildPrefix(currentPrefix, k), ConfigEntry(v.toString))
    }
    start
  }

  private def buildPrefix(currentPrefix: String, key: String) =
    (currentPrefix, key) match {
      case ("", "0.0.0")         => currentPrefix // filter out the (unused) config version numbering
      case (cp, _) if cp.isEmpty => key
      case _                     => s"$currentPrefix.$key"
    }
}
