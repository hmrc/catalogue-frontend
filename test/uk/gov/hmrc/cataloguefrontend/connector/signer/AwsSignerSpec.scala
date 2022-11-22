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

package uk.gov.hmrc.cataloguefrontend.connector.signer

import software.amazon.awssdk.auth.credentials.{AwsCredentials, AwsCredentialsProvider, StaticCredentialsProvider}
import uk.gov.hmrc.cataloguefrontend.util.UnitSpec

import java.time.LocalDateTime

class AwsSignerSpec extends UnitSpec {

  val awsAccessKey = "AKIDEXAMPLE"
  val awsSecretKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"
  val credentials: AwsCredentials = new AwsCredentials {
    override def accessKeyId(): String = awsAccessKey

    override def secretAccessKey(): String = awsSecretKey
  }
  val credentialsProvider: AwsCredentialsProvider = StaticCredentialsProvider.create(credentials)
  val region = "eu-west-2"
  val service = "service"

  val clock: () => LocalDateTime = () => LocalDateTime.of(2021, 9, 15, 12, 42, 0)

  "getSignedHeaders" should {
    "sign a GET request" in {
      val signer = AwsSigner(credentialsProvider, region, service, clock)

      val signedHeaders = signer.getSignedHeaders(
        uri = "/",
        method = "GET",
        queryParams = Map.empty[String, String],
        headers = Map.empty[String, String],
        payload =None
      )

      val expectedAmzDate = "20210915T124200Z"
      val expectedSignature = "8ec4ea9c921e719e79b7d3d5ac5f0ac6e7c78aa1fc027f9d437502518dd17b8c"
      val expectedAuthorizationHeader =
        s"AWS4-HMAC-SHA256 Credential=$awsAccessKey/20210915/$region/$service/aws4_request, SignedHeaders=x-amz-date, Signature=$expectedSignature"

      signedHeaders.contains("Authorization") shouldBe true
      signedHeaders.get("Authorization") shouldBe Some(expectedAuthorizationHeader)
      signedHeaders.get("X-Amz-Date") shouldBe Some(expectedAmzDate)
    }

    "sign a POST request" in {
      val signer = AwsSigner(credentialsProvider, region, service, clock)
      val payload = """ { "exampleKey": "exampleValue" } """

      val signedHeaders = signer.getSignedHeaders(
        uri = "/submit",
        method = "POST",
        queryParams = Map.empty[String, String],
        headers = Map.empty[String, String],
        payload = Some(payload.getBytes)
      )

      val expectedAmzDate = "20210915T124200Z"
      val expectedSignature = "444e165e87437cb89a1ac6d0583e6529e54b5699de1411a366bc21afd79af392"
      val expectedAuthorizationHeader =
        s"AWS4-HMAC-SHA256 Credential=$awsAccessKey/20210915/$region/$service/aws4_request, SignedHeaders=x-amz-date, Signature=$expectedSignature"

      signedHeaders.contains("Authorization") shouldBe true
      signedHeaders.get("Authorization") shouldBe Some(expectedAuthorizationHeader)
      signedHeaders.get("X-Amz-Date") shouldBe Some(expectedAmzDate)
    }
  }

}
