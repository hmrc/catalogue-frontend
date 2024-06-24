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

package uk.gov.hmrc.cataloguefrontend.service

import uk.gov.hmrc.cataloguefrontend.connector.GitRepository
import uk.gov.hmrc.cataloguefrontend.model.TeamName

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class DefaultBranchesService @Inject()()(implicit val ec: ExecutionContext){

  def allTeams(repos: Seq[GitRepository]): Seq[TeamName] =
    repos.map(_.teamNames).flatten.distinct.sorted

  def filterRepositories(
    repositories:     Seq[GitRepository],
    name:             Option[String],
    defaultBranch:    Option[String],
    teamNames:        Option[TeamName],
    singleOwnership:  Boolean,
    includeArchived:  Boolean
  ): Seq[GitRepository] =
    repositories
      .filter(repo => name.fold(true)(repo.name.contains(_)))
      .filter(repo => defaultBranch.fold(true)(repo.defaultBranch.contains(_)))
      .filter(repo => teamNames.fold(true)(repo.teamNames.contains(_)))
      .filter(repo => !singleOwnership || repo.teamNames.length == 1)
      .filter(repo => includeArchived || !repo.isArchived)
}
