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
import uk.gov.hmrc.cataloguefrontend.model.{Environment, DigitalService, ServiceName, TeamName}
import uk.gov.hmrc.cataloguefrontend.serviceconfigs.ServiceConfigsConnector
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class WhatsRunningWhereService @Inject()(
  releasesConnector      : ReleasesConnector,
  serviceConfigsConnector: ServiceConfigsConnector
)(using ExecutionContext):

  def sm2Profiles()(using HeaderCarrier): Future[Seq[Profile]] =
    releasesConnector
      .profiles()
      .map(_.filter(_.profileType == ProfileType.ServiceManager))
      .map(_.sortBy(_.profileName.asString))

  def releases(
    teamName      : Option[TeamName]
  , digitalService: Option[DigitalService]
  , sm2Profile    : Option[String]
  )(using HeaderCarrier): Future[Seq[WhatsRunningWhere]] =
    for
      releasesData      <- releasesConnector.releases(teamName, digitalService, sm2Profile)
      deploymentConfigs <- serviceConfigsConnector.deploymentConfig()
      isConsulMap       =  buildIsConsulMap(deploymentConfigs)
      enrichedReleases  =  enrichReleasesWithDeploymentType(releasesData, isConsulMap)
    yield
      enrichedReleases

  private def buildIsConsulMap(deploymentConfigs: Seq[uk.gov.hmrc.cataloguefrontend.cost.DeploymentConfig]): Map[(ServiceName, Environment), Boolean] =
    deploymentConfigs
      .flatMap: config =>
        val isConsul = determineIsConsul(config)
        isConsul.map(consul => (config.serviceName, config.environment) -> consul)
      .toMap

  private def determineIsConsul(config: uk.gov.hmrc.cataloguefrontend.cost.DeploymentConfig): Option[Boolean] =
    val migrationStage = config.envVars
      .get("consul_migration_stage")
      .orElse(config.envVars.get("consul-migration-stage"))
      .orElse(config.jvm.get("consul_migration_stage"))
      .flatMap(_.toIntOption)

    migrationStage match
      case Some(2) | Some(3) => Some(true)  // Consul
      case _                 => None        // Appmesh or unknown

  private def enrichReleasesWithDeploymentType(
    releases      : Seq[WhatsRunningWhere],
    isConsulMap   : Map[(ServiceName, Environment), Boolean]
  ): Seq[WhatsRunningWhere] =
    releases.map: release =>
      val enrichedVersions = release.versions.map: version =>
        // Prefer isConsul from releases API, fallback to service-configs
        val isConsul = version.isConsul.orElse(isConsulMap.get((release.serviceName, version.environment)))
        version.copy(isConsul = isConsul)
      release.copy(versions = enrichedVersions)

  def releasesForService(service: ServiceName)(using HeaderCarrier): Future[WhatsRunningWhere] =
    for
      releaseData       <- releasesConnector.releasesForService(service)
      deploymentConfigs <- serviceConfigsConnector.deploymentConfig(service = Some(service))
      isConsulMap       =  buildIsConsulMap(deploymentConfigs)
      enrichedRelease   =  enrichReleasesWithDeploymentType(Seq(releaseData), isConsulMap).head
    yield enrichedRelease

  def allDeploymentConfigs(releases: Seq[WhatsRunningWhere])(using HeaderCarrier): Future[Seq[ServiceDeploymentConfigSummary]] =
    val releasesPerEnv = releases.map(r => (r.serviceName, r.versions.map(_.environment))).toMap

    serviceConfigsConnector
      .deploymentConfig()
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
