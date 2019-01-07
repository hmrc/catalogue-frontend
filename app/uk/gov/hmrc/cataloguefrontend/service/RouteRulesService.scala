/*
 * Copyright 2019 HM Revenue & Customs
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
import scala.concurrent.ExecutionContext.Implicits.global

class RouteRulesService @Inject()(routeRulesConnector: RouteRulesConnector) {
  import RouteRulesService._

  private def resolveEnvironmentBasePath(environmentName: String) = environmentName.toLowerCase match {
    case "production" => s"https://www.tax.service.gov.uk"
    case _ => s"https://www.$environmentName.tax.service.gov.uk"
  }

  private def enrichEnvironmentBasePath(environmentRoute: EnvironmentRoute) =
    environmentRoute.copy(basePath = resolveEnvironmentBasePath(environmentRoute.environment))

  def serviceRoutes(serviceName: String)(implicit hc: HeaderCarrier) =
    routeRulesConnector.serviceRoutes(serviceName).map(environmentRoutes =>
      ServiceRoutes(environmentRoutes.map(enrichEnvironmentBasePath))
    )

  def serviceUrl(serviceName: String)(implicit hc: HeaderCarrier) =
    routeRulesConnector.serviceRoutes(serviceName).map(environmentRoutes => {
      environmentRoutes
        .find(environmentRoute => environmentRoute.environment == "production")
        .map(enrichEnvironmentBasePath)
    })
}

@Singleton
object RouteRulesService {
  case class ServiceRoutes(environmentRoutes: Seq[EnvironmentRoute]) {
    private val hasInconsistentRoutesInEnvironment =
      environmentRoutes.nonEmpty && environmentRoutes.map(environmentRoute => environmentRoute.routes.length).distinct.length != 1

    private val hasDifferentRoutesInEnvironment =
      environmentRoutes.nonEmpty &&
        environmentRoutes
          .flatMap(environmentRoute => environmentRoute.routes.map(route => route.frontendPath))
          .distinct.length != environmentRoutes.head.routes.length

    val isDefined: Boolean = environmentRoutes.nonEmpty

    val hasInconsistentUrls: Boolean = hasInconsistentRoutesInEnvironment || hasDifferentRoutesInEnvironment
  }

  type EnvironmentRoutes = Seq[EnvironmentRoute]

  case class EnvironmentRoute(environment: String, basePath: String = "", routes: Seq[Route])

  case class Route(frontendPath: String, backendPath: String, ruleConfigurationUrl: String)

  case class EnvironmentUrl(environment: String, url: String)
}
