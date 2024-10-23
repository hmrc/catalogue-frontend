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

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.service.SearchByUrlService.{FrontendRoute, FrontendRoutes}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class SearchByUrlConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using ExecutionContext):
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  private given Reads[FrontendRoute] =
    ( (__ \ "frontendPath"        ).read[String]
    ~ (__ \ "ruleConfigurationUrl").readWithDefault[String]("")
    ~ (__ \ "isRegex"             ).readWithDefault[Boolean](false)
    ~ (__ \ "isDevhub"            ).readWithDefault[Boolean](false)
    )(FrontendRoute.apply)

  private given Reads[FrontendRoutes] =
    ( (__ \ "service"    ).read[ServiceName]
    ~ (__ \ "environment").read[Environment]
    ~ (__ \ "routes"     ).read[Seq[FrontendRoute]]
    )(FrontendRoutes.apply)

  private val baseUrl = servicesConfig.baseUrl("service-configs")

  def search(term: String)(using HeaderCarrier): Future[Seq[FrontendRoutes]] =
    val url = url"$baseUrl/service-configs/frontend-routes/search?frontendPath=$term"
    httpClientV2
      .get(url)
      .execute[Seq[FrontendRoutes]]
      .recover:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty
