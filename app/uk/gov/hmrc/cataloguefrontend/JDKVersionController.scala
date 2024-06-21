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

package uk.gov.hmrc.cataloguefrontend
import javax.inject.{Inject, Singleton}
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.{SlugInfoFlag, TeamName}
import uk.gov.hmrc.cataloguefrontend.service.DependenciesService
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.{JdkAcrossEnvironmentsPage, JdkVersionPage}

import cats.implicits._

import scala.concurrent.ExecutionContext

@Singleton
class JDKVersionController @Inject() (
  override val mcc   : MessagesControllerComponents,
  dependenciesService: DependenciesService,
  teamsReposConnector: TeamsAndRepositoriesConnector,
  jdkPage            : JdkVersionPage,
  jdkCountsPage      : JdkAcrossEnvironmentsPage,
  override val auth  : FrontendAuthComponents
)(implicit
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders {

  def findLatestVersions(flag: String, teamName: Option[TeamName]) =
    BasicAuthAction.async { implicit request =>
      for {
        teams            <- teamsReposConnector.allTeams()
        selectedFlag     =  SlugInfoFlag.parse(flag.toLowerCase).getOrElse(SlugInfoFlag.Latest)
        selectedTeamName =  teamName.flatMap(n => teams.find(_.name == n)).map(_.name)
        jdkVersions      <- dependenciesService.getJDKVersions(selectedFlag, selectedTeamName)
      } yield Ok(jdkPage(jdkVersions.sortBy(_.version), SlugInfoFlag.values, teams, selectedFlag, selectedTeamName))
    }

  def compareAllEnvironments(teamName: Option[TeamName]) =
    BasicAuthAction.async { implicit request =>
      for {
        teams            <- teamsReposConnector.allTeams()
        selectedTeamName =  teamName.flatMap(n => teams.find(_.name == n)).map(_.name)
        envs             <- SlugInfoFlag.values.traverse(env => dependenciesService.getJDKCountsForEnv(env, selectedTeamName))
        jdks             =  envs.flatMap(_.usage.keys).distinct.sortBy(_.version)
      } yield Ok(jdkCountsPage(envs, jdks, teams, selectedTeamName))
    }
}
