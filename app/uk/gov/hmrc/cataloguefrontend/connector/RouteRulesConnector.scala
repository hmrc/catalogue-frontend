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
import play.api.libs.functional.syntax.*
import play.api.libs.json.{JsResult, Reads, __}
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

  def routes(
    service    : ServiceName
  , routeType  : Option[RouteType]   = None
  , environment: Option[Environment] = None
  )(using 
    HeaderCarrier
  ): Future[Seq[Route]] =
    val url = url"$baseUrl/service-configs/routes/${service.asString}"
    given Reads[Route] = Route.reads
    httpClientV2
      .get(url)
      .execute[Seq[Route]]
      .recover:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty

  def frontendServices()(using HeaderCarrier): Future[Seq[String]] =
    val url = url"$baseUrl/service-configs/frontend-services"
    httpClientV2.get(url)
      .execute[Seq[String]]
      .recover:
        case NonFatal(ex) =>
          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Seq.empty

//  // Used by ShutterService, replace with query param for Frontend Routes only
//  def frontendRoutes(service: ServiceName)(using HeaderCarrier): Future[Seq[EnvironmentRoute]] =
//    val url = url"$baseUrl/service-configs/frontend-route/${service.asString}"
//    given Reads[EnvironmentRoute] = EnvironmentRoute.reads
//    httpClientV2
//      .get(url)
//      .execute[Seq[EnvironmentRoute]]
//      .recover:
//        case NonFatal(ex) =>
//          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
//          Seq.empty

//  def adminFrontendRoutes(service: ServiceName)(using HeaderCarrier): Future[Seq[EnvironmentRoute]] =
//    val url = url"$baseUrl/service-configs/admin-frontend-route/${service.asString}"
//    given Reads[AdminFrontendRoute] = AdminFrontendRoute.reads
//    httpClientV2
//      .get(url)
//      .execute[Seq[AdminFrontendRoute]]
//      .map:
//        _
//          .flatMap: raw =>
//            raw.allow.keys.map: env =>
//              EnvironmentRoute(
//                environment = env
//              , routes      = Seq(Route(
//                                frontendPath         = raw.route
//                              , ruleConfigurationUrl = raw.location
//                              , isRegex              = false
//                              ))
//              )
//          .groupBy(_.environment)
//          .toSeq
//          .map: (k, v) =>
//            EnvironmentRoute(k, v.flatMap(_.routes.sortBy(_.ruleConfigurationUrl)), isAdmin = true)
//      .recover:
//        case NonFatal(ex) =>
//          logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
//          Seq.empty

object RouteRulesConnector:
  import uk.gov.hmrc.cataloguefrontend.util.{FromString, FromStringEnum, Parser}
  import FromStringEnum._

  given Parser[RouteType] = Parser.parser(RouteType.values)

  enum RouteType(
    val asString     : String,
    val displayString: String
  ) extends FromString
    derives Ordering, Reads:
    case Frontend      extends RouteType(asString = "frontend"     , displayString = "Frontend"      )
    case AdminFrontend extends RouteType(asString = "adminfrontend", displayString = "Admin Frontend")
    case Devhub        extends RouteType(asString = "devhub"       , displayString = "Devhub"        )
    case ApiContext    extends RouteType(asString = "apicontext"   , displayString = "Api Context"   )

  case class Route(
    path                : String
  , ruleConfigurationUrl: Option[String]
  , isRegex             : Boolean = false
  , routeType           : RouteType
  , environment         : Environment
  )

  object Route:
    val reads: Reads[Route] =
      ( (__ \ "path"                ).read[String]
      ~ (__ \ "ruleConfigurationUrl").readNullable[String]
      ~ (__ \ "isRegex"             ).readWithDefault[Boolean](false)
      ~ (__ \ "routeType"           ).read[RouteType]
      ~ (__ \ "environment"         ).read[Environment]
      )(Route.apply)
end RouteRulesConnector
