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
import scala.concurrent.Future

class ConfigService @Inject()(configConnector: ConfigConnector, configParser: ConfigParser) extends ConfigJson {
  import ConfigService._

  def configByEnvironment(serviceName: String)(implicit hc: HeaderCarrier): Future[ConfigByEnvironment] =
    configConnector.configByEnv(serviceName)

  def configByKey(serviceName: String)(implicit hc: HeaderCarrier) =
    configConnector.configByKey(serviceName)

}


@Singleton
object ConfigService {

  case class ConfigEntry(value: String)
  case class ConfigByKeyEntry(environment: String, configSource: String, value: String)

  type ConfigByEnvironment = Map[String, Map[String, Map[String, ConfigEntry]]]
  type ConfigByKey         = Map[String, List[ConfigByKeyEntry]]

  val environments: Seq[String] =
    Seq("local", "development", "qa", "staging", "integration", "externaltest", "production")
  val sourcePrecedence: Seq[String] =
    Seq("applicationConf", "baseConfig", "appConfigCommonOverridable", "appConfig", "appConfigCommonFixed")

  def sortBySourcePrecedence(entries: List[ConfigByKeyEntry]): Seq[ConfigByKeyEntry] =
    entries.sortWith((a, b) => sourcePrecedence.indexOf(a.configSource) < sourcePrecedence.indexOf(b.configSource))

  def filterForEnv(env: String, entries: List[ConfigByKeyEntry]) =
    entries.filter(e => List(env.toLowerCase, "internal").contains(e.environment))
}
