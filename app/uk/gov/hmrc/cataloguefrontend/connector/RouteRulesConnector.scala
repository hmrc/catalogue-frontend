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

import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class RouteRulesConnector @Inject() (
  httpClientV2  : HttpClientV2,
  servicesConfig: ServicesConfig
)(using ExecutionContext):
  import HttpReads.Implicits._
  import RouteRulesConnector._

  private val logger = Logger(getClass)

  private val baseUrl: String = servicesConfig.baseUrl("service-configs")

  def frontendServices()(using HeaderCarrier): Future[Seq[String]] =
    val url = url"$baseUrl/service-configs/frontend-services"
    httpClientV2.get(url)
      .execute[Seq[String]]
      .recover:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty

  def frontendRoutes(service: ServiceName)(using HeaderCarrier): Future[Seq[EnvironmentRoute]] =
    val url = url"$baseUrl/service-configs/frontend-route/${service.asString}"
    given Reads[EnvironmentRoute] = EnvironmentRoute.reads
    httpClientV2
      .get(url)
      .execute[Seq[EnvironmentRoute]]
      .recover:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty

  def adminFrontendRoutes(service: ServiceName)(using HeaderCarrier): Future[Seq[EnvironmentRoute]] =
    val url = url"$baseUrl/service-configs/admin-frontend-route/${service.asString}"
    given Reads[AdminFrontendRoute] = AdminFrontendRoute.reads
    httpClientV2
      .get(url)
      .execute[Seq[AdminFrontendRoute]]
      .map:
        _
          .flatMap: raw =>
            raw.allow.keys.map: env =>
              EnvironmentRoute(
                environment = env
              , routes      = Seq(Route(
                                frontendPath         = raw.route
                              , ruleConfigurationUrl = raw.location
                              , isRegex              = false
                              ))
              )
          .groupBy(_.environment)
          .toSeq
          .map: (k, v) =>
            EnvironmentRoute(k, v.flatMap(_.routes.sortBy(_.ruleConfigurationUrl)), isAdmin = true)
      .recover:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty

object RouteRulesConnector:
  case class Route(
    frontendPath        : String
  , ruleConfigurationUrl: String
  , isRegex             : Boolean = false
  )

  case class EnvironmentRoute(
    environment: Environment
  , routes     : Seq[Route]
  , isAdmin    : Boolean = false
  )

  object EnvironmentRoute:
    val reads: Reads[EnvironmentRoute] =
      given Reads[Route] =
        ( (__ \"frontendPath"        ).read[String]
        ~ (__ \"ruleConfigurationUrl").read[String]
        ~ (__ \"isRegex"             ).read[Boolean]
        )(Route.apply)

      ( (__ \"environment").read[Environment]
      ~ (__ \"routes"     ).read[Seq[Route]]
      ~ Reads.pure(false)
      )(EnvironmentRoute.apply)

  case class AdminFrontendRoute(
    service : ServiceName
  , route   : String
  , allow   : Map[Environment, List[String]]
  , location: String
  )

  object AdminFrontendRoute:
    val reads: Reads[AdminFrontendRoute] =
      ( (__ \"service" ).read[ServiceName]
      ~ (__ \"route"   ).read[String]
      ~ (__ \"allow"   ).read[Map[Environment, List[String]]]
      ~ (__ \"location").read[String]
      )(AdminFrontendRoute.apply)

end RouteRulesConnector
