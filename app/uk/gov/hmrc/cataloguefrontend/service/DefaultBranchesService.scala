/*
 * Copyright 2021 HM Revenue & Customs
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

import uk.gov.hmrc.cataloguefrontend.connector.RepositoryDisplayDetails
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject._
import scala.concurrent.ExecutionContext
import scala.language.postfixOps

@Singleton
class DefaultBranchesService @Inject()()(implicit val ec: ExecutionContext){

  def allTeams(repos: Seq[RepositoryDisplayDetails]): Seq[String] = {
    val repositories = repos.map(t => t.teamNames)
    repositories.flatten.distinct.sorted
  }

  def updatedDefaultBranchCount(
      repos:            Seq[RepositoryDisplayDetails],
      name:             Option[String],
      defaultBranch:    Option[String],
      teamNames:        Option[String],
      singleOwnership:  Option[Boolean],
      archived:         Option[Boolean]): Int = {

    var results: Seq[RepositoryDisplayDetails] = repos

    if(name.isDefined)              { results = results.filter(_.name contains name.get) }
    if(defaultBranch.isDefined)     { results = results.filter(_.defaultBranch contains defaultBranch.get) }
    if(teamNames.isDefined)         { results = results.filter(_.teamNames contains teamNames.get) }
    if(singleOwnership.isDefined)   { results = results.filter(_.teamNames.length == 1) }
    if(archived.isEmpty)            { results = results.filter(_.isArchived == false) }

    results.map(r => r.defaultBranch).count(_ != "master")
  }

  def filterRepositories(
            repositories:     Seq[RepositoryDisplayDetails],
            name:             Option[String],
            defaultBranch:    Option[String],
            teamNames:        Option[String],
            singleOwnership:  Option[Boolean],
            archived:         Option[Boolean]): Seq[RepositoryDisplayDetails] = {

    var results: Seq[RepositoryDisplayDetails] = repositories

    if(name.isDefined)              { results = results.filter(_.name contains name.get) }
    if(defaultBranch.isDefined)     { results = results.filter(_.defaultBranch contains defaultBranch.get) }
    if(teamNames.isDefined)         { results = results.filter(_.teamNames contains teamNames.get) }
    if(singleOwnership.isDefined)   { results = results.filter(_.teamNames.length == 1) }
    if(archived.isEmpty)            { results = results.filter(_.isArchived == false) }

    results
  }
}
