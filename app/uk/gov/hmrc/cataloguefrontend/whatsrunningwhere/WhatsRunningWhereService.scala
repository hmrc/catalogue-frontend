/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.Inject
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class WhatsRunningWhereService @Inject()(releasesConnector: ReleasesConnector)(implicit ec: ExecutionContext) {

  /** Get releases from Heritage infrastructure. This will be removed once everything has migrated */
  def releases(profile: Option[Profile])(implicit hc: HeaderCarrier): Future[Seq[WhatsRunningWhere]] =
    releasesConnector.releases(profile)

  def ecsReleases(profile: Option[Profile])(implicit hc: HeaderCarrier): Future[Seq[WhatsRunningWhere]] =
    releasesConnector.ecsReleases(profile) map convert

  def profiles(implicit hc: HeaderCarrier): Future[Seq[Profile]] =
    releasesConnector.profiles

  private def convert(serviceDeployments: Seq[ServiceDeployment]): Seq[WhatsRunningWhere] =
    serviceDeployments
      .groupBy(_.serviceName)
      .map {
        case (serviceName, deployments) =>
          val versions = deployments.flatMap { d =>
            d.lastCompleted.map { lastCompleted =>
              WhatsRunningWhereVersion(
                environment   = d.environment,
                versionNumber = lastCompleted.version,
                lastSeen      = lastCompleted.time)
            }
          }
          WhatsRunningWhere(
            applicationName = serviceName,
            versions        = versions.toList,
            deployedIn      = ECS
          )
      }
      .toSeq
}
