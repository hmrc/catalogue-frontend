/*
 * Copyright 2017 HM Revenue & Customs
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

import java.time.LocalDateTime

import uk.gov.hmrc.cataloguefrontend.DateHelper._

object SearchFiltering {

  implicit class DeploymentsResult(deployments: Seq[TeamRelease]) {

    def filter(query: DeploymentsFilter): Seq[TeamRelease] = {

      val q = if (query.isEmpty) DeploymentsFilter(from = Some(LocalDateTime.now().minusMonths(1))) else query

      deployments.toStream
        .filter(x => q.team.isEmpty || x.teams.map(_.toLowerCase).exists(_.startsWith(q.team.get.toLowerCase)))
        .filter(x => q.serviceName.isEmpty || x.name.toLowerCase.startsWith(q.serviceName.get.toLowerCase))
        .filter(x => q.from.isEmpty || x.productionDate.epochSeconds >= q.from.get.epochSeconds)
        .filter(x => q.to.isEmpty || x.productionDate.epochSeconds < q.to.get.plusDays(1).epochSeconds)

    }

  }

  implicit class RepositoryResult(repositories: Seq[RepositoryDisplayDetails]) {

    def filter(q: RepoListFilter): Seq[RepositoryDisplayDetails] = {

      repositories.toStream
        .filter(x => q.name.isEmpty || q.name.get == x.name)
        .filter(x => q.repoType.isEmpty || q.repoType.get.equalsIgnoreCase(x.repoType.toString) || ("service".equalsIgnoreCase(q.repoType.get) && x.repoType == RepoType.Service))
    }

  }


}
