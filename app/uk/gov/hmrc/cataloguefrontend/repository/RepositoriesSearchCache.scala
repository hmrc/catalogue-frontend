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

package uk.gov.hmrc.cataloguefrontend.repository

import akka.Done
import play.api.{Configuration, cache}
import play.api.cache.AsyncCacheApi
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.connector.{GitRepository, Team, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration

@Singleton
class RepositoriesSearchCache @Inject() (
  cache                         : AsyncCacheApi,
  config                        : Configuration,
  teamsAndRepositoriesConnector : TeamsAndRepositoriesConnector
)(implicit ec : ExecutionContext) {

  implicit val hc = HeaderCarrier()
  val cacheExpiry: Duration = config.get[Duration]("repositories.cache.expiry")

  def getReposOrElseUpdate(): Future[Seq[GitRepository]] =
    cache.getOrElseUpdate[Seq[GitRepository]]("allRepos", cacheExpiry) {
      teamsAndRepositoriesConnector.allRepositories
        .map(_.sortBy(_.name.toLowerCase))
    }

  def getTeamsOrElseUpdate(): Future[Seq[Team]] =
    cache.getOrElseUpdate[Seq[Team]]("allTeams", cacheExpiry) {
      teamsAndRepositoriesConnector.allTeams
        .map(_.sortBy(_.name.asString.toLowerCase))
    }

}
