/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.libs.json.Json
import uk.gov.hmrc.cataloguefrontend.connector.ConfigConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class ConfigService @Inject()(configConnector: ConfigConnector) {
  import ConfigService._

  def configByEnvironment(serviceName: String)(implicit hc: HeaderCarrier): Future[ConfigByEnvironment] =
    configConnector.configByEnv(serviceName)

  def configByKey(serviceName: String)(implicit hc: HeaderCarrier) =
    configConnector.configByKey(serviceName)
}

@Singleton
object ConfigService {
  type EnvironmentName = String
  type KeyName = String

  type ConfigByEnvironment = Map[EnvironmentName, Seq[ConfigSourceEntries]]
  type ConfigByKey = Map[KeyName, Map[EnvironmentName, Seq[ConfigSourceValue]]]

  case class ConfigSourceEntries(source: String, precedence: Int, entries: Map[KeyName, String] = Map())
  case class ConfigSourceValue(source: String, precedence: Int, value: String)

  val environments: Seq[String] = Seq("local", "development", "qa", "staging", "integration", "externaltest", "production")

  def sortBySourcePrecedence(entries: Option[Seq[ConfigSourceValue]]): Seq[ConfigSourceValue] =
    entries.map(e => e.sortWith((a, b) => a.precedence < b.precedence)).getOrElse(Seq())

  def friendlySourceName(source: String, environment: String): String = {
    source match {
      case "referenceConf"              => "Microservice reference.conf files"
      case "applicationConf"            => "Microservice application.conf file"
      case "baseConfig"                 => "App-config-base"
      case "appConfigEnvironment"       => s"App-config-$environment"
      case "appConfigCommonFixed"       => "App-config-common fixed settings"
      case "appConfigCommonOverridable" => "App-config-common overridable settings"
      case _ => source
    }
  }
}
