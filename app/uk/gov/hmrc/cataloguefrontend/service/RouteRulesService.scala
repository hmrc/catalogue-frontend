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

package uk.gov.hmrc.cataloguefrontend.service

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cataloguefrontend.model.ServiceName
import uk.gov.hmrc.cataloguefrontend.connector.RouteRulesConnector
import uk.gov.hmrc.cataloguefrontend.connector.RouteRulesConnector.EnvironmentRoute
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class RouteRulesService @Inject() (
  routeRulesConnector: RouteRulesConnector
)(using ExecutionContext) {
  import RouteRulesService._

  def serviceRoutes(serviceName: ServiceName)(using HeaderCarrier): Future[ServiceRoutes] =
    for {
      frontendRoutes <- routeRulesConnector.frontendRoutes(serviceName)
      adminRoutes    <- routeRulesConnector.adminFrontendRoutes(serviceName)
    } yield ServiceRoutes(frontendRoutes ++ adminRoutes)
}

@Singleton
object RouteRulesService {
  case class ServiceRoutes(environmentRoutes: Seq[EnvironmentRoute]) {
    private val normalisedEvironmentRoutes = // this should probably be a Map[Environment, Route] - we would need to move isAdmin onto the Route to remove EnvironmentRoute
      environmentRoutes
        .groupBy(_.environment)
        .map { case (env, routes) => EnvironmentRoute(
                                       environment = env
                                     , routes      = routes.flatMap(_.routes)
                                     )
        }.toSeq

    private[service] val referenceEnvironmentRoutes: Option[EnvironmentRoute] =
      normalisedEvironmentRoutes
        .find(_.environment == "production")
        .orElse(normalisedEvironmentRoutes.headOption)
        .orElse(None)

    private def hasDifferentRoutesToReferenceEnvironment(environmentRoute: EnvironmentRoute, referenceEnvironmentRoute: EnvironmentRoute) =
      environmentRoute.routes
        .map(_.frontendPath)
        .diff(referenceEnvironmentRoute.routes.map(_.frontendPath))
        .nonEmpty

    private def filterRoutesToDifferences(environmentRoute: EnvironmentRoute, referenceEnvironmentRoute: EnvironmentRoute) =
      environmentRoute.routes
        .filter(r =>
          environmentRoute.routes
            .map(_.frontendPath)
            .diff(referenceEnvironmentRoute.routes.map(_.frontendPath))
            .contains(r.frontendPath)
        )

    val inconsistentRoutes: Seq[EnvironmentRoute] =
      referenceEnvironmentRoutes
        .map { refEnvRoutes =>
          normalisedEvironmentRoutes
            .filter(_.environment != refEnvRoutes.environment)
            .filter(environmentRoute => hasDifferentRoutesToReferenceEnvironment(environmentRoute, refEnvRoutes))
            .map(environmentRoute => environmentRoute.copy(routes = filterRoutesToDifferences(environmentRoute, refEnvRoutes)))
        }
        .getOrElse(Nil)

    val hasInconsistentRoutes: Boolean = inconsistentRoutes.nonEmpty

    val isDefined: Boolean = environmentRoutes.nonEmpty
  }
}
