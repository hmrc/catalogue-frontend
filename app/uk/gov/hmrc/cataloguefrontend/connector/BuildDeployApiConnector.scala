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

import play.api.Logging
import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import play.api.libs.ws.writeableOf_JsValue
import play.api.mvc.PathBindable
import uk.gov.hmrc.cataloguefrontend.ChangePrototypePassword.PrototypePassword
import uk.gov.hmrc.cataloguefrontend.config.BuildDeployApiConfig
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.*
import uk.gov.hmrc.cataloguefrontend.createappconfigs.CreateAppConfigsForm
import uk.gov.hmrc.cataloguefrontend.createrepository.{CreatePrototype, CreateService, CreateTest}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, TeamName}
import uk.gov.hmrc.cataloguefrontend.util.{FromString, FromStringEnum, Parser}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class BuildDeployApiConnector @Inject() (
  httpClientV2: HttpClientV2,
  config      : BuildDeployApiConfig
)(using
  ExecutionContext
) extends Logging:

  private def executeRequest(
    endpoint   : String,
    body       : JsValue,
    queryParams: Map[String, String] = Map.empty[String, String]
  )(using
    HeaderCarrier
  ): Future[Either[String, BuildDeployResponse]] =
    val url = url"${config.platopsBndApiBaseUrl}/$endpoint?$queryParams"

    given Reads[BuildDeployResponse] = BuildDeployResponse.reads

    given HttpReads[Either[String, BuildDeployResponse]] =
      summon[HttpReads[Either[UpstreamErrorResponse, BuildDeployResponse]]]
        .flatMap:
          case Right(r) =>
            HttpReads.pure(Right(r))
          case Left(UpstreamErrorResponse.Upstream4xxResponse(e)) =>
            HttpReads.ask
              .flatMap: (_, _, response) =>
                logger.error(s"Failed to call Build and Deploy API endpoint $endpoint. response: $response: ${e.getMessage}", e)
                Try(HttpReads.pure[Either[String, BuildDeployResponse]](
                  Left((response.json \ "message").as[String])
                )).getOrElse(throw e)
          case Left(other) =>
            throw other

    httpClientV2
      .post(url)
      .withBody(body)
      .execute[Either[String, BuildDeployResponse]]

  end executeRequest

  def changePrototypePassword(
    repositoryName: String,
    password      : PrototypePassword
  )(using
    HeaderCarrier
  ): Future[Either[String, String]] =
    given Writes[ChangePrototypePasswordRequest] = ChangePrototypePasswordRequest.writes
    executeRequest(
      endpoint = "change-prototype-password",
      body     = Json.toJson(ChangePrototypePasswordRequest(repositoryName, password.value))
    ).map:
      case Right(response) => Right(response.message)
      case Left(errorMsg)  => Left(errorMsg)

  def getPrototypeDetails(
    prototypeName: String
  )(using
    HeaderCarrier
  ): Future[PrototypeDetails] =
    given Reads[PrototypeDetails]           = PrototypeDetails.reads
    given Writes[GetPrototypeStatusRequest] = GetPrototypeStatusRequest.writes

    executeRequest(
      endpoint = "get-prototype-details",
      body     = Json.toJson(GetPrototypeStatusRequest(prototypeName))
    )
      .map:
        case Left(errMsg) => logger.error(s"Call to GetPrototypeStatus failed with: $errMsg")
                             PrototypeDetails(
                               url    = None,
                               status = PrototypeStatus.Undetermined
                             )
        case Right(res)   => res.details.as[PrototypeDetails]
      .recover: e =>
        logger.error(s"Call GetPrototypeStatus failed with: ${e.getMessage}", e)
        PrototypeDetails(
          url    = None,
          status = PrototypeStatus.Undetermined
        )

  def setPrototypeStatus(
    prototype: String,
    status   : PrototypeStatus
  )(using
    HeaderCarrier
  ): Future[Either[String, Unit]] =
    given Writes[SetPrototypeStatusRequest] = SetPrototypeStatusRequest.writes
    executeRequest(
      endpoint = "set-prototype-status",
      body     = Json.toJson(SetPrototypeStatusRequest(
                   prototype,
                   status.asString
                 ))
    ).map(_.map(_ => ()))

  def createServiceRepository(
    payload: CreateService
  )(using
    HeaderCarrier
  ): Future[Either[String, AsyncRequestId]] =
    given Writes[CreateServiceRepoRequest] = CreateServiceRepoRequest.writes

    val body = Json.toJson(CreateServiceRepoRequest(
      repositoryName = payload.repositoryName,
      teamName       = payload.teamName,
      makePrivate    = payload.makePrivate,
      repositoryType = payload.serviceType,
    ))

    logger.info(s"Calling the B&D Create Repository API with the following payload: ${body}")

    executeRequest(
      endpoint = "create-service-repository",
      body     = body
    ).map(_.map(resp => AsyncRequestId(resp.details)))

  def createPrototypeRepository(
    payload: CreatePrototype
  )(using
    HeaderCarrier
  ): Future[Either[String, AsyncRequestId]] =
    given Writes[CreatePrototypeRepoRequest] = CreatePrototypeRepoRequest.writes
    val body = Json.toJson(CreatePrototypeRepoRequest(
        repositoryName            = payload.repositoryName,
        teamName                  = payload.teamName,
        password                  = payload.password,
        slackNotificationChannels = payload.slackChannels,
      ))

    logger.info:
      val obfuscatedBody = body.as[JsObject] + ("password" -> JsString("**********************"))
      s"Calling the B&D Create Prototype Repository API with the following payload: $obfuscatedBody"

    executeRequest(
      endpoint = "create-prototype-repository",
      body     = body
    ).map(_.map(resp => AsyncRequestId(resp.details)))

  def createTestRepository(
    payload: CreateTest
  )(using
    HeaderCarrier
  ): Future[Either[String, AsyncRequestId]] =
    given Writes[CreateTestRepoRequest] = CreateTestRepoRequest.writes
    val body =
      Json.toJson(CreateTestRepoRequest(
        repositoryName = payload.repositoryName,
        teamName       = payload.teamName,
        makePrivate    = payload.makePrivate,
        repositoryType = payload.testType,
      ))

    logger.info(s"Calling the B&D Create Test Repository API with the following payload: ${body}")

    executeRequest(
      endpoint = "create-test-repository",
      body     = body
    ).map(_.map(resp => AsyncRequestId(resp.details)))

  def createAppConfigs(
    payload      : CreateAppConfigsForm,
    serviceName  : ServiceName,
    serviceType  : ServiceType,
    requiresMongo: Boolean,
    isApi        : Boolean
  )(using
    HeaderCarrier
  ): Future[Either[String, AsyncRequestId]] =
    val (st, zone) =
      serviceType match
        case ServiceType.Frontend         => ("Frontend microservice", "public"   )
        case ServiceType.Backend if isApi => ("API microservice"     , "protected")
        case ServiceType.Backend          => ("Backend microservice" , "protected")

    val appConfigEnvironments = Seq(
        ("base"                          , payload.appConfigBase),
        (Environment.Development.asString, payload.appConfigDevelopment),
        (Environment.QA.asString         , payload.appConfigQA),
        (Environment.Staging.asString    , payload.appConfigStaging),
        (Environment.Production.asString, payload.appConfigProduction),
      ).collect { case (env, flag) if flag => env }

    given Writes[CreateAppConfigsRequest] = CreateAppConfigsRequest.writes

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

end BuildDeployApiConnector

object BuildDeployApiConnector:
  case class BuildDeployResponse(
    message: String,
    details: JsValue
  )

  private object BuildDeployResponse:
    val reads: Reads[BuildDeployResponse] =
      ( (__ \ "message").read[String]
      ~ (__ \ "details").readWithDefault[JsValue](JsNull)
      )(BuildDeployResponse.apply)

  case class AsyncRequestId(request: JsValue)

  import FromStringEnum._

  given Parser[PrototypeStatus] = Parser.parser(PrototypeStatus.values)

  enum PrototypeStatus(
    override val asString: String,
    val displayString    : String
  ) extends FromString
    derives Ordering, Reads, PathBindable:
    case Running      extends PrototypeStatus(asString = "running"     , displayString = "Running"     )
    case Stopped      extends PrototypeStatus(asString = "stopped"     , displayString = "Stopped"     )
    case Undetermined extends PrototypeStatus(asString = "undetermined", displayString = "Undetermined")

  case class PrototypeDetails(
    url   : Option[String],
    status: PrototypeStatus
  )

  object PrototypeDetails:
    val reads: Reads[PrototypeDetails] =
      ( (__ \ "prototypeUrl").readNullable[String]
      ~ (__ \ "status"      ).read[PrototypeStatus]
      )(PrototypeDetails.apply)

  case class ChangePrototypePasswordRequest(
    prototype: String,
    password : String
  )

  object ChangePrototypePasswordRequest:
    val writes: Writes[ChangePrototypePasswordRequest] =
      ( (__ \ "repositoryName").write[String]
      ~ (__ \ "password"      ).write[String]
      )(r => Tuple.fromProductTyped(r))

  case class GetPrototypeStatusRequest(
    prototype: String
  )

  object GetPrototypeStatusRequest:
    val writes: Writes[GetPrototypeStatusRequest] =
      (__ \ "prototype").write[String].contramap[GetPrototypeStatusRequest](_.prototype)

  case class SetPrototypeStatusRequest(
    prototype: String,
    status   : String
  )

  object SetPrototypeStatusRequest:
    val writes: Writes[SetPrototypeStatusRequest] =
      ( (__ \ "prototype").write[String]
      ~ (__ \ "status"   ).write[String]
      )(r => Tuple.fromProductTyped(r))

  case class CreateServiceRepoRequest(
    repositoryName            : String,
    teamName                  : TeamName,
    makePrivate               : Boolean,
    repositoryType            : String,
    slackNotificationChannels : String   = ""
  )

  object CreateServiceRepoRequest:
    val writes: Writes[CreateServiceRepoRequest] =
      ( (__ \ "repositoryName"           ).write[String]
      ~ (__ \ "teamName"                 ).write[TeamName]
      ~ (__ \ "makePrivate"              ).write[Boolean]
      ~ (__ \ "repositoryType"           ).write[String]
      ~ (__ \ "slackNotificationChannels").write[String]
      )(r => Tuple.fromProductTyped(r))

  case class CreatePrototypeRepoRequest(
    repositoryName           : String,
    teamName                 : TeamName,
    password                 : String,
    slackNotificationChannels: String
  )

  object CreatePrototypeRepoRequest:
    val writes: Writes[CreatePrototypeRepoRequest] =
      ( (__ \ "repositoryName"           ).write[String]
      ~ (__ \ "teamName"                 ).write[TeamName]
      ~ (__ \ "password"                 ).write[String]
      ~ (__ \ "slackNotificationChannels").write[String]
      )(r => Tuple.fromProductTyped(r))

  case class CreateTestRepoRequest(
    repositoryName       : String,
    teamName             : TeamName,
    makePrivate          : Boolean,
    repositoryType       : String
  )

  object CreateTestRepoRequest:
    val writes: Writes[CreateTestRepoRequest] =
      ( (__ \ "repositoryName"       ).write[String]
      ~ (__ \ "teamName"             ).write[TeamName]
      ~ (__ \ "makePrivate"          ).write[Boolean]
      ~ (__ \ "repositoryType"       ).write[String]
      )(r => Tuple.fromProductTyped(r))

  case class CreateAppConfigsRequest(
    microserviceName: ServiceName,
    microserviceType: String,
    hasMongo        : Boolean,
    environments    : Seq[String],
    zone            : String
  )

  object CreateAppConfigsRequest:
    val writes: Writes[CreateAppConfigsRequest] =
      ( (__ \ "microserviceName").write[ServiceName]
      ~ (__ \ "microserviceType").write[String]
      ~ (__ \ "hasMongo"        ).write[Boolean]
      ~ (__ \ "environments"    ).write[Seq[String]]
      ~ (__ \ "zone"            ).write[String]
    )(r => Tuple.fromProductTyped(r))

end BuildDeployApiConnector
