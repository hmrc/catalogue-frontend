/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.Configuration
import scala.concurrent.Future
import uk.gov.hmrc.cataloguefrontend.Team
import uk.gov.hmrc.cataloguefrontend.connector.{LeakDetectionConnector, RepositoryWithLeaks}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

@Singleton
class LeakDetectionService @Inject()(leakDetectionConnector: LeakDetectionConnector, configuration: Configuration) {
  def urlIfLeaksFound(repoName: String)(implicit hc: HeaderCarrier): Future[Option[String]] =
    repositoriesWithLeaks.map { reposWithLeaks =>
      if (hasLeaks(reposWithLeaks)(repoName)) {
        Some(s"$leakDetectionUrl/reports/repositories/$repoName")
      } else {
        None
      }
    }

  private def leakDetectionUrl: String =
    if (leakDetectionConnector.url.contains("localhost")) {
      leakDetectionConnector.url
    } else {
      productionLeakDetectionUrl
    }

  private val productionLeakDetectionUrl = {
    val key = "microservice.services.leak-detection.productionUrl"
    configuration
      .getString(key)
      .getOrElse(
        throw new Exception(s"Failed reading from config, expected to find: $key")
      )
  }

  private val ldsIntegrationEnabled: Boolean = {
    val key = "ldsIntegration.enabled"
    configuration
      .getBoolean(key)
      .getOrElse(
        throw new Exception(s"Failed reading from config, expected to find: $key")
      )
  }

  def repositoriesWithLeaks(implicit hc: HeaderCarrier): Future[Seq[RepositoryWithLeaks]] =
    if (ldsIntegrationEnabled) {
      leakDetectionConnector.repositoriesWithLeaks
    } else {
      Future.successful(Nil)
    }

  def hasLeaks(reposWithLeaks: Seq[RepositoryWithLeaks])(repoName: String): Boolean =
    reposWithLeaks.exists(_.name == repoName)

  def isTeamResponsibleForRepo(contributes: Boolean, owns: Boolean, ownedByOthers: Boolean): Boolean =
    owns || (contributes && !ownedByOthers)

  def teamHasLeaks(team: Team, allTeamsInfo: Seq[Team], reposWithLeaks: Seq[RepositoryWithLeaks]): Boolean = {
    val teamRepos                  = team.repos.map(_.values.toList.flatten).getOrElse(Nil)
    val reposOwnDirectly           = team.ownedRepos
    val reposOwnedBySomeone        = allTeamsInfo.flatMap(_.ownedRepos)
    val reposToCheck: List[String] = (teamRepos ++ reposOwnDirectly).intersect(reposWithLeaks.map(_.name))

    reposToCheck
      .exists(
        repo =>
          isTeamResponsibleForRepo(
            contributes   = teamRepos.contains(repo),
            owns          = team.ownedRepos.contains(repo),
            ownedByOthers = reposOwnedBySomeone.contains(repo)
        ))
  }

}
