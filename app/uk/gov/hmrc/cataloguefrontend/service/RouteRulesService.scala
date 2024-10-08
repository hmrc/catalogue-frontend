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

@Singleton
class RouteRulesService @Inject()(
)(using ExecutionContext):

  import RouteRulesService._

  // TODO replace with
  // def inconsistentRoutes(routes: Seq[Route])(using HeaderCarrier): Seq[Route] =

  def serviceRoutes(routes: Seq[Route])(using HeaderCarrier): ServiceRoutes =
    // exclude new Devhub route type for now
    ServiceRoutes(routes.filterNot(_.routeType == RouteType.Devhub))


object RouteRulesService:
  case class ServiceRoutes(
    routes: Seq[Route]
  ):

    private val referenceRoutes: Seq[Route] =
      for
        referenceEnv <- routes.map(_.environment).sorted.reverse.headOption.toSeq
        routes       <- routes.filter(_.environment == referenceEnv)
      yield routes

    val inconsistentRoutes =
      routes.groupBy(_.environment)
        .collect:
          case (_, envRoutes) =>
            val differentPaths =
              envRoutes
                .map(_.path)
                .diff(referenceRoutes.map(_.path))
            envRoutes.filter: r =>
              differentPaths.contains(r.path)
        .flatten
        .toSeq

    val hasInconsistentRoutes: Boolean =
      inconsistentRoutes.nonEmpty

end RouteRulesService
