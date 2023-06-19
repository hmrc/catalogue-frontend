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
import play.api.{Logger, Logging}
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import uk.gov.hmrc.cataloguefrontend.ChangePrototypePassword.PrototypePassword
import uk.gov.hmrc.cataloguefrontend.config.BuildDeployApiConfig
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector._
import uk.gov.hmrc.cataloguefrontend.connector.signer.AwsSigner
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpReadsInstances, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._

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

  def changePrototypePassword(payload: ChangePrototypePasswordRequest): Future[ChangePrototypePasswordResponse] = {
    implicit val rwr: Writes[ChangePrototypePasswordRequest] = ChangePrototypePasswordRequest.writes
    implicit val rhr: HttpReads[ChangePrototypePasswordResponse] = ChangePrototypePasswordResponse.httpReads

    val queryParams = Map.empty[String, String]

    val url = url"${config.baseUrl}/v1/SetHerokuPrototypePassword?$queryParams"

    val body = Json.toJson(payload)

    val headers = AwsSigner(awsCredentialsProvider, config.awsRegion, "execute-api", () => LocalDateTime.now())
      .getSignedHeaders(
        uri = url.getPath,
        method = "POST",
        queryParams = queryParams,
        headers = Map[String, String]("host" -> config.host),
        payload = Some(Json.toBytes(body))
      )

    httpClientV2
      .post(url)
      .withBody(body)
      .setHeader(headers.toSeq: _*)
      .execute[ChangePrototypePasswordResponse]
  }

  def getPrototypeStatus(prototype: String): Future[PrototypeStatus] = {
    implicit val gpsR: HttpReads[GetPrototypeStatusResponse] = GetPrototypeStatusResponse.httpReads

    val queryParams = Map.empty[String, String]

    val url = url"${config.baseUrl}/v1/GetPrototypeStatus?$queryParams"

    val body = Json.obj(
      "prototype" -> JsString(prototype)
    )

    val headers = AwsSigner(awsCredentialsProvider, config.awsRegion, "execute-api", () => LocalDateTime.now())
      .getSignedHeaders(
        uri = url.getPath,
        method = "POST",
        queryParams = queryParams,
        headers = Map[String, String]("host" -> config.host),
        payload = Some(Json.toBytes(body))
      )

    httpClientV2
      .post(url)
      .withBody(body)
      .setHeader(headers.toSeq: _*)
      .execute[GetPrototypeStatusResponse]
      .map(_.status)
  }
}

object BuildDeployApiConnector {
  final case class ChangePrototypePasswordRequest(
    repoName: String,
    password: PrototypePassword
  )

  object ChangePrototypePasswordRequest {
    val writes: Writes[ChangePrototypePasswordRequest] =
      ( (__ \ "repository_name").write[String]
      ~ (__ \ "password"       ).write[String].contramap[PrototypePassword](_.value)
      ) (unlift(ChangePrototypePasswordRequest.unapply))
  }

  final case class ChangePrototypePasswordResponse(success: Boolean, message: String)

  object ChangePrototypePasswordResponse {
    private val reads: Reads[ChangePrototypePasswordResponse] =
      ( (__ \ "success").read[Boolean]
      ~ (__ \ "message").read[String]
      ) (ChangePrototypePasswordResponse.apply _)

    val httpReads: HttpReads[ChangePrototypePasswordResponse] =
      implicitly[HttpReads[HttpResponse]]
        .flatMap { response =>
          response.status match {
            case 400 =>
              val msg: String = (response.json \ "message").as[String]
              HttpReads.pure(ChangePrototypePasswordResponse(success = false, msg))
            case _ =>
              implicit val r: Reads[ChangePrototypePasswordResponse] = reads
              HttpReadsInstances.readFromJson[ChangePrototypePasswordResponse]
          }
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

    val format: Format[PrototypeStatus] = new Format[PrototypeStatus] {
      override def writes(o: PrototypeStatus): JsValue = JsString(o.asString)
      override def reads(json: JsValue): JsResult[PrototypeStatus] =
        json.validate[String].flatMap(s => PrototypeStatus.parse(s).map(p => JsSuccess(p)).getOrElse(JsError("invalid prototype status")))
    }
  }

  final case class GetPrototypeStatusResponse(success: Boolean, message: String, status: PrototypeStatus)

  object GetPrototypeStatusResponse {
    implicit val psF: Format[PrototypeStatus] = PrototypeStatus.format
    private val reads: Reads[GetPrototypeStatusResponse] =
      ( (__ \ "success").read[Boolean]
      ~ (__ \ "message").read[String]
      ~ (__ \ "details" \ "status").read[PrototypeStatus]
      ) (GetPrototypeStatusResponse.apply _)

    val httpReads: HttpReads[GetPrototypeStatusResponse] =
      implicitly[HttpReads[HttpResponse]]
        .flatMap { response =>
          response.status match {
            case 200 =>
              implicit val r: Reads[GetPrototypeStatusResponse] = reads
              HttpReadsInstances.readFromJson[GetPrototypeStatusResponse]
            case _ =>
              val msg = (response.json \ "message").as[String]
              val prototype = (response.json \ "details" \ "prototype").as[String]
              Logger(getClass).warn(s"Unable to determine prototype status for $prototype - $msg")
              HttpReads.pure(GetPrototypeStatusResponse(success = false, message = msg, status = PrototypeStatus.Undetermined))
          }
        }
  }

}
