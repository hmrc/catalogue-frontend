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
import uk.gov.hmrc.cataloguefrontend.ChangePrototypePassword.PrototypePassword
import uk.gov.hmrc.cataloguefrontend.config.BuildDeployApiConfig
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector._
import uk.gov.hmrc.cataloguefrontend.connector.model.TeamName
import uk.gov.hmrc.cataloguefrontend.createappconfigs.CreateAppConfigsForm
import uk.gov.hmrc.cataloguefrontend.createrepository.{CreatePrototypeRepoForm, CreateServiceRepoForm}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class BuildDeployApiConnector @Inject() (
  httpClientV2: HttpClientV2,
  config      : BuildDeployApiConfig
)(implicit
  ec          : ExecutionContext
) extends Logging {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private def executeRequest(
    endpoint   : String,
    body       : JsValue,
    queryParams: Map[String, String] = Map.empty[String, String]
  ): Future[Either[String, BuildDeployResponse]] = {
    val url = url"${config.platopsBndApiBaseUrl}/$endpoint?$queryParams"

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
      .execute[Either[String, BuildDeployResponse]]
  }

  def changePrototypePassword(repositoryName: String, password: PrototypePassword): Future[Either[String, String]] = {
    implicit val cpprr = ChangePrototypePasswordRequest.writes
    executeRequest(
      endpoint = "change-prototype-password",
      body     = Json.toJson(ChangePrototypePasswordRequest(repositoryName, password.value))
    ).map {
      case Right(response) => Right(response.message)
      case Left(errorMsg)  => Left(errorMsg)
    }
  }

  def getPrototypeDetails(prototypeName: String): Future[PrototypeDetails] = {
    implicit val pdr  : Reads[PrototypeDetails]           = PrototypeDetails.reads
    implicit val gpsrw: Writes[GetPrototypeStatusRequest] = GetPrototypeStatusRequest.writes

    executeRequest(
      endpoint = "get-prototype-details",
      body     = Json.toJson(GetPrototypeStatusRequest(prototypeName))
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
    implicit val spsrw: Writes[SetPrototypeStatusRequest] = SetPrototypeStatusRequest.writes
    executeRequest(
      endpoint = "set-prototype-status",
      body     = Json.toJson(SetPrototypeStatusRequest(
                   prototype,
                   status.asString
                 ))
    ).map(_.map(_ => ()))
  }

  def createServiceRepository(payload: CreateServiceRepoForm): Future[Either[String, AsyncRequestId]] = {
    implicit val csrrw: Writes[CreateServiceRepoRequest] = CreateServiceRepoRequest.writes

    val body = Json.toJson(CreateServiceRepoRequest(
      repositoryName = payload.repositoryName,
      teamName       = payload.teamName,
      makePrivate    = payload.makePrivate,
      repositoryType = payload.repoType,
    ))

    logger.info(s"Calling the B&D Create Repository API with the following payload: ${body}")

    executeRequest(
      endpoint = "create-service-repository",
      body = body
    ).map(_.map(resp => AsyncRequestId(resp.details)))
  }

  def createPrototypeRepository(payload: CreatePrototypeRepoForm): Future[Either[String, AsyncRequestId]] = {
    implicit val cprrw : Writes[CreatePrototypeRepoRequest] = CreatePrototypeRepoRequest.writes
    val body = Json.toJson(CreatePrototypeRepoRequest(
        repositoryName            = payload.repositoryName,
        teamName                  = payload.teamName,
        password                  = payload.password,
        slackNotificationChannels = payload.slackChannels,
      ))

    val obfuscatedBody = body.as[JsObject] + ("password" -> JsString("**********************"))
    logger.info(s"Calling the B&D Create Prototype Repository API with the following payload: $obfuscatedBody")

    executeRequest(
      endpoint = "create-prototype-repository",
      body = body
    ).map(_.map(resp => AsyncRequestId(resp.details)))
  }

  def createTestRepository(payload: CreateServiceRepoForm): Future[Either[String, AsyncRequestId]] = {
    implicit val crrrw: Writes[CreateTestRepoRequest] = CreateTestRepoRequest.writes
    val body =
      Json.toJson(CreateTestRepoRequest(
        repositoryName = payload.repositoryName,
        teamName       = payload.teamName,
        makePrivate    = payload.makePrivate,
        repositoryType = payload.repoType,
      ))

    logger.info(s"Calling the B&D Create Test Repository API with the following payload: ${body}")

    executeRequest(
      endpoint = "create-test-repository",
      body = body
    ).map(_.map(resp => AsyncRequestId(resp.details)))
  }

  def createAppConfigs(payload: CreateAppConfigsForm, serviceName: String, serviceType: ServiceType, requiresMongo: Boolean, isApi: Boolean): Future[Either[String, AsyncRequestId]] = {
    val (st, zone) =
      serviceType match {
        case ServiceType.Frontend         => ( "Frontend microservice", "public"    )
        case ServiceType.Backend if isApi => ( "API microservice"     , "protected" )
        case ServiceType.Backend          => ( "Backend microservice" , "protected" )
      }

    val appConfigEnvironments = Seq(
        ("base"                          , payload.appConfigBase),
        (Environment.Development.asString, payload.appConfigDevelopment),
        (Environment.QA.asString         , payload.appConfigQA),
        (Environment.Staging.asString    , payload.appConfigStaging),
        (Environment.Production.asString, payload.appConfigProduction),
      ).collect { case (env, flag) if flag => env }

    implicit val cacrw: Writes[CreateAppConfigsRequest] = CreateAppConfigsRequest.writes

    val body = Json.toJson(CreateAppConfigsRequest(
      serviceName,
      st,
      requiresMongo,
      appConfigEnvironments,
      zone
    ))

    logger.info(s"Calling the B&D Create App Configs API with the following payload: $body")

    executeRequest(
      endpoint = "create-app-configs",
      body     = body
    ).map(_.map(resp => AsyncRequestId(resp.details)))
  }
}

object BuildDeployApiConnector {
  final case class BuildDeployResponse(
    message: String,
    details: JsValue
  )

  private object BuildDeployResponse {
    val reads: Reads[BuildDeployResponse] =
      ( (__ \ "message").read[String]
      ~ (__ \ "details").readWithDefault[JsValue](JsNull)
      )(BuildDeployResponse.apply _)
  }

  final case class AsyncRequestId(request: JsValue)

  sealed trait PrototypeStatus { def asString: String; def displayString: String }

  object PrototypeStatus {
    case object Running      extends PrototypeStatus { val asString = "running"     ; override def displayString = "Running"      }
    case object Stopped      extends PrototypeStatus { val asString = "stopped"     ; override def displayString = "Stopped"      }
    case object Undetermined extends PrototypeStatus { val asString = "undetermined"; override def displayString = "Undetermined" }

    val values: List[PrototypeStatus] =
      List(Running, Stopped, Undetermined)

    def parse(s: String): Option[PrototypeStatus] =
      values.find(_.asString == s)

    val reads: Reads[PrototypeStatus] =
      _.validate[String]
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

  final case class PrototypeDetails(
    url   : Option[String],
    status: PrototypeStatus
  )

  object PrototypeDetails {
    val reads: Reads[PrototypeDetails] = {
      implicit val psR: Reads[PrototypeStatus] = PrototypeStatus.reads
      ( (__ \ "prototypeUrl").readNullable[String]
      ~ (__ \ "status").read[PrototypeStatus]
      )(PrototypeDetails.apply _)
    }
  }

  final case class ChangePrototypePasswordRequest(
    prototype: String,
    password : String
  )

  object ChangePrototypePasswordRequest {
    val writes: Writes[ChangePrototypePasswordRequest] =
      ( (__ \ "repositoryName").write[String]
      ~ (__ \ "password"      ).write[String]
      )(unlift(ChangePrototypePasswordRequest.unapply))
  }

  case class GetPrototypeStatusRequest(
    prototype: String
  )

  object GetPrototypeStatusRequest {
    val writes: Writes[GetPrototypeStatusRequest] =
      (__ \ "prototype").write[String].contramap[GetPrototypeStatusRequest](_.prototype)
  }

  case class SetPrototypeStatusRequest(
    prototype: String,
    status   : String
  )

  object SetPrototypeStatusRequest {
    val writes: Writes[SetPrototypeStatusRequest] =
      ( (__ \ "prototype").write[String]
      ~ (__ \ "status"   ).write[String]
      )(unlift(SetPrototypeStatusRequest.unapply _))
  }

  case class CreateServiceRepoRequest(
    repositoryName            : String,
    teamName                  : TeamName,
    makePrivate               : Boolean,
    repositoryType            : String,
    slackNotificationChannels : String   = ""
  )

  object CreateServiceRepoRequest {
    val writes: Writes[CreateServiceRepoRequest] = {
      implicit val tnf: Format[TeamName] = TeamName.format
      ( (__ \ "repositoryName"           ).write[String]
      ~ (__ \ "teamName"                 ).write[TeamName]
      ~ (__ \ "makePrivate"              ).write[Boolean]
      ~ (__ \ "repositoryType"           ).write[String]
      ~ (__ \ "slackNotificationChannels").write[String]
      )(unlift(CreateServiceRepoRequest.unapply _))
    }
  }

  case class CreatePrototypeRepoRequest(
    repositoryName           : String,
    teamName                 : TeamName,
    password                 : String,
    slackNotificationChannels: String
  )

  object CreatePrototypeRepoRequest {
    val writes: Writes[CreatePrototypeRepoRequest] = {
      implicit val tnf: Format[TeamName] = TeamName.format
      ( (__ \ "repositoryName"           ).write[String]
      ~ (__ \ "teamName"                 ).write[TeamName]
      ~ (__ \ "password"                 ).write[String]
      ~ (__ \ "slackNotificationChannels").write[String]
      )(unlift(CreatePrototypeRepoRequest.unapply _))
    }
  }

  case class CreateTestRepoRequest(
    repositoryName       : String,
    teamName             : TeamName,
    makePrivate          : Boolean,
    repositoryType       : String
  )

  object CreateTestRepoRequest {
    val writes: Writes[CreateTestRepoRequest] = {
      implicit val tnf: Format[TeamName] = TeamName.format
      ( (__ \ "repositoryName"       ).write[String]
      ~ (__ \ "teamName"             ).write[TeamName]
      ~ (__ \ "makePrivate"          ).write[Boolean]
      ~ (__ \ "repositoryType"       ).write[String]
      )(unlift(CreateTestRepoRequest.unapply _))
    }
  }

  case class CreateAppConfigsRequest(
    microserviceName: String,
    microserviceType: String,
    hasMongo        : Boolean,
    environments    : Seq[String],
    zone            : String
  )

  object CreateAppConfigsRequest {
    val writes: Writes[CreateAppConfigsRequest] =
      ( (__ \ "microserviceName").write[String]
      ~ (__ \ "microserviceType").write[String]
      ~ (__ \ "hasMongo"        ).write[Boolean]
      ~ (__ \ "environments"    ).write[Seq[String]]
      ~ (__ \ "zone"            ).write[String]
    )(unlift(CreateAppConfigsRequest.unapply _))
  }
}
