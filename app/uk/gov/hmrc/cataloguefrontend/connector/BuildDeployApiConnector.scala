/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.libs.json.{Format, __}
import com.google.inject.{Inject, Singleton}
import play.api.Logging
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import uk.gov.hmrc.cataloguefrontend.config.BuildDeployApiConfig
import uk.gov.hmrc.cataloguefrontend.connector.BuildDeployApiConnector.{ChangePrototypePasswordRequest, ChangePrototypePasswordResponse}
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BuildDeployApiConnector @Inject() (httpClientV2: HttpClientV2, awsCredentialsProvider: AwsCredentialsProvider, config: BuildDeployApiConfig)(implicit ec: ExecutionContext)
    extends Logging {

  def changePrototypePassword(payload: ChangePrototypePasswordRequest): Future[ChangePrototypePasswordResponse] = ???

}

object BuildDeployApiConnector {
  final case class ChangePrototypePasswordRequest(appName: String, password: String)

  object ChangePrototypePasswordRequest {
    val format: Format[ChangePrototypePasswordRequest] =
      ( (__ \ "app_name").format[String]
      ~ (__ \ "password").format[String]
      ) (ChangePrototypePasswordRequest.apply, unlift(ChangePrototypePasswordRequest.unapply))
  }

  final case class ChangePrototypePasswordResponse(success: Boolean, message: String)

  object ChangePrototypePasswordResponse {
    val format: Format[ChangePrototypePasswordResponse] =
      ( (__ \ "success").format[Boolean]
      ~ (__ \ "message").format[String]
      ) (ChangePrototypePasswordResponse.apply, unlift(ChangePrototypePasswordResponse.unapply))
  }
}
