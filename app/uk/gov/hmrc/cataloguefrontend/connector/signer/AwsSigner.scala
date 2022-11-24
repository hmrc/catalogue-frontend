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

// Inspired by: https://github.com/ticofab/aws-request-signer

package uk.gov.hmrc.cataloguefrontend.connector.signer

import software.amazon.awssdk.auth.credentials.{AwsCredentials, AwsCredentialsProvider, AwsSessionCredentials}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.{InvalidKeyException, MessageDigest, NoSuchAlgorithmException}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.collection.immutable.{ListMap, TreeMap}

object AwsSigner {

  def apply(credentialsProvider: AwsCredentialsProvider,
            region: String,
            service: String,
            clock: () => LocalDateTime): AwsSigner = new AwsSigner(credentialsProvider, region, service, clock)

  def apply(awsAccessKeyId: String,
            awsSecretKey: String,
            region: String,
            service: String,
            clock: () => LocalDateTime): AwsSigner = {

    val credentialsProvider = new AwsCredentialsProvider {

      override def resolveCredentials: AwsCredentials = new AwsCredentials {
        override def accessKeyId: String = awsAccessKeyId

        override def secretAccessKey: String = awsSecretKey
      }
    }
    new AwsSigner(credentialsProvider, region, service, clock)
  }
}

class AwsSigner (credentialsProvider: AwsCredentialsProvider,
                 region: String,
                 service: String,
                 clock: () => LocalDateTime) {

  private val BASE16MAP = Array[Char]('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
  private val HMAC_SHA256 = "HmacSHA256"
  private val SLASH = "/"
  private val X_AMZ_DATE = "x-amz-date"
  private val RETURN = "\n"
  private val AWS4_HMAC_SHA256 = "AWS4-HMAC-SHA256"
  private val AWS4_REQUEST = "/aws4_request"
  private val AWS4_HMAC_SHA256_CREDENTIAL = "AWS4-HMAC-SHA256 Credential="
  private val SIGNED_HEADERS = ", SignedHeaders="
  private val SIGNATURE = ", Signature="
  private val SHA_256 = "SHA-256"
  private val AWS4 = "AWS4"
  private val AWS_4_REQUEST = "aws4_request"
  private val CONNECTION = "connection"
  private val CLOSE = ":close"
  private val EMPTY = ""
  private val ZERO = "0"
  private val CONTENT_LENGTH = "Content-Length"
  private val AUTHORIZATION = "Authorization"
  private val SESSION_TOKEN = "x-amz-security-token"
  private val DATE = "date"
  private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
  private val BASIC_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE

  def getSignedHeaders(uri: String,
                       method: String,
                       queryParams: Map[String, String],
                       headers: Map[String, String],
                       payload: Option[Array[Byte]]): Map[String, String] = {

    def queryParamsString(queryParams: Map[String, String]) = {
      val orderedParams = ListMap(queryParams.toSeq.sortWith(_._1 < _._1): _*)

      orderedParams.map { case (key, value) => key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8.toString) }.mkString("&")
    }

    def sign(stringToSign: String, now: LocalDateTime, credentials: AwsCredentials): String = {
      def hmacSHA256(data: String, key: Array[Byte]): Array[Byte] = {
        try {
          val mac: Mac = Mac.getInstance(HMAC_SHA256)
          mac.init(new SecretKeySpec(key, HMAC_SHA256))
          mac.doFinal(data.getBytes(StandardCharsets.UTF_8))
        } catch {
          case e: NoSuchAlgorithmException => throw e
          case i: InvalidKeyException => throw i
        }
      }

      def getSignatureKey(now: LocalDateTime, credentials: AwsCredentials): Array[Byte] = {
        val kSecret: Array[Byte] = (AWS4 + credentials.secretAccessKey()).getBytes(StandardCharsets.UTF_8)
        val kDate: Array[Byte] = hmacSHA256(now.format(BASIC_DATE_FORMATTER), kSecret)
        val kRegion: Array[Byte] = hmacSHA256(region, kDate)
        val kService: Array[Byte] = hmacSHA256(service, kRegion)
        hmacSHA256(AWS_4_REQUEST, kService)
      }

      toBase16(hmacSHA256(stringToSign, getSignatureKey(now, credentials)))
    }

    def headerAsString(header: (String, Object), method: String): String =
      if (header._1.equalsIgnoreCase(CONNECTION)) {
        CONNECTION + CLOSE
      } else if (header._1.equalsIgnoreCase(CONTENT_LENGTH) && header._2.equals(ZERO) && !method.equalsIgnoreCase("POST")) {
        header._1.toLowerCase + ':'
      } else {
        header._1.toLowerCase + ':' + header._2
      }

    def getCredentialScope(now: LocalDateTime): String =
      now.format(BASIC_DATE_FORMATTER) + SLASH + region + SLASH + service + AWS4_REQUEST

    def hash(payload: Array[Byte]): Array[Byte] =
      try {
        val md: MessageDigest = MessageDigest.getInstance(SHA_256)
        md.update(payload)
        md.digest
      } catch {
        case n: NoSuchAlgorithmException => throw n
      }

    def toBase16(data: Array[Byte]): String =
      data.flatMap(byte => Array(BASE16MAP(byte >> 4 & 0xF), BASE16MAP(byte & 0xF))).mkString

    def createStringToSign(canonicalRequest: String, now: LocalDateTime): String =
      AWS4_HMAC_SHA256 + RETURN +
        now.format(DATE_FORMATTER) + RETURN +
        getCredentialScope(now) + RETURN +
        toBase16(hash(canonicalRequest.getBytes(StandardCharsets.UTF_8)))

    val now: LocalDateTime = clock.apply()

    val credentials: AwsCredentials = credentialsProvider.resolveCredentials()

    var result = TreeMap[String, String]()(Ordering.by(_.toLowerCase))
    for ((key, value) <- headers) result += key -> value

    if (!result.contains(DATE)) {
      result += (X_AMZ_DATE -> now.format(DATE_FORMATTER))
    }

    credentials match {
      case asc: AwsSessionCredentials => result += (SESSION_TOKEN -> asc.sessionToken())
      case _ => // do nothing
    }

    val headersString: String = result.map(pair => headerAsString(pair, method) + RETURN).mkString
    val signedHeaders: List[String] = result.map(pair => pair._1.toLowerCase).toList

    val signedHeaderKeys = signedHeaders.mkString(";")
    val canonicalRequest =
      method + RETURN +
        uri + RETURN +
        queryParamsString(queryParams) + RETURN +
        headersString + RETURN +
        signedHeaderKeys + RETURN +
        toBase16(hash(payload.getOrElse(EMPTY.getBytes(StandardCharsets.UTF_8))))

    val stringToSign = createStringToSign(canonicalRequest, now)
    val signature = sign(stringToSign, now, credentials)
    val authorizationHeader = AWS4_HMAC_SHA256_CREDENTIAL +
      credentials.accessKeyId() + SLASH + getCredentialScope(now) +
      SIGNED_HEADERS + signedHeaderKeys +
      SIGNATURE + signature

    result += (AUTHORIZATION -> authorizationHeader)

    result
  }

}
