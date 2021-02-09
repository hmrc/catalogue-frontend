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

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cataloguefrontend.service.RouteRulesService._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads}
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class RouteRulesConnector @Inject()(
  http          : HttpClient,
  servicesConfig: ServicesConfig
)(implicit val ec: ExecutionContext
) {
  import HttpReads.Implicits._

  private val logger = Logger(getClass)

  private val url: String = s"${servicesConfig.baseUrl("service-configs")}/frontend-route"

  implicit val routeReads: Reads[Route] = Json.reads[Route]
  implicit val environmentRouteReads: Reads[EnvironmentRoute] = Json.using[Json.WithDefaultValues].reads[EnvironmentRoute]

  def serviceRoutes(service: String)(implicit hc: HeaderCarrier): Future[EnvironmentRoutes] =
    http
      .GET[EnvironmentRoutes](url"$url/$service")
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty
      }
}
