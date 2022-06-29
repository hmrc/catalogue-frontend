/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.cataloguefrontend.connector.ConfigConnector
import uk.gov.hmrc.cataloguefrontend.whatsrunningwhere.model.ServiceDeploymentConfigSummary

import javax.inject.Inject
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class WhatsRunningWhereService @Inject()(releasesConnector: ReleasesConnector, configConnector: ConfigConnector) {

  def releasesForProfile(profile: Option[Profile])(implicit hc: HeaderCarrier): Future[Seq[WhatsRunningWhere]] =
    releasesConnector.releases(profile)

  def profiles(implicit hc: HeaderCarrier): Future[Seq[Profile]] =
    releasesConnector.profiles

  def releasesForService(service: String)(implicit hc: HeaderCarrier): Future[WhatsRunningWhere] =
    releasesConnector.releasesForService(service)

  def allReleases(releases: Seq[WhatsRunningWhere])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[ServiceDeploymentConfigSummary]] = {
    val releasesPerEnv = releases.map(r => (r.applicationName.asString, r.versions.map(v => v.environment.asString))).toMap
    configConnector.allDeploymentConfig
      .map(_.filter(config => {
        releasesPerEnv.getOrElse(config.serviceName, List.empty).contains(config.environment)
      }
      ).groupBy(_.serviceName)
        .map(service => ServiceDeploymentConfigSummary(service._1, service._2))
        .toSeq
      )
  }
}

class WhatsRunningWhereServiceConfig @Inject()(configuration: Configuration) {
  def maxMemoryAmount: Double =
    configuration
      .get[Double]("whats-running-where.max-memory")
}
