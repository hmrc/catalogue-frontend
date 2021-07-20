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

package uk.gov.hmrc.cataloguefrontend

import java.util.Date

import akka.stream.scaladsl.Source
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import play.api.http.HttpEntity
import play.api.libs.json.{Json, OFormat}
import play.api.mvc._
import uk.gov.hmrc.cataloguefrontend.connector.model.{Dependencies, TeamName, Version, VersionState}
import uk.gov.hmrc.cataloguefrontend.connector.{DigitalService, ServiceDependenciesConnector, Team, TeamsAndRepositoriesConnector}
import uk.gov.hmrc.cataloguefrontend.util.CsvUtils
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

case class DependencyReport(
  repository: String,
  team: String,
  digitalService: String,
  dependencyName: String,
  dependencyType: String,
  currentVersion: String,
  latestVersion: String,
  colour: String,
  timestamp: Long = new Date().getTime
)

@Singleton
class DependencyReportController @Inject() (
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector,
  serviceDependencyConnector: ServiceDependenciesConnector,
  cc: MessagesControllerComponents
)(implicit val ec: ExecutionContext)
    extends FrontendController(cc) {

  implicit val drFormat: OFormat[DependencyReport] = Json.format[DependencyReport]

  private def getDependencies(
    digitalServices: Seq[DigitalService],
    allTeams: Seq[Team],
    dependencies: Dependencies
  ): Seq[DependencyReport] = {

    val repoName = dependencies.repositoryName

    val libraryDependencyReportLines = dependencies.libraryDependencies.map { d =>
      DependencyReport(
        repository = repoName,
        team = findTeamNames(repoName, allTeams).map(_.asString).mkString(";"),
        digitalService = findDigitalServiceName(repoName, digitalServices),
        dependencyName = d.name,
        dependencyType = "library",
        currentVersion = d.currentVersion.toString,
        latestVersion = d.latestVersion.getOrElse("Unknown").toString,
        colour = getColour(d.currentVersion, d.latestVersion)
      )
    }

    val sbtPluginDependencyReportLines = dependencies.sbtPluginsDependencies.map { d =>
      DependencyReport(
        repository = repoName,
        team = findTeamNames(repoName, allTeams).map(_.asString).mkString(";"),
        digitalService = findDigitalServiceName(repoName, digitalServices),
        dependencyName = d.name,
        dependencyType = "plugin",
        currentVersion = d.currentVersion.toString,
        latestVersion = d.latestVersion.getOrElse("Unknown").toString,
        colour = getColour(d.currentVersion, d.latestVersion)
      )
    }

    libraryDependencyReportLines ++ sbtPluginDependencyReportLines
  }

  private def findTeamNames(repositoryName: String, teams: Seq[Team]): Seq[TeamName] =
    teams
      .filter(_.repos.isDefined)
      .filter(team => team.repos.get.values.flatten.toSeq.contains(repositoryName))
      .map(_.name)

  private def findDigitalServiceName(repositoryName: String, errorsOrDigitalServices: Seq[DigitalService]): String =
    errorsOrDigitalServices
      .find(_.repositories.exists(_.name == repositoryName))
      .map(_.name)
      .getOrElse("Unknown")

  private def getColour(currentVersion: Version, optLatestVersion: Option[Version]): String =
    // TODO this never returns BobbyRule state...
    // see Dependency#versionState
    optLatestVersion.flatMap(latestVersion => Version.getVersionState(currentVersion, latestVersion)) match {
      case Some(_: VersionState.BobbyRuleViolated)  => "version-active-violation"
      case Some(_: VersionState.BobbyRulePending)   => "version-pending-violation"
      case Some(VersionState.NewVersionAvailable)   => "version-new-available"
      case _                                        => "version-ok"
    }

  def dependencyReport(): Action[AnyContent] =
    Action.async { implicit request =>
      for {
        allTeams         <- teamsAndRepositoriesConnector.allTeams
        digitalServices1 <- teamsAndRepositoriesConnector.allDigitalServices
        digitalServices <- Future
                             .sequence {
                               digitalServices1.map(teamsAndRepositoriesConnector.digitalServiceInfo)
                             }
                             .map(_.flatten)
        allDependencies <- serviceDependencyConnector.getAllDependencies
        deps = allDependencies.flatMap { dependencies =>
                 getDependencies(digitalServices, allTeams, dependencies)
               }
        csv    = CsvUtils.toCsv(CsvUtils.toRows(deps, ignoreFields = Seq("timestamp")))
        source = Source.single(ByteString(csv, "UTF-8"))
      } yield Result(
        header = ResponseHeader(200, Map("Content-Disposition" -> "inline; filename=\"deprep.csv\"")),
        body = HttpEntity.Streamed(source, None, Some("text/csv"))
      )
    }
}
