/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.functional.syntax.{toFunctionalBuilderOps, unlift}
import play.api.libs.json.{OFormat, Writes, __}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class PlatformInitiative(
   initiativeName: String,
   initiativeDescription: String,
   currentProgress: Int,
   targetProgress: Int,
   completedLegend: String,
   inProgressLegend: String
 )

object PlatformInitiative {
  implicit val format: OFormat[PlatformInitiative] = {
    ((__ \ "initiativeName").format[String]
      ~ (__ \ "initiativeDescription").format[String]
      ~ (__ \ "currentProgress").format[Int]
      ~ (__ \ "targetProgress").format[Int]
      ~ (__ \ "completedLegend").format[String]
      ~ (__ \ "inProgressLegend").format[String]
      ) (apply, unlift(unapply))
  }
  implicit val writes: Writes[PlatformInitiative] = {
    ((__ \ "initiativeName").write[String]
      ~ (__ \ "initiativeDescription").write[String]
      ~ (__ \ "currentProgress").write[Int]
      ~ (__ \ "targetProgress").write[Int]
      ~ (__ \ "completedLegend").format[String]
      ~ (__ \ "inProgressLegend").format[String]
      ) (unlift(unapply))
  }
}

class PlatformInitiativesConnector @Inject()(
  http: HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext) {

  private val platformInitiativesBaseUrl: String =
    servicesConfig.baseUrl("platform-initiatives")

  def allInitiatives(implicit hc: HeaderCarrier): Future[Seq[PlatformInitiative]] = {
    http.GET[Seq[PlatformInitiative]](url"$platformInitiativesBaseUrl/platform-initiatives/initiatives")
  }
}

object PlatformInitiativesConnector {
  sealed trait PlatformInitiativesError

  case class HTTPError(code: Int) extends PlatformInitiativesError

  case class ConnectionError(exception: Throwable) extends PlatformInitiativesError {
    override def toString: String = exception.getMessage
  }
}
