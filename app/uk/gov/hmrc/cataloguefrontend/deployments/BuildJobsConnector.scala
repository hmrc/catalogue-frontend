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

package uk.gov.hmrc.cataloguefrontend.deployments

import play.api.{Configuration, Logging}
import play.api.libs.ws.writeableOf_urlEncodedForm
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.play.bootstrap.binders.SafeRedirectUrl
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, Version}
import uk.gov.hmrc.internalauth.client.Retrieval

import java.net.URL
import javax.inject.{Inject, Singleton}
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BuildJobsConnector @Inject()(
  httpClientV2: HttpClientV2,
  config      : Configuration
)(implicit ec: ExecutionContext) extends Logging {

  import HttpReads.Implicits._

  private val authorizationHeader = {
    val username = config.get[String]("jenkins.buildjobs.username")
    val token    = config.get[String]("jenkins.buildjobs.token")
    s"Basic ${java.util.Base64.getEncoder().encodeToString(s"$username:$token".getBytes("UTF-8"))}"
  }

  private val baseUrl = config.get[String]("jenkins.buildjobs.url")

  def deployMicroservice(
    serviceName: ServiceName
  , version    : Version
  , environment: Environment
  , user       : Retrieval.Username
  )(implicit hc: HeaderCarrier): Future[String] = {
    val url = new URL(s"$baseUrl/job/build-and-deploy/job/deploy-microservice/buildWithParameters")

    implicit val locationRead: HttpReads[String] =
      HttpReads[HttpResponse].map(_.header("Location") match {
        case Some(url) if baseUrl.startsWith("https:") => url.replace("http:", "https:") // Jenkins requires this switch
        case Some(url)                                 => url                            // It should not happen for acceptance tests
        case None                                      => sys.error(s"Could not find Location header for: $url")
      })

    httpClientV2
      .post(url)
      .setHeader(
        HeaderNames.AUTHORIZATION -> authorizationHeader
      , HeaderNames.CONTENT_TYPE -> "application/x-www-form-urlencoded"
      )
      .withBody(Map(
        "SERVICE"         -> Seq(serviceName.asString)
      , "SERVICE_VERSION" -> Seq(version.original)
      , "ENVIRONMENT"     -> Seq(environment.asString)
      , "DEPLOYER_ID"     -> Seq(user.value)
      ))
      .execute[Either[UpstreamErrorResponse, String]]
      .flatMap {
        case Right(res) => Future.successful(res)
        case Left(err)  => Future.failed(new RuntimeException(s"Call to $url failed with upstream error: ${err.message}"))
      }
  }

  def queueStatus(queueUrl: SafeRedirectUrl)(implicit hc: HeaderCarrier): Future[BuildJobsConnector.QueueStatus] = {
    val url = url"${queueUrl.url}api/json?tree=cancelled,executable[number,url]"
    implicit val r = BuildJobsConnector.QueueStatus.format

    httpClientV2
      .get(url)
      .setHeader("Authorization" -> authorizationHeader)
      .execute[Either[UpstreamErrorResponse, BuildJobsConnector.QueueStatus]]
      .flatMap {
        case Right(res) => Future.successful(res)
        case Left(err)  => Future.failed(new RuntimeException(s"Call to $url failed with upstream error: ${err.message}"))
      }
  }

  def buildStatus(buildUrl: SafeRedirectUrl)(implicit hc: HeaderCarrier): Future[BuildJobsConnector.BuildStatus] = {
    val url = url"${buildUrl.url}api/json?tree=number,url,timestamp,result"
    implicit val r = BuildJobsConnector.BuildStatus.format
    httpClientV2
      .post(url)
      .setHeader("Authorization" -> authorizationHeader)
      .execute[Either[UpstreamErrorResponse, BuildJobsConnector.BuildStatus]]
      .flatMap {
        case Right(res) => Future.successful(res)
        case Left(err)  => Future.failed(new RuntimeException(s"Call to $url failed with upstream error: ${err.message}"))
      }
  }
}

object BuildJobsConnector {
  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  case class QueueStatus(cancelled: Option[Boolean], executable: Option[QueueExecutable])
  case class QueueExecutable(number: Int, url: String)

  object QueueStatus {
    val format: Format[QueueStatus] = {
      implicit val f: Format[QueueExecutable] =
        ( (__ \ "number").format[Int]
        ~ (__ \ "url"   ).format[String]
        )(QueueExecutable.apply, qe => Tuple.fromProductTyped(qe))

      ( (__ \ "cancelled" ).formatNullable[Boolean]
      ~ (__ \ "executable").formatNullable[QueueExecutable]
      )(QueueStatus.apply, qs => Tuple.fromProductTyped(qs))
    }
  }

  case class BuildStatus(
    number     : Int,
    url        : String,
    timestamp  : Instant,
    result     : Option[String],
    description: Option[String]
  )
  object BuildStatus {
    val format: OFormat[BuildStatus] =
      ( (__ \ "number"     ).format[Int]
      ~ (__ \ "url"        ).format[String]
      ~ (__ \ "timestamp"  ).format[Instant]
      ~ (__ \ "result"     ).formatNullable[String]
      ~ (__ \ "description").formatNullable[String]
      )(BuildStatus.apply, bs => Tuple.fromProductTyped(bs))
  }
}
