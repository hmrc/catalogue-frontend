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
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cataloguefrontend.model.ServiceName
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object RouteRulesConnector {
  case class Route(
    frontendPath        : String
  , ruleConfigurationUrl: String
  , isRegex             : Boolean = false
  )

  case class EnvironmentRoute(
    environment: String
  , routes     : Seq[RouteRulesConnector.Route]
  , isAdmin    : Boolean = false
  )

  case class AdminFrontendRoute(
    service : String
  , route   : String
  , allow   : Map[String, List[String]]
  , location: String
  )

  implicit val routeReads: Reads[Route]                           = Json.reads[Route]
  implicit val environmentRouteReads: Reads[EnvironmentRoute]     = Json.using[Json.WithDefaultValues].reads[EnvironmentRoute]
  implicit val adminFrontendRouteReads: Reads[AdminFrontendRoute] = Json.using[Json.WithDefaultValues].reads[AdminFrontendRoute]
}

@Singleton
class RouteRulesConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using ExecutionContext) {
  import HttpReads.Implicits._
  import RouteRulesConnector._

  private val logger = Logger(getClass)

  private val baseUrl: String = servicesConfig.baseUrl("service-configs")

  def frontendServices()(using HeaderCarrier): Future[Seq[String]] = {
    val url = url"$baseUrl/service-configs/frontend-services"
    httpClientV2.get(url)
      .execute[Seq[String]]
      .recover {
        case NonFatal(ex) => {
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty
        }
      }
  }

  def frontendRoutes(service: ServiceName)(using HeaderCarrier): Future[Seq[EnvironmentRoute]] = {
    val url = url"$baseUrl/service-configs/frontend-route/${service.asString}"
    httpClientV2
      .get(url)
      .execute[Seq[EnvironmentRoute]]
      .recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty
      }
    }

  def adminFrontendRoutes(service: ServiceName)(using HeaderCarrier): Future[Seq[EnvironmentRoute]] = {
    val url = url"$baseUrl/service-configs/admin-frontend-route/${service.asString}"
    httpClientV2
      .get(url)
      .execute[Seq[AdminFrontendRoute]]
      .map(
        _.flatMap(raw => raw.allow.keys.map { env => EnvironmentRoute(
          environment = env
        , routes      = Route(
                          frontendPath         = raw.route
                        , ruleConfigurationUrl = raw.location
                        , isRegex              = false
                        ) :: Nil
        )})
        .groupBy(_.environment)
        .toSeq
        .map { case (k, v) => EnvironmentRoute(k, v.flatMap(_.routes.sortBy(_.ruleConfigurationUrl)), isAdmin = true)}
      ).recover {
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty
      }
    }
}
