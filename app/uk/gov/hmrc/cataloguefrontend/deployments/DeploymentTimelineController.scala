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

import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, ServiceDependenciesConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.Environment
import uk.gov.hmrc.cataloguefrontend.service.ServiceDependencies
import uk.gov.hmrc.internalauth.client.{FrontendAuthComponents, IAAction, ResourceType, Retrieval}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.deployments.DeploymentTimelinePage
import cats.implicits._

import java.time.{LocalDate, ZoneOffset}
import javax.inject.{Inject, Singleton}
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsService
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.DeploymentTimelineEvent
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeploymentTimelineController @Inject()(
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  serviceDependenciesConnector : ServiceDependenciesConnector,
  deploymentGraphService       : DeploymentGraphService,
  serviceConfigsService        : ServiceConfigsService,
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
      services    <- teamsAndRepositoriesConnector.allRepositories(repoType = Some(RepoType.Service))
      serviceNames = services.map(_.name.toLowerCase).sorted
      data        <- deploymentGraphService.findEvents(service, start, end).map(_.filter(_.env != Environment.Integration)) // filter as only platform teams are interested in this env
      dataWithConfigChanges <- addConfigChangedFlags(service, data)
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
      view         = page(service, start, end, dataWithConfigChanges, slugInfo, serviceNames)
    } yield Ok(view)
  }

  private def addConfigChangedFlags(service: String, events: Seq[DeploymentTimelineEvent])
                                   (implicit hc: HeaderCarrier): Future[Seq[DeploymentTimelineEvent]] = {
    val updatedEventsFutures = events.zipWithIndex.map { case (event, index) =>
      val previousEventOpt = events.take(index).findLast(_.env == event.env)
      previousEventOpt match {
        case Some(previousEvent) =>
          serviceConfigsService.configChangedBetweenVersions(service, event.env, event.version, previousEvent.version).map { configChanged =>
            event.copy(configChanged = configChanged)
          }
        case None =>
          Future.successful(event)
      }
    }
    Future.sequence(updatedEventsFutures)
  }
}
