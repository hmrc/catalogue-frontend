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
import play.api.libs.json.{Json, Reads, Writes, __}
import com.google.inject.{Inject, Singleton}
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

  def createARepository(payload: CreateRepoForm): Future[Unit] = {
    implicit val crfw: Writes[CreateRepoForm] = CreateRepoForm.writes

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
         |   "type": "${payload.repoType}",
         |   "bootstrap_tag": "",
         |   "init_webhook_version": "2.2.0",
         |   "default_branch_name": "main"
         |}
         |   """.stripMargin

    val body = Json.toJson(finalPayload)

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
      .execute[Unit](HttpReads.Implicits.throwOnFailure(implicitly[HttpReads[Either[UpstreamErrorResponse, Unit]]]), implicitly[ExecutionContext])
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
}
