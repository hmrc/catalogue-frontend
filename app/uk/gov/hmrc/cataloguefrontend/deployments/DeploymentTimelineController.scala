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

import cats.data.OptionT
import cats.implicits._
import play.api.Logging
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.{GitHubProxyConnector, RepoType, ServiceDependenciesConnector, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.deployments.view.html.{DeploymentTimelinePage, DeploymentTimelineSelectPage}
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName, Version}
import uk.gov.hmrc.cataloguefrontend.service.ServiceDependencies
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsService
import uk.gov.hmrc.cataloguefrontend.util.DateHelper.{atStartOfDayInstant, atEndOfDayInstant}
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.DeploymentTimelineEvent
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class DeploymentTimelineController @Inject()(
  serviceDependenciesConnector : ServiceDependenciesConnector
, teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
, gitHubProxyConnector         : GitHubProxyConnector
, deploymentGraphService       : DeploymentGraphService
, serviceConfigsService        : ServiceConfigsService
, deploymentTimelinePage       : DeploymentTimelinePage
, deploymentTimelineSelectPage : DeploymentTimelineSelectPage
, override val mcc             : MessagesControllerComponents
, override val auth            : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders
     with Logging:

  def graph(service: Option[ServiceName], to: LocalDate, from: LocalDate) =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      val start  = to.atStartOfDayInstant
      val end    = from.atEndOfDayInstant

      for
        services     <- teamsAndRepositoriesConnector.allRepositories(repoType = Some(RepoType.Service))
        serviceNames =  services.map(s => ServiceName(s.name))
        events       <- service match
                          case Some(service) if start.isBefore(end) =>
                            deploymentGraphService.findEvents(service, start, end).map(_.filter(_.env != Environment.Integration)) // filter as only platform teams are interested in this env
                          case _ =>
                            Future.successful(Seq.empty[DeploymentTimelineEvent])
        slugInfo     <- events
                          .groupBy(_.version)
                          .keys
                          .toList
                          .foldLeftM[Future, Seq[ServiceDependencies]](Seq.empty): (xs, v) =>
                            service
                              .fold(Future.successful(Option.empty[ServiceDependencies])): serviceName =>
                                serviceDependenciesConnector.getSlugInfo(serviceName, Some(v))
                              .map:
                                xs ++ _.toSeq
      yield Ok(deploymentTimelinePage(service, start, end, events, slugInfo, serviceNames))

  def graphSelect(serviceName: ServiceName, deploymentId: String, fromDeploymentId: Option[String]) =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      (for
         configChanges     <- OptionT(serviceConfigsService.configChanges(deploymentId, fromDeploymentId))
         previousVersion   =  configChanges.app.from.getOrElse(Version("0.1.0"))
         deployedVersion   =  configChanges.app.to
         environment       =  configChanges.env.environment
         deployedSlug      <- OptionT(serviceDependenciesConnector.getSlugInfo(serviceName, Some(deployedVersion)))
         oPreviousSlug     <- OptionT.liftF(serviceDependenciesConnector.getSlugInfo(serviceName, Some(previousVersion)))
         oGitHubCompare    <- OptionT.liftF:
                                gitHubProxyConnector
                                  .compare(serviceName.asString, v1 = previousVersion, v2 = deployedVersion)
                                  .recover:
                                    case NonFatal(ex) => logger.error(s"Could not call git compare ${ex.getMessage}", ex); None
         jvmChanges        =  (oPreviousSlug.map(_.java), deployedSlug.java)
       yield
         Ok(deploymentTimelineSelectPage(serviceName, environment, Some(previousVersion), deployedVersion, oGitHubCompare, jvmChanges, configChanges))
      ).getOrElse(NotFound("Could not compare deployments - data not found or initial deployment"))
