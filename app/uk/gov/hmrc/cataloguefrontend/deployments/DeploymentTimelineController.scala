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
import uk.gov.hmrc.cataloguefrontend.connector.{RepoType, ServiceDependenciesConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.service.ServiceDependencies
import uk.gov.hmrc.cataloguefrontend.util.DateHelper.{atStartOfDayInstant, atEndOfDayInstant}
import uk.gov.hmrc.cataloguefrontend.view.deployments.html.DeploymentTimelinePage
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.DeploymentTimelineEvent
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeploymentTimelineController @Inject()(
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  serviceDependenciesConnector : ServiceDependenciesConnector,
  deploymentGraphService       : DeploymentGraphService,
  deploymentTimelinePage       : DeploymentTimelinePage,
  override val mcc             : MessagesControllerComponents,
  override val auth            : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  def graph(service: Option[ServiceName], to: LocalDate, from: LocalDate) =
    BasicAuthAction.async { implicit request =>
      val start  = to.atStartOfDayInstant
      val end    = from.atEndOfDayInstant

      for
        services     <- teamsAndRepositoriesConnector.allRepositories(repoType = Some(RepoType.Service))
        serviceNames =  services.map(_.name.toLowerCase).sorted
        data         <- service.fold(Future.successful(Seq.empty[DeploymentTimelineEvent])): serviceName =>
                          deploymentGraphService.findEvents(serviceName, start, end).map(_.filter(_.env != Environment.Integration)) // filter as only platform teams are interested in this env
        slugInfo     <- data
                          .groupBy(_.version)
                          .keys
                          .toList
                          .foldLeftM[Future, Seq[ServiceDependencies]](Seq.empty): (xs, v) =>
                            service
                              .fold(Future.successful(Option.empty[ServiceDependencies])): serviceName =>
                                serviceDependenciesConnector.getSlugInfo(serviceName, Some(v))
                              .map:
                                xs ++ _.toSeq
        view         =  deploymentTimelinePage(service, start, end, data, slugInfo, serviceNames)
      yield Ok(view)
    }
