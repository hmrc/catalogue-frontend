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

package uk.gov.hmrc.cataloguefrontend.deployments

import cats.implicits._
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{ServiceDependenciesConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.service.ServiceDependencies
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.deployments.DeploymentTimelinePage

import java.time.{LocalDate, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeploymentTimelineController @Inject()(
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  serviceDependenciesConnector : ServiceDependenciesConnector,
  deploymentGraphService       : DeploymentGraphService,
  page                         : DeploymentTimelinePage,
  override val mcc             : MessagesControllerComponents,
  override val auth            : FrontendAuthComponents
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {


  def graph(service: String, to: LocalDate, from: LocalDate) = BasicAuthAction.async { implicit request =>
    val start  = to.atStartOfDay().toInstant(ZoneOffset.UTC)
    val end    = from.atTime(23,59,59).toInstant(ZoneOffset.UTC)

    for {
      services    <- teamsAndRepositoriesConnector.allServices()
      serviceNames = services.map(_.name.toLowerCase).sorted
      data        <- deploymentGraphService.findEvents(service, start, end)
      slugInfo    <- data
                      .groupBy(_.version)
                      .keys
                      .toList
                      .foldLeftM[Future, List[ServiceDependencies]](List.empty){
                        case (xs, v) => serviceDependenciesConnector.getSlugInfo(service, Some(v)).map {
                          case Some(x) => xs :+ x
                          case None    => xs
                        }
                      }
      view         = page(service, start, end, data, slugInfo, serviceNames)
    } yield Ok(view)
  }
}
