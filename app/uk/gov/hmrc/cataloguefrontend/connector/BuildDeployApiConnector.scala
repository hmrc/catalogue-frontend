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

package uk.gov.hmrc.cataloguefrontend.connector

import com.google.inject.{Inject, Singleton}
import play.api.Logging
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.PathBindable
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import uk.gov.hmrc.cataloguefrontend.ChangePrototypePassword.PrototypePassword
import uk.gov.hmrc.cataloguefrontend.config.BuildDeployApiConfig
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector._
import uk.gov.hmrc.cataloguefrontend.connector.model.Version
import uk.gov.hmrc.cataloguefrontend.connector.signer.AwsSigner
import uk.gov.hmrc.cataloguefrontend.createappconfigs.CreateAppConfigsRequest
import uk.gov.hmrc.cataloguefrontend.createarepository.CreateRepoForm
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}

import java.net.URL
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class BuildDeployApiConnector @Inject() (
  httpClientV2          : HttpClientV2,
  awsCredentialsProvider: AwsCredentialsProvider,
  config                : BuildDeployApiConfig
)(implicit
  ec                    : ExecutionContext
) extends Logging {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private def buildUrl(
    endpoint   : String,
    queryParams: Map[String, String]
  ): URL = url"${config.baseUrl}/v1/$endpoint?$queryParams"

  private def signedHeaders(
    uri        : String,
    queryParams: Map[String, String],
    body       : JsValue
  ): Map[String, String] =
    AwsSigner(awsCredentialsProvider, config.awsRegion, "execute-api", () => LocalDateTime.now())
      .getSignedHeaders(
        uri         = uri,
        method      = "POST",
        queryParams = queryParams,
        headers     = Map[String, String]("host" -> config.host),
        payload     = Some(Json.toBytes(body))
      )

  private def signAndExecuteRequest(
    endpoint   : String,
    body       : JsValue,
    queryParams: Map[String, String] = Map.empty[String, String],
    logBody    : Boolean = true
  ): Future[Either[String, BuildDeployResponse]] = {
    val url = buildUrl(endpoint, queryParams)

    val headers = signedHeaders(url.getPath, queryParams, body)

    logger.info(s"Calling the $url" + (if (logBody) { s" with the following payload: $body"} else { "" }))

    implicit val r: Reads[BuildDeployResponse] = BuildDeployResponse.reads

    implicit val hr: HttpReads[Either[String, BuildDeployResponse]] =
      implicitly[HttpReads[Either[UpstreamErrorResponse, BuildDeployResponse]]]
        .flatMap {
          case Right(r) =>
            HttpReads.pure(Right(r))
          case Left(UpstreamErrorResponse.Upstream4xxResponse(e)) =>
            HttpReads.ask
              .flatMap { case (method, url, response) =>
                logger.error(s"Failed to call Build and Deploy API endpoint $endpoint. response: $response: ${e.getMessage}", e)
                Try(HttpReads.pure(Left((response.json \ "message").as[String]): Either[String, BuildDeployResponse])).getOrElse(throw e)
              }
          case Left(other) => throw other
        }

    httpClientV2
      .post(url)
      .withBody(body)
      .setHeader(headers.toSeq: _*)
      .execute[Either[String, BuildDeployResponse]]
      .map { rsp => logger.info(s"$url response: $rsp"); rsp}
  }

  def changePrototypePassword(prototype: String, password: PrototypePassword): Future[Either[String, String]] = {
    val body = Json.obj(
      "repository_name" -> JsString(prototype),
      "password"        -> JsString(password.value)
    )

    signAndExecuteRequest(
      endpoint = "SetHerokuPrototypePassword",
      body     = body,
      logBody  = false
    ).map {
      case Right(response) => Right(response.message)
      case Left(errorMsg)  => Left(errorMsg)
    }
  }

  def getPrototypeDetails(prototypeName: String): Future[PrototypeDetails] = {
    implicit val pdR: Reads[PrototypeDetails] = PrototypeDetails.reads

    val body = Json.obj(
      "prototype" -> JsString(prototypeName)
    )

    signAndExecuteRequest(
      endpoint = "GetPrototypeStatus",
      body     = body
    ).map {
      case Left(errMsg) => logger.error(s"Call to GetPrototypeStatus failed with: $errMsg")
                           PrototypeDetails(
                             url    = None,
                             status = PrototypeStatus.Undetermined
                           )
      case Right(res)   => res.details.as[PrototypeDetails]
    }.recover {
      case e =>
        logger.error(s"Call GetPrototypeStatus failed with: ${e.getMessage}", e)
        PrototypeDetails(
          url    = None,
          status = PrototypeStatus.Undetermined
        )
    }
  }

  def setPrototypeStatus(prototype: String, status: PrototypeStatus): Future[Either[String, Unit]] = {
    val body = Json.obj(
      "prototype" -> JsString(prototype),
      "status"    -> JsString(status.asString)
    )

    signAndExecuteRequest(
      endpoint = "SetPrototypeStatus",
      body     = body
    ).map(_.map(_ => ()))
  }


  def createARepository(payload: CreateRepoForm): Future[Either[String, RequestState.Id]] = {
    val body =
      s"""
         |{
         |   "repository_name": "${payload.repositoryName}",
         |   "make_private": ${payload.makePrivate},
         |   "allow_auto_merge": true,
         |   "delete_branch_on_merge": true,
         |   "team_name": "${payload.teamName}",
         |   "repository_type": "${payload.repoType}",
         |   "bootstrap_tag": "",
         |   "init_webhook_version": "2.2.0",
         |   "default_branch_name": "main"
         |}""".stripMargin

    signAndExecuteRequest(
      endpoint = "CreateRepository",
      body     = Json.parse(body)
    ).map(_.map(_.details.as[RequestState.Id](RequestState.Id.reads)))
  }

  def createAppConfigs(payload: CreateAppConfigsRequest, serviceName: String, serviceType: ServiceType, requiresMongo: Boolean, isApi: Boolean): Future[Either[String, RequestState.Id]] = {
    val (st, zone) = serviceType match {
      case ServiceType.Frontend         => ( "Frontend microservice", "public"    )
      case ServiceType.Backend if isApi => ( "API microservice"     , "protected" )
      case ServiceType.Backend          => ( "Backend microservice" , "protected" )
    }

    val body =
      s"""
         |{
         |   "microservice_name": "$serviceName",
         |   "microservice_type": "$st",
         |   "microservice_requires_mongo": $requiresMongo,
         |   "app_config_base": ${payload.appConfigBase},
         |   "app_config_development": ${payload.appConfigDevelopment},
         |   "app_config_qa": ${payload.appConfigQA},
         |   "app_config_staging": ${payload.appConfigStaging},
         |   "app_config_production": ${payload.appConfigProduction},
         |   "zone": "$zone"
         |}""".stripMargin

    signAndExecuteRequest(
      endpoint = "CreateAppConfigs",
      body     = Json.parse(body)
    ).map(_.map(_.details.as[RequestState.Id](RequestState.Id.reads)))
  }

  def triggerMicroserviceDeployment(
    serviceName: String
  , environment: Environment
  , version: Version
  , slugSource: String
  , deployerId: String
  ): Future[Either[String, RequestState.EcsTask]] =
    signAndExecuteRequest(
      endpoint = "TriggerMicroserviceDeployment",
      body     = Json.obj(
                   "service"         -> JsString(serviceName)
                 , "environment"     -> JsString(environment.asString)
                 , "service_version" -> JsString(version.original)
                 , "slug_source"     -> JsString(slugSource)
                 , "deployer_id"     -> JsString(deployerId)
                 )
    ).map(_.map(_.details.as[RequestState.EcsTask](RequestState.EcsTask.reads)))

  def getRequestState[T <: RequestState](state: T): Future[Either[String, JsValue]] =
    signAndExecuteRequest(
      endpoint = "GetRequestState",
      body     = state match {
                   case s: RequestState.Id      => Json.toJson(s)(RequestState.Id.writes)
                   case s: RequestState.EcsTask => Json.toJson(s)(RequestState.EcsTask.writes)
                 }
    ).map(_.map(_.details))
}

object BuildDeployApiConnector {
  final case class BuildDeployResponse(message: String, details: JsValue)

  private object BuildDeployResponse {
    val reads: Reads[BuildDeployResponse] =
      ( (__ \ "message").read[String]
      ~ (__ \ "details").readWithDefault[JsValue](JsNull)
      )(BuildDeployResponse.apply _)
  }

  sealed trait RequestState

  object RequestState {
    final case class Id(id: String, start: Long) extends RequestState
    object Id {
      val reads: Reads[Id] =
        ( (__ \ "get_request_state_payload" \ "bnd_api_request_id"          ).read[String]
        ~ (__ \ "get_request_state_payload" \ "start_timestamp_milliseconds").read[Long]
        )(Id.apply _)

      val writes: Writes[Id] =
      ( (__ \ "bnd_api_request_id"          ).write[String]
      ~ (__ \ "start_timestamp_milliseconds").write[Long]
      )(unlift(Id.unapply))
    }

    final case class EcsTask(accountId: String, logGroupName: String, clusterName: String, logStreamNamePrefix: String, arn: String, start: Long) extends RequestState
    object EcsTask {
      val reads: Reads[EcsTask] =
        ( (__ \ "get_request_state_payload" \ "bnd_api_ecs_task" \ "account_id"            ).read[String]
        ~ (__ \ "get_request_state_payload" \ "bnd_api_ecs_task" \ "log_group_name"        ).read[String]
        ~ (__ \ "get_request_state_payload" \ "bnd_api_ecs_task" \ "cluster_name"          ).read[String]
        ~ (__ \ "get_request_state_payload" \ "bnd_api_ecs_task" \ "log_stream_name_prefix").read[String]
        ~ (__ \ "get_request_state_payload" \ "bnd_api_ecs_task" \ "arn"                   ).read[String]
        ~ (__ \ "get_request_state_payload" \ "start_timestamp_milliseconds"               ).read[Long]
        )(EcsTask.apply _)

      val writes: Writes[EcsTask] =
        ( (__ \ "bnd_api_ecs_task" \ "account_id"            ).write[String]
        ~ (__ \ "bnd_api_ecs_task" \ "log_group_name"        ).write[String]
        ~ (__ \ "bnd_api_ecs_task" \ "cluster_name"          ).write[String]
        ~ (__ \ "bnd_api_ecs_task" \ "log_stream_name_prefix").write[String]
        ~ (__ \ "bnd_api_ecs_task" \ "arn"                   ).write[String]
        ~ (__ \ "start_timestamp_milliseconds"               ).write[Long]
        )(unlift(EcsTask.unapply))
    }
  }

  sealed trait PrototypeStatus { def asString: String; def displayString: String }

  object PrototypeStatus {
    case object Running      extends PrototypeStatus { val asString = "running"      ; override def displayString = "Running"      }
    case object Stopped      extends PrototypeStatus { val asString = "stopped"      ; override def displayString = "Stopped"      }
    case object Undetermined extends PrototypeStatus { val asString = "undetermined" ; override def displayString = "Undetermined" }

    val values: List[PrototypeStatus] =
      List(Running, Stopped, Undetermined)

    def parse(s: String): Option[PrototypeStatus] =
      values.find(_.asString == s)

    val reads: Reads[PrototypeStatus] = json =>
      json.validate[String]
          .flatMap(s =>
            PrototypeStatus
              .parse(s)
              .map(JsSuccess(_))
              .getOrElse(JsError("invalid prototype status"))
          )

    implicit val pathBindable: PathBindable[PrototypeStatus] =
      new PathBindable[PrototypeStatus] {
        override def bind(key: String, value: String): Either[String, PrototypeStatus] =
          parse(value).toRight(s"Invalid prototype status '$value'")

        override def unbind(key: String, value: PrototypeStatus): String =
          value.asString
      }
  }

  final case class PrototypeDetails(url: Option[String], status: PrototypeStatus)

  object PrototypeDetails {
    val reads: Reads[PrototypeDetails] = {
      implicit val psR: Reads[PrototypeStatus] = PrototypeStatus.reads
      ( (__ \ "prototype_url").readNullable[String]
      ~ (__ \ "status").read[PrototypeStatus]
      )(PrototypeDetails.apply _)
    }
  }
}
