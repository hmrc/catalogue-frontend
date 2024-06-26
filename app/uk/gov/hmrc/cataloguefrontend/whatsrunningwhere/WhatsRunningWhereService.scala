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

package uk.gov.hmrc.cataloguefrontend.whatsrunningwhere

import play.api.Configuration
import uk.gov.hmrc.cataloguefrontend.cost.DeploymentSize
import uk.gov.hmrc.cataloguefrontend.model.{Environment, ServiceName}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class WhatsRunningWhereService @Inject()(
  releasesConnector      : ReleasesConnector,
  serviceConfigsConnector: ServiceConfigsConnector
)(using ExecutionContext):

  def releasesForProfile(profile: Option[Profile])(using HeaderCarrier): Future[Seq[WhatsRunningWhere]] =
    releasesConnector.releases(profile)

  def profiles()(using HeaderCarrier): Future[Seq[Profile]] =
    releasesConnector.profiles()

  def releasesForService(service: ServiceName)(using HeaderCarrier): Future[WhatsRunningWhere] =
    releasesConnector.releasesForService(service)

  def allDeploymentConfigs(releases: Seq[WhatsRunningWhere])(using HeaderCarrier): Future[Seq[ServiceDeploymentConfigSummary]] =
    val releasesPerEnv = releases.map(r => (r.serviceName, r.versions.map(_.environment))).toMap
    serviceConfigsConnector.deploymentConfig()
      .map:
        _
          .filter: config =>
            releasesPerEnv.getOrElse(config.serviceName, List.empty).contains(config.environment)
          .groupBy(_.serviceName)
          .map: (serviceName, deploymentConfigs) =>
            ServiceDeploymentConfigSummary(serviceName, deploymentConfigs.groupBy(_.environment).view.mapValues(_.head.deploymentSize).toMap)
          .toSeq

end WhatsRunningWhereService

class WhatsRunningWhereServiceConfig @Inject()(
  configuration: Configuration
):
  def maxMemoryAmount: Double =
    configuration
      .get[Double]("whats-running-where.max-memory")

case class ServiceDeploymentConfigSummary(
  serviceName    : ServiceName,
  deploymentSizes: Map[Environment, DeploymentSize]
)
