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

package uk.gov.hmrc.cataloguefrontend

import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, RepoType, Team}
import uk.gov.hmrc.cataloguefrontend.repositories.RepoListFilter

object SearchFiltering {

  implicit class RepositoryResult(repositories: Seq[GitRepository]) {

    def filter(query: RepoListFilter): Seq[GitRepository] = {

      val q = query.copy(
        team     = if (query.team.contains("All")) None else query.team,
        repoType = query.repoType match {
          case None            => Some("Service")
          case Some("All")     => None
          case _               => query.repoType
        }
      )

      repositories.toStream
        .filter(x => q.name.fold(true)(name => x.name.toLowerCase.contains(name.toLowerCase)))
        .filter(x => q.team.fold(true)(team => x.teamNames.exists(_.toLowerCase.contains(team.toLowerCase))))
        .filter(x =>
          q.repoType.fold(true)(repoType =>
            repoType.equalsIgnoreCase(x.repoType.toString)
              || ("service".equalsIgnoreCase(repoType) && x.repoType == RepoType.Service)
          )
        )
    }
  }

  implicit class TeamResult(teams: Seq[Team]) {

    def filter(teamFilter: TeamFilter): Seq[Team] =
      teams.filter(team => teamFilter.name.fold(true)(name => team.name.asString.toLowerCase.contains(name.toLowerCase)))
  }

  implicit class DigitalServiceNameResult(digitalServiceNames: Seq[String]) {

    def filter(digitalServiceNameFilter: DigitalServiceNameFilter): Seq[String] =
      digitalServiceNames.filter(digitalServiceName => digitalServiceNameFilter.value.fold(true)(value => digitalServiceName.toLowerCase.contains(value.toLowerCase)))
  }
}
