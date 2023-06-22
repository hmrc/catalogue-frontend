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

import play.api.libs.functional.syntax._
import play.api.libs.json._
import com.google.inject.{Inject, Singleton}
import play.api.mvc.PathBindable
import play.api.Logging
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import uk.gov.hmrc.cataloguefrontend.ChangePrototypePassword.PrototypePassword
import uk.gov.hmrc.cataloguefrontend.config.BuildDeployApiConfig
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector._
import uk.gov.hmrc.cataloguefrontend.connector.signer.AwsSigner
import uk.gov.hmrc.cataloguefrontend.createarepository.CreateRepoForm
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpReadsInstances, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._

import java.net.URL
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

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
    endpoint: String,
    queryParams: Map[String, String]
  ): URL = url"${config.baseUrl}/v1/$endpoint?$queryParams"

  private def signedHeaders(
    uri: String,
    queryParams: Map[String, String],
    body: JsValue
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
    endpoint: String,
    body: JsValue,
    queryParams: Map[String, String] = Map.empty[String, String]
  ): Future[BuildDeployResponse] = {
    val url = buildUrl(endpoint, queryParams)

    val headers = signedHeaders(url.getPath, queryParams, body)

    implicit val hr: HttpReads[BuildDeployResponse] =
      implicitly[HttpReads[HttpResponse]]
        .flatMap { response =>
          response.status match {
            case 200  =>
              implicit val r: Reads[BuildDeployResponse] = BuildDeployResponse.reads
              HttpReadsInstances.readFromJson[BuildDeployResponse]
            case code =>
              val msg = (response.json \ "message").as[String]
              logger.warn(s"Received $code response from Build and Deploy API endpoint $endpoint - $msg")
              HttpReads.pure(BuildDeployResponse(success = false, message = msg, details = None))
          }
        }

    httpClientV2
      .post(url)
      .withBody(body)
      .setHeader(headers.toSeq: _*)
      .execute[BuildDeployResponse]
  }

  def changePrototypePassword(prototype: String, password: PrototypePassword): Future[Either[String, String]] = {

    val body = Json.obj(
      "repository_name" -> JsString(prototype),
      "password"        -> JsString(password.value)
    )

    signAndExecuteRequest(
      endpoint = "SetHerokuPrototypePassword",
      body     = body
    ).map { response =>
      if(response.success) Right(response.message)
      else Left(response.message)
    }
  }
  
  def getPrototypeStatus(prototype: String): Future[PrototypeStatus] = {
    implicit val psR: Reads[PrototypeStatus] = PrototypeStatus.reads

    val body = Json.obj(
      "prototype" -> JsString(prototype)
    )

    signAndExecuteRequest(
      endpoint = "GetPrototypeStatus",
      body     = body
    ).map(_.details.fold[PrototypeStatus](PrototypeStatus.Undetermined)(_.as[PrototypeStatus]))
  }

  def setPrototypeStatus(prototype: String, status: PrototypeStatus): Future[PrototypeStatus] = {
    implicit val psR: Reads[PrototypeStatus] = PrototypeStatus.reads

    val body = Json.obj(
      "prototype" -> JsString(prototype),
      "status" -> JsString(status.asString)
    )

    signAndExecuteRequest(
      endpoint = "SetPrototypeStatus",
      body     = body
    ).map(_.details.fold[PrototypeStatus](PrototypeStatus.Undetermined)(_.as[PrototypeStatus]))
  }

  def createARepository(payload: CreateRepoForm): Future[Unit] = {
    val queryParams = Map.empty[String, String]

    val url = url"${config.baseUrl}/v1/CreateRepository?$queryParams"

    val finalPayload =
      s"""
         |{
         |   "repository_name": "${payload.repositoryName}",
         |   "make_private": "${payload.makePrivate}",
         |   "allow_auto_merge": "true",
         |   "delete_branch_on_merge": "true",
         |   "team_name": "${payload.teamName}",
         |   "repository_type": "${payload.repoType}",
         |   "bootstrap_tag": "",
         |   "init_webhook_version": "2.2.0",
         |   "default_branch_name": "main"
         |}
         |   """.stripMargin

    val body = Json.toJson(finalPayload)

    val headers = signedHeaders(url.getPath, queryParams, body)

    httpClientV2
      .post(url)
      .withBody(body)
      .setHeader(headers.toSeq: _*)
      .execute[Unit](HttpReads.Implicits.throwOnFailure(implicitly[HttpReads[Either[UpstreamErrorResponse, Unit]]]), implicitly[ExecutionContext])
  }
}

object BuildDeployApiConnector {
  final case class BuildDeployResponse(success: Boolean, message: String, details: Option[JsValue])

  private object BuildDeployResponse {
    val reads: Reads[BuildDeployResponse] =
      ( (__ \ "success").read[Boolean]
      ~ (__ \ "message").read[String]
      ~ (__ \ "details").readNullable[JsValue]
      )(BuildDeployResponse.apply _)
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

    val reads: Reads[PrototypeStatus] =
      Reads.at[PrototypeStatus](__ \ "status")(
        _.validate[String]
          .flatMap(s =>
            PrototypeStatus
              .parse(s)
              .map(JsSuccess(_))
              .getOrElse(JsError("invalid prototype status"))
          )
      )

    implicit val pathBindable: PathBindable[PrototypeStatus] =
      new PathBindable[PrototypeStatus] {
        override def bind(key: String, value: String): Either[String, PrototypeStatus] =
          parse(value).toRight(s"Invalid prototype status '$value'")

        override def unbind(key: String, value: PrototypeStatus): String =
          value.asString
      }
  }
}
