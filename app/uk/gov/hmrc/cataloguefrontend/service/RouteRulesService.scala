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
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.connector.RouteRulesConnector
import uk.gov.hmrc.cataloguefrontend.connector.RouteRulesConnector.{Route, RouteType}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

class RouteRulesService @Inject()(
)(using ExecutionContext):

  import RouteRulesService._

  def serviceRoutes(routes: Seq[Route])(using HeaderCarrier): ServiceRoutes =
    // exclude new Devhub route type for now
    ServiceRoutes(routes.filterNot(_.routeType == RouteType.Devhub))

@Singleton
object RouteRulesService:
  case class ServiceRoutes(
    routes: Seq[Route]
  ):

    private val envRoutes =
      routes.groupBy(_.environment)

    private[service] val referenceRoutes: Seq[Route] =
      envRoutes
        .getOrElse(
          Environment.Production,
          envRoutes.values.headOption.getOrElse(Seq.empty)
        )

    private def hasDifferentPaths(envRoutes: Seq[Route], refRoutes: Seq[Route]): Boolean =
      envRoutes
        .map(_.path)
        .diff(refRoutes.map(_.path))
        .nonEmpty

    private def filterDifferences(envRoutes: Seq[Route], refRoutes: Seq[Route]): Seq[Route] =
      envRoutes
        .filter: r =>
          envRoutes
            .map(_.path)
            .diff(refRoutes.map(_.path))
            .contains(r.path)

    val inconsistentRoutes: Seq[Route] =
      envRoutes
        .collect:
          case (env, routes) if env != Environment.Production && hasDifferentPaths(routes, referenceRoutes) =>
           filterDifferences(routes, referenceRoutes)
        .flatten
        .toSeq

    val hasInconsistentRoutes: Boolean =
      inconsistentRoutes.nonEmpty

end RouteRulesService
