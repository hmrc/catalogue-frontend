/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.connector.ConfigConnector
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class ConfigService @Inject()(configConnector: ConfigConnector) {
  import ConfigService._

  def configByEnvironment(serviceName: String)(implicit hc: HeaderCarrier): Future[ConfigByEnvironment] =
    configConnector.configByEnv(serviceName)

  def configByKey(serviceName: String)(implicit hc: HeaderCarrier): Future[ConfigByKey] =
    configConnector.configByKey(serviceName)
}

@Singleton
object ConfigService {

  type KeyName = String

  trait ConfigEnvironment { def asString: String }
  object ConfigEnvironment {
    case object Local                            extends ConfigEnvironment { override def asString = "local"      }
    case class  ForEnvironment(env: Environment) extends ConfigEnvironment { override def asString = env.asString }

    val values: List[ConfigEnvironment] =
      Local :: Environment.values.map(ForEnvironment.apply)

    val reads: Reads[ConfigEnvironment] =
      new Reads[ConfigEnvironment] {
        override def reads(json: JsValue) =
          json.validate[String]
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
      Reads.of[Map[String, Seq[ConfigSourceEntries]]]
        .map(_.map { case (k , v) => (JsString(k).as[ConfigEnvironment], v) })
    }
  }

  type ConfigByKey = Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]]

  object ConfigByKey {
    val reads: Reads[ConfigByKey] = {
      implicit val cer  = ConfigEnvironment.reads
      implicit val csvf = ConfigSourceValue.reads
      Reads.of[Map[KeyName, Map[String, Seq[ConfigSourceValue]]]]
        .map(_.mapValues(_.map { case (k, v) => (JsString(k).as[ConfigEnvironment], v)}))
    }
  }

  case class ConfigSourceEntries(
      source    : String
    , precedence: Int
    , entries   : Map[KeyName, String] = Map()
    )

  object ConfigSourceEntries {
     val reads = Json.reads[ConfigSourceEntries]
  }

  case class ConfigSourceValue(
      source    : String
    , precedence: Int
    , value     : String
    )

  object ConfigSourceValue {
    val reads = Json.reads[ConfigSourceValue]
  }

  def sortBySourcePrecedence(entries: Option[Seq[ConfigSourceValue]]): Seq[ConfigSourceValue] =
    entries.map(_.sortBy(_.precedence)).getOrElse(Seq())

  def friendlySourceName(source: String, environment: ConfigEnvironment): String = {
    source match {
      case "referenceConf"              => "Microservice reference.conf files"
      case "applicationConf"            => "Microservice application.conf file"
      case "baseConfig"                 => "App-config-base"
      case "appConfigEnvironment"       => s"App-config-${environment.asString}"
      case "appConfigCommonFixed"       => "App-config-common fixed settings"
      case "appConfigCommonOverridable" => "App-config-common overridable settings"
      case _                            => source
    }
  }
}
