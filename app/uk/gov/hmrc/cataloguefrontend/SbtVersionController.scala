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

import cats.implicits._
import play.api.mvc.{MessagesControllerComponents, RequestHeader}
import uk.gov.hmrc.cataloguefrontend.auth.CatalogueAuthBuilders
import uk.gov.hmrc.cataloguefrontend.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.cataloguefrontend.model.{SlugInfoFlag, TeamName, given}
import uk.gov.hmrc.cataloguefrontend.service.DependenciesService
import uk.gov.hmrc.cataloguefrontend.util.Parser
import uk.gov.hmrc.cataloguefrontend.view.html.{SbtVersionPage, SbtAcrossEnvironmentsPage}
import uk.gov.hmrc.internalauth.client.FrontendAuthComponents
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SbtVersionController @Inject()(
  override val mcc             : MessagesControllerComponents,
  dependenciesService          : DependenciesService,
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  sbtVersionPage               : SbtVersionPage,
  sbtAcrossEnvironmentsPage    : SbtAcrossEnvironmentsPage,
  override val auth            : FrontendAuthComponents
)(using
  override val ec: ExecutionContext
) extends FrontendController(mcc)
     with CatalogueAuthBuilders:

  def findLatestVersions(flag: String, teamName: Option[TeamName]) =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      for
        teams        <- teamsAndRepositoriesConnector.allTeams()
        selectedFlag =  Parser[SlugInfoFlag].parse(flag.toLowerCase).getOrElse(SlugInfoFlag.Latest)
        selectedTeam =  teamName.flatMap(n => teams.find(_.name == n))
        sbtVersions  <- dependenciesService.getSbtVersions(selectedFlag, selectedTeam.map(_.name))
      yield Ok(sbtVersionPage(sbtVersions.sortBy(s => (s.version, s.serviceName)), SlugInfoFlag.values.toSeq, teams, selectedFlag, selectedTeam))

  def compareAllEnvironments(teamName: Option[TeamName]) =
    BasicAuthAction.async: request =>
      given RequestHeader = request
      for
        teams        <- teamsAndRepositoriesConnector.allTeams()
        selectedTeam =  teamName.flatMap(n => teams.find(_.name == n))
        envs         <- SlugInfoFlag.values.toSeq.traverse(env => dependenciesService.getSbtCountsForEnv(env, selectedTeam.map(_.name)))
        sbts         =  envs.flatMap(_.usage.keys).distinct.sorted
      yield Ok(sbtAcrossEnvironmentsPage(envs, sbts, teams, selectedTeam))

end SbtVersionController
