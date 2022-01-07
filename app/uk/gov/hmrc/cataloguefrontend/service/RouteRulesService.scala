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

package uk.gov.hmrc.cataloguefrontend.service

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.connector.RouteRulesConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class RouteRulesService @Inject() (
  routeRulesConnector: RouteRulesConnector
)(implicit val ec: ExecutionContext) {
  import RouteRulesService._

  def serviceRoutes(serviceName: String)(implicit hc: HeaderCarrier): Future[ServiceRoutes] =
    routeRulesConnector.serviceRoutes(serviceName).map(environmentRoutes => ServiceRoutes(environmentRoutes))

  def serviceUrl(serviceName: String, environment: String = "production")(implicit hc: HeaderCarrier): Future[Option[EnvironmentRoute]] =
    routeRulesConnector
      .serviceRoutes(serviceName)
      .map(environmentRoutes =>
        environmentRoutes
          .find(environmentRoute => environmentRoute.environment == environment)
      )
}

@Singleton
object RouteRulesService {
  case class ServiceRoutes(environmentRoutes: Seq[EnvironmentRoute]) {
    private[service] val referenceEnvironmentRoutes: Option[EnvironmentRoute] =
      environmentRoutes
        .find(environmentRoute => environmentRoute.environment == "production")
        .orElse(environmentRoutes.headOption)
        .orElse(None)

    private def hasDifferentRoutesToReferenceEnvironment(environmentRoute: EnvironmentRoute, referenceEnvironmentRoute: EnvironmentRoute) =
      environmentRoute.routes
        .map(route => route.frontendPath)
        .diff(referenceEnvironmentRoute.routes.map(route => route.frontendPath))
        .nonEmpty

    private def filterRoutesToDifferences(environmentRoute: EnvironmentRoute, referenceEnvironmentRoute: EnvironmentRoute) =
      environmentRoute.routes
        .filter(r =>
          environmentRoute.routes
            .map(route => route.frontendPath)
            .diff(referenceEnvironmentRoute.routes.map(route => route.frontendPath))
            .contains(r.frontendPath)
        )

    val inconsistentRoutes: Seq[EnvironmentRoute] =
      referenceEnvironmentRoutes
        .map { refEnvRoutes =>
          environmentRoutes
            .filter(environmentRoute => environmentRoute.environment != refEnvRoutes.environment)
            .filter(environmentRoute => hasDifferentRoutesToReferenceEnvironment(environmentRoute, refEnvRoutes))
            .map(environmentRoute => environmentRoute.copy(routes = filterRoutesToDifferences(environmentRoute, refEnvRoutes)))
        }
        .getOrElse(Nil)

    val hasInconsistentRoutes: Boolean = inconsistentRoutes.nonEmpty

    val isDefined: Boolean = environmentRoutes.nonEmpty
  }

  case class EnvironmentRoute(environment: String, routes: Seq[Route])

  case class Route(frontendPath: String, backendPath: String, ruleConfigurationUrl: String, isRegex: Boolean = false)

  case class EnvironmentUrl(environment: String, url: String)
}
