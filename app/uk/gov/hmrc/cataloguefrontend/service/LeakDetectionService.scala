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
import uk.gov.hmrc.cataloguefrontend.connector.{LeakDetectionConnector, RepositoryWithLeaks, Team}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails

import scala.collection.JavaConverters._

@Singleton
class LeakDetectionService @Inject()(leakDetectionConnector: LeakDetectionConnector, configuration: Configuration) {
  def urlIfLeaksFound(repoName: String)(implicit hc: HeaderCarrier): Future[Option[String]] =
    repositoriesWithLeaks.map { reposWithLeaks =>
      if (hasLeaks(reposWithLeaks)(repoName)) {
        Some(s"$leakDetectionPublicUrl/reports/repositories/$repoName")
      } else {
        None
      }
    }

  private val leakDetectionPublicUrl: String = configuration.get[String]("lds.publicUrl")

  private val ldsIntegrationEnabled: Boolean = configuration.get[Boolean]("lds.integrationEnabled")

  val repositoriesToIgnore: List[String] =
    configuration.underlying.getStringList("lds.noWarningsOn").asScala.toList

  def repositoriesWithLeaks(implicit hc: HeaderCarrier): Future[Seq[RepositoryWithLeaks]] =
    if (ldsIntegrationEnabled) {
      leakDetectionConnector.repositoriesWithLeaks
    } else {
      Future.successful(Nil)
    }

  def hasLeaks(reposWithLeaks: Seq[RepositoryWithLeaks])(repoName: String): Boolean =
    reposWithLeaks.exists(_.name == repoName)

  def teamHasLeaks(team: Team, reposWithLeaks: Seq[RepositoryWithLeaks]): Boolean = {
    val teamRepos: Seq[String] = team.repos
      .map(_.values.toList.flatten)
      .getOrElse(Nil)
      .filterNot(repositoriesToIgnore.contains)
    teamRepos.intersect(reposWithLeaks.map(_.name)).nonEmpty
  }

}
