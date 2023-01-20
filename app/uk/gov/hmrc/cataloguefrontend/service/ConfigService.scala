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

package uk.gov.hmrc.cataloguefrontend.service

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.cataloguefrontend.connector.{ConfigConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.ConfigService.ArtifactNameResult.{ArtifactNameError, ArtifactNameFound, ArtifactNameNotFound}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

class ConfigService @Inject()(
  configConnector: ConfigConnector,
  teamsAndReposConnector: TeamsAndRepositoriesConnector
)(implicit
  ec: ExecutionContext
) {
  import ConfigService._

  def configByEnvironment(serviceName: String)(implicit hc: HeaderCarrier): Future[ConfigByEnvironment] =
    configConnector.configByEnv(serviceName)

  def configByKey(serviceName: String)(implicit hc: HeaderCarrier): Future[ConfigByKey] =
    configConnector.configByKey(serviceName)

  def findArtifactName(serviceName: String)(implicit hc: HeaderCarrier): Future[ArtifactNameResult] =
    configByKey(serviceName)
      .map(
        _.getOrElse("artifact_name", Map.empty)
          .mapValues(_.headOption.map(_.value))
          .values
          .flatten
          .groupBy(identity)
          .keys
          .toList
      )
      .map {
        case Nil                 => ArtifactNameNotFound
        case artifactName :: Nil => ArtifactNameFound(artifactName)
        case list                =>
          ArtifactNameError(s"Different artifact names found for service in different environments - [${list.mkString(",")}]")
      }

  def serviceRelationships(serviceName: String)(implicit hc: HeaderCarrier): Future[ServiceRelationshipsWithHasRepo] =
    for {
      repos    <- teamsAndReposConnector.allRepositories()
      srs      <- configConnector.serviceRelationships(serviceName)
      inbound  =  srs.inboundServices.sorted.map(s => if(repos.exists(_.name == s)) (s, true) else (s, false))
      outbound =  srs.outboundServices.sorted.map(s => if(repos.exists(_.name == s)) (s, true) else (s, false))
    } yield ServiceRelationshipsWithHasRepo(inbound, outbound)
}

@Singleton
object ConfigService {

  type KeyName = String

  trait ConfigEnvironment { def asString: String }
  object ConfigEnvironment {
    case object Local                           extends ConfigEnvironment { override def asString = "local"      }
    case class ForEnvironment(env: Environment) extends ConfigEnvironment { override def asString = env.asString }

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

  type ConfigByKey = Map[KeyName, Map[ConfigEnvironment, Seq[ConfigSourceValue]]]

  object ConfigByKey {
    val reads: Reads[ConfigByKey] = {
      implicit val cer  = ConfigEnvironment.reads
      implicit val csvf = ConfigSourceValue.reads
      Reads
        .of[Map[KeyName, Map[String, Seq[ConfigSourceValue]]]]
        .map(_.mapValues(_.map { case (k, v) => (JsString(k).as[ConfigEnvironment], v) }).toMap)
    }
  }

  case class ConfigSourceEntries(
    source : String,
    entries: Map[KeyName, String]
  )

  object ConfigSourceEntries {
    val reads =
      ( (__ \ "source" ).read[String]
      ~ (__ \ "entries").read[Map[KeyName, String]]
      )(ConfigSourceEntries.apply _)
  }

  case class ConfigSourceValue(
    source: String,
    value : String
  ){
    def isSuppressed: Boolean =
      value == "<<SUPPRESSED>>"
  }

  object ConfigSourceValue {
    val reads =
      ( (__ \ "source").read[String]
      ~ (__ \ "value" ).read[String]
      )(ConfigSourceValue.apply _)
  }

  case class ServiceRelationships(
    inboundServices : Seq[String],
    outboundServices: Seq[String]
  )

  object ServiceRelationships {
    val reads =
      ( (__ \ "inboundServices" ).read[Seq[String]]
      ~ (__ \ "outboundServices").read[Seq[String]]
    )(ServiceRelationships.apply _)
  }

  case class ServiceRelationshipsWithHasRepo(
    inboundServices : Seq[(String, Boolean)],
    outboundServices: Seq[(String, Boolean)]
  ) {
    def size: Int = Seq(inboundServices.size, outboundServices.size).max
  }

  def friendlySourceName(
    source     : String,
    environment: ConfigEnvironment,
    key        : Option[String]
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
      case "base64"                     => s"Base64 (decoded from config ${key.getOrElse("'key'")}.base64)"
      case _                            => source
    }

  sealed trait ArtifactNameResult

  object ArtifactNameResult {
    case class ArtifactNameFound(name: String)  extends ArtifactNameResult
    case object ArtifactNameNotFound            extends ArtifactNameResult
    case class ArtifactNameError(error: String) extends ArtifactNameResult
  }
}
